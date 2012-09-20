package de.cased.mobilecloud;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.util.Log;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CapabilityItem;
/**
 * The PeerWorker is the Client of the management protocol. Every device needs
 * one. A PeerWorker makes sure to have management channels established.
 * @author stas
 *
 */
public class PeerClientWorker extends Thread implements
		PeerHandlerBackReference {

	private static final String TAG = "PeerWorker";
	private RuntimeConfiguration config;

	private ArrayList<RouteEntry> neighbors;  //make sure to have synchronized access
	private ArrayList<ManagementClientHandler> managementConnections;

	public ArrayList<ManagementClientHandler> getManagementConnections() {
		return managementConnections;
	}

	private Timer timer;
	private List<CapabilityItem> requestedCapabilties;
	private SSLSocket socket = null;
	private boolean isConnected = false;

	private PeerClientCommunicator communicator;



	// private boolean latelyFoundConnection = false;
	//
	// long threshold = Long.parseLong(config.getProperties()
	// .getProperty(
	// "management_connection_delay_between_retries"));

	// private ArrayList<String> currentlyConnecting = new ArrayList<String>();

	private Map<String, ConnectionCandidate> connectionAttempts = new HashMap<String, ConnectionCandidate>();

	public boolean isConnected() {
		return isConnected;
	}

	public void setConnected(boolean isConnected) {
		this.isConnected = isConnected;
	}

	private boolean running = true;

	private int neededSimultaneousConections;

	public PeerClientWorker(List<CapabilityItem> requestedCapabilities,
			PeerClientCommunicator communicator) {

		Log.d(TAG, "reached PeerClientWorker constructor");
		config = RuntimeConfiguration.getInstance();
		// config.allowManagementConnectionsForClient();
		// config.allowVpnForClient();

		neighbors = new ArrayList<RouteEntry>();
		managementConnections = new ArrayList<ManagementClientHandler>();
		// stop all of these when destroying the worker(this)
		timer = new Timer();// stop this when destroying the worker(this)

		this.requestedCapabilties = requestedCapabilities;
		neededSimultaneousConections =
				Integer.parseInt(config.getProperties().getProperty(
						"management_connections"));
		this.communicator = communicator;
	}

	@Override
	public void run(){

		initNeighborListUpdater();
		while (running) {
			Log.d(TAG,
					"iterating over managementconnections to determine if we need more");
			if (managementConnections.size() > 0
					&& !isConnected) {
				Log.d(TAG,
						"we can start chosing data connections");
				communicator.setCurrentStatus(Status.Connecting);
				selectRouter();
				// boolean successful = selectRouter();
				// waitToBeNotified(successful);
			} else if (needMoreManagementConnections()) {
				Log.d(TAG,
						"need more management connections, have "
								+ managementConnections.size());
				// sendOverviewMessage(SEARCHING_FOR_MANAGEMENT_CONNECTIONS,
				// null);

				if (communicator.getCurrentStatus() != Status.Connecting ||
						communicator.getCurrentStatus() != Status.Connected) {

					communicator
							.setCurrentStatus(Status.SearchingManagementConnections);
				}
				while (running && needMoreManagementConnections()) {
					final RouteEntry whichNeighborToPick = getFirstUnconnectedBestNeighbor();

					if (whichNeighborToPick == null) {
						Log.d(TAG, "no neighbors available");
						waitToBeNotified(true); // unverified
					} else {
						subsequentiallyConnect(whichNeighborToPick);
					}
				}
			} else {
				Log.d(TAG,
						"dont need anything, we are connected and have enough management connections");
				// sendOverviewMessage(CONNECTED, null);
				waitToBeNotified(true); // unverified
			}

			// TODO: remove this eventually
			try {
				Thread.sleep(500);
			 } catch (InterruptedException e) {
			 Log.e(TAG, e.getMessage(), e);
			 }

		}
	}

	private boolean selectRouter() {
		Log.d(TAG, "selectRouter");
		int maxMetric = Integer.MAX_VALUE;
		ManagementClientHandler bestConnection = null;
		for (ManagementClientHandler management : managementConnections) {
			RouteEntry routeInfo = management.getRouteInfo();
			int currentMetric = routeInfo.getMetric();
			if(currentMetric < maxMetric){
				maxMetric = currentMetric;
				bestConnection = management;
			}
		}
		return setRouteInformation(bestConnection);
	}

	private boolean setRouteInformation(ManagementClientHandler bestConnection) {

		// this also needs to be told to the service provider in the management
		// channel!
		Log.d(TAG, "setRouteInformation");

		if(bestConnection == null){
			Log.d(TAG, "there was no bestConnection submitted ;<");
			return false;
		}else{
			bestConnection.requestVPN();
			return true;
		}
	}

	private void waitToBeNotified(boolean goToSleep) {
			Log.d(TAG, "going to sleep");
			if (goToSleep) {
				try {
				synchronized (this) {
					wait();
				}

				} catch (InterruptedException e) {
					e.printStackTrace();
					Log.d(TAG, "failed during sleep");
			} finally {
				Log.d(TAG, "woke up");
				}
			}
	}

	private synchronized boolean needMoreManagementConnections() {
		return managementConnections.size() < neededSimultaneousConections;
	}

	private void initNeighborListUpdater() {

		TimerTask updateNeighborList = new TimerTask() {
			@Override
			public void run() {

					// TODO read table, check if it differs and if thresholds
					// expired, if so wake up! Else let it sleep.
					ArrayList<RouteEntry> freshTable = Utilities
							.readRoutingTable(config);
					Collections.sort(freshTable);

					if (doesNewRoutingTableDifferFromOld(freshTable)) {
						Log.d(TAG, "got new routing entries, waking up");
						neighbors = freshTable;
					decideIfNeedsWakeup();
					} else if (checkExpirationTimers()) {
						Log.d(TAG, "timers expired, waking up");
						neighbors = freshTable;
					decideIfNeedsWakeup();

					}

					// neighbors = Utilities.readRoutingTable(config);
					// Collections.sort(neighbors);
					// Log.d(TAG, "updated neighbor list");
			}

			private void decideIfNeedsWakeup() {
				if (!isConnected || needMoreManagementConnections()) {
					wakeUp();
				}

			}

			private boolean checkExpirationTimers() {
				long threshold = Long.parseLong(config.getProperties()
						.getProperty(
								"management_connection_delay_between_retries"));

				boolean punishmentExpired = false;
				for (RouteEntry entry : neighbors) {
					String destination = entry.getDestination();
					if (connectionAttempts.containsKey(destination)) {
						ConnectionCandidate attempt = connectionAttempts
								.get(destination);
						if (resetConnectionAttempts(destination, attempt,
								threshold) && !isConnectedAlready(destination)) {
							punishmentExpired = true;
						}

					}
				}
				return punishmentExpired;
			}

			private boolean doesNewRoutingTableDifferFromOld(
					ArrayList<RouteEntry> freshTable) {
				for (RouteEntry entry : freshTable) {
					if (!isDestinationInOldTable(entry)) {
						return true;
					}
				}
				return false;

			}

			private boolean isDestinationInOldTable(RouteEntry entry) {
				for (RouteEntry old_entry : neighbors) {
					if (entry.getDestination().equals(
							old_entry.getDestination())) {
						return true;
					}
				}
				return false;
			}
		 };
		 timer.scheduleAtFixedRate(updateNeighborList, new Date(), 5000); //
		// every
		// 5
		// seconds

//		neighbors = Utilities.readRoutingTable(config);
//		Collections.sort(neighbors);
//		Log.d(TAG, "updated neighbor list");

	}

	private void wakeUp() {
		synchronized (this) {
			Log.d(TAG, "waking up");
			notifyAll();
		}
	}

	private void subsequentiallyConnect(RouteEntry whichNeighborToPick) {

		Log.d(TAG, "found a neighbor, establishing management connection");
		int managementPort = Integer.parseInt(config.getProperties()
				.getProperty("management_port"));

		SSLContext ssl_context = config.getSslContex();
		SSLSocketFactory socketFactory = ssl_context.getSocketFactory();
		try {

			Log.d(TAG, "creating ssl socket");

			// InetAddress inteAddress =
			// InetAddress.getByName("stas.violates.us");
			InetSocketAddress socketAddress = new InetSocketAddress(
					whichNeighborToPick.getDestination(),
					managementPort);

			Socket temp = new Socket();
			temp.connect(socketAddress, 10000);

			socket = (SSLSocket) socketFactory.createSocket(temp,
					whichNeighborToPick.getDestination(), managementPort, true);

			// Log.d(TAG, "creating ssl socket");
			// socket = (SSLSocket) socketFactory
			// .createSocket(whichNeighborToPick.getDestination(),
			// managementPort);


			// currentlyConnecting.add(whichNeighborToPick
			// .getDestination());

			// socket.addHandshakeCompletedListener(new
			// HandshakeCompletedListener() {
			//
			// @Override
			// public void handshakeCompleted(
			// HandshakeCompletedEvent event)
			// {
			// Log.d(TAG,
			// "ssl handshake completed>>>>>>>>>>>>>>>>>>>>>><");
			// // initiateProtocol(socket,
			// // whichNeighborToPick);
			// // currentlyConnecting
			// // .remove(whichNeighborToPick
			// // .getDestination());
			// Log.d(TAG, "conenction established");
			//
			// }
			// });
			socket.setUseClientMode(true);
			socket.startHandshake();
			initiateProtocol(socket,
					whichNeighborToPick);

			// currentlyConnecting.add(whichNeighborToPick.getDestination());

		} catch (UnknownHostException e) {
			// currentlyConnecting
			// .remove(whichNeighborToPick.getDestination());
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			// currentlyConnecting
			// .remove(whichNeighborToPick.getDestination());
		}


	}

	private void initiateProtocol(SSLSocket connection, RouteEntry entry) {
		Log.d(TAG, "initiating Protocol for management connection");
		ManagementClientHandler handler = new ManagementClientHandler(
				connection, requestedCapabilties, entry, this);
		managementConnections.add(handler);
		handler.start();
	}

	private synchronized RouteEntry getFirstUnconnectedBestNeighbor() {

		if (neighbors.size() == 0)
			return null;

		int counter = 0;
		Log.d(TAG, "getFirstUnconnectedNeighbor");
		Log.d(TAG, "neighborsize:" + neighbors.size());
		long threshold = Long.parseLong(config.getProperties()
				.getProperty(
						"management_connection_delay_between_retries"));

		ConnectionCandidate lowestAttemptCandidate = null;
		int attempts = Integer.MAX_VALUE;
		String candidateDestination = "";
		RouteEntry lowestAttemptRouteEntry = null;
		for (RouteEntry entry : neighbors) {
			String destination = entry.getDestination();
			if (connectionAttempts.containsKey(destination)) {
				if (connectionAttempts
						.get(destination).getConnectionAttempts() < attempts) {
					attempts = connectionAttempts
							.get(destination).getConnectionAttempts();
					lowestAttemptCandidate = connectionAttempts
							.get(destination);
					candidateDestination = destination;
					lowestAttemptRouteEntry = entry;
				}
			}
		}

		while (counter < neighbors.size()) {
			String destination = neighbors.get(counter).getDestination();
			Log.d(TAG, "analyzing neighbor:" + destination);

			if (!isConnectedAlready(destination)) {
				Log.d(TAG, "neighbor is not connected yet");
				// if connection attempt made previously
				if (!connectionAttempts.containsKey(destination)) {
					Log.d(TAG, "found new potential connection partner");
					ConnectionCandidate attempt = new ConnectionCandidate();
					attempt.setLastConnectionAttempt(System.currentTimeMillis());
					attempt.increaseConnectionAttempts();
					connectionAttempts.put(destination, attempt);

					// latelyFoundConnection = true;

					return neighbors.get(counter);

				}
			}
			counter++;
		}

		if (lowestAttemptCandidate != null) {
			Log.d(TAG,
					"connection attempt made previously, picking least attempted candidate");
			// ConnectionCandidate attempt = connectionAttempts
			// .get(candidateDestination);

			// if not connected for threshold, reset counter
			resetConnectionAttempts(candidateDestination,
					lowestAttemptCandidate,
					threshold);

			// if connection retries didnt reach threshold, retry
			if (lowestAttemptCandidate.getConnectionAttempts() <= Integer
					.parseInt(config.getProperties().getProperty(
							"management_connection_retries"))) {
				Log.d(TAG,
						"Retrying connection with "
								+ candidateDestination);
				lowestAttemptCandidate.setLastConnectionAttempt(System
						.currentTimeMillis());
				lowestAttemptCandidate.increaseConnectionAttempts();

				// latelyFoundConnection = true;

				return lowestAttemptRouteEntry;

			}
		}
		Log.d(TAG, "cant find unconnected neighbor");
		return null;

	}

	private boolean resetConnectionAttempts(String destination,
			ConnectionCandidate attempt, long threshold) {
		if (attempt.getLastConnectionAttempt() + threshold < System
					.currentTimeMillis()) {
			Log.d(TAG,
					"timeout for connection penalty reached for "
							+ destination);
			attempt.setConnectionAttempts(0);
			return true;
		}
		return false;
	}

	private boolean isConnectedAlready(String target) {
		for (ManagementClientHandler handler : managementConnections) {
			if(target.equals(handler.getTargetIP())){
				return true;
			}
		}
		return false;
	}

	public void halt(){
		synchronized (this) {
			running = false;
			this.notifyAll();
			timer.cancel();
			timer.purge();
			for (ManagementClientHandler handler : managementConnections) {
				handler.halt(true);
			}
		}
	}

//	private synchronized void listNeighbors(){
//		Log.d(TAG, "Listing Neighbors");
//		for (RouteEntry entry : neighbors) {
//			Log.d(TAG, "entry:" + entry.getDestination() +
//					" over " + entry.getNextHop() +
//					" with a cost of " +
//					entry.getMetric());
//		}
//	}
	/**
	 * gets called from Management client handler. Its assumed that the handler
	 * closed all resources and just wants to be removed from the internal list
	 * of the worker.
	 *
	 * @param managementClientHandler
	 */
	@Override
	public void deleteHandler(Object managementClientHandler) {
		Log.d(TAG, "deleting Handler");
		managementConnections.remove(managementClientHandler);
		synchronized (this) {
			Log.d(TAG, "waking up");
			notifyAll();
		}
	}

	public void reportNotUsefulNeighbor(String neighbor) {
		if (connectionAttempts.containsKey(neighbor)) {
			ConnectionCandidate attempt = connectionAttempts.get(neighbor);
			attempt.setConnectionAttempts(Integer.parseInt(config
					.getProperties().getProperty(
							"management_connection_retries")) + 1);
		}
	}

	// private void sendOverviewMessage(int choosingDataConnection, Object obj)
	// {
	// Message msg = Message.obtain();
	//
	// msg.arg1 = choosingDataConnection;
	// msg.obj = obj;
	//
	// try {
	// messenger.send(msg);
	// } catch (android.os.RemoteException e1) {
	// Log.w(getClass().getName(), "Exception sending message", e1);
	// }
	// }

	public void setConnected(ManagementClientHandler managementClientHandler) {
		// sendOverviewMessage(CONNECTED,
		// managementClientHandler.getRemoteMeshIP());

		ConnectionCandidate attempt = connectionAttempts
				.get(managementClientHandler.getRemoteMeshIP());
		if (attempt != null) {
			attempt.setConnectionAttempts(0);
			attempt.setLastConnectionAttempt(System.currentTimeMillis());
		}

		setConnected(true);
		communicator.setCurrentStatus(Status.Connected);
		communicator.setGateway(managementClientHandler.getRemoteMeshIP());
	}

	public void reportManagementStatusChange() {
		communicator.reportManagementStatusChange();
	}
}
