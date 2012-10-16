package de.cased.mobilecloud;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import android.util.Log;

import com.google.protobuf.Message;

import de.cased.mobilecloud.common.PeerCommunicationProtocol.BatteryState;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Capability;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CapabilityItem;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CloseManagementChannel;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CloseVPN;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ConnectionACK;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ConnectionNACK;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Hello;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.HelloResponse;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.KeepAlive;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Quota;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ResourceRequestMessage;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNGranted;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRefused;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRequest;
import de.cased.mobilecloud.common.PeerCommunicationStateContext;
import ext.org.bouncycastle.operator.ContentVerifierProvider;
import ext.org.bouncycastle.operator.OperatorCreationException;
import ext.org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import ext.org.bouncycastle.pkcs.PKCSException;

/**
 * The handler class of the p2p management server.
 * It handles clients.
 *
 * @author stas
 *
 */
public class ManagementServerHandler extends Thread {

	private static String TAG = "ManagementHandler";

	private SSLSocket socket;

	// private DataOutputStream writer;
	// private DataInputStream reader;

	private ObjectOutputStream writer;
	private ObjectInputStream reader;

	private PeerCommunicationStateContext protocolState;
	private boolean running;
	private PeerServerWorker backreference;
	private Object latestMessage;
	private RuntimeConfiguration config;

	private Timer timer;

	private X509Certificate peerCertificate;

	public void setPeerCertificate(X509Certificate peerCert) {
		this.peerCertificate = peerCert;
	}

	public ManagementServerHandler(SSLSocket client, PeerServerWorker backref){
		this.socket = client;
		running = true;
		backreference = backref;
		config = RuntimeConfiguration.getInstance();
		buildProtocolReactions();
	}

	private void buildProtocolReactions() {
		protocolState = new PeerCommunicationStateContext();
		analyzeHello();
		analyzeConnectionNACK();
		analyzeConnectionACK();
		analyzeVPNRequest();
		analyzeCloseVPN();
		analyzeCloseManagementChannel();
		analyzeResourceRequest();

	}

	private class ResourceRequestAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof ResourceRequestMessage) {
				Log.d(TAG, "Received ResourceRequestMessage");

				ResourceRequestMessage message = (ResourceRequestMessage) latestMessage;
				byte[] tempTarget = new byte[message.getPayload().size()];
				Log.d(TAG, "received encapsulated payload with size:"
						+ message.getPayload().size());
				message.getPayload().copyTo(tempTarget, 0);
				try {
					ResourceRequest request = new ResourceRequest(tempTarget);
					// TODO: verify signature
					// need the public key of the signer

					SSLSession session = socket.getSession();
					Log.d(TAG,
							"used cipher suite is:" + session.getCipherSuite());

					// http://stackoverflow.com/questions/9574870/no-peer-certificate-error-in-android-2-3-but-not-in-4
					// seems android 2.3 does not "see" the certs after
					// handshake
					// javax.security.cert.X509Certificate[] certs = session
					// .getPeerCertificateChain();
					// Log.d(TAG, "ssl session provides " + certs.length
					// + " certificates in its chain");

					// if (certs.length > 0) {
					ContentVerifierProvider contentVerifierProvider = new JcaContentVerifierProviderBuilder()
							.setProvider("BC").build(
									peerCertificate.getPublicKey());
					if (!request.isSignatureValid(contentVerifierProvider)) {
						Log.d(TAG, "invalid signature");
					} else {
						Log.d(TAG,
								"Resource Request signed correctly, retrieving information");
						int port = request.getPort();
						String transportLayer = Utilities
								.ipHeaderIdToString(request.getTransportLayer());
						String ipAddress = Utilities.intIpToString(request
								.getIP());

						Log.d(TAG, "client requests access to " + ipAddress
								+ ":" + port + " on " + transportLayer);
						// TODO: open iptables, start TimerTask to close them
						// again and store the ResourceRequest on SD card
					}

					// } else {
					// Log.d(TAG,
					// "this cannot happen, it means the other side did not present any certificates during handshake");
					// }






				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
					Log.d(TAG,
							"failed retrieving ResourceRequest from byte array");
				} catch (OperatorCreationException e) {
					Log.e(TAG, e.getMessage(), e);
				} catch (PKCSException e) {
					Log.e(TAG, e.getMessage(), e);
				}
				latestMessage = null;
			}
		}
	}

	private void analyzeResourceRequest() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new ResourceRequestAnalyzer());
	}

	private class CloseManagementChannelAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof CloseManagementChannel) {
				Log.d(TAG, "Closing management channel gracefully");
				halt();
				latestMessage = null;
			}
		}
	}

	private void analyzeCloseManagementChannel() {
		protocolState
				.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
						new CloseManagementChannelAnalyzer());
	}

	private class CloseVPNChannelAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof CloseVPN) {
				closeVPN();
				latestMessage = null;
			}
		}
	}

	private void analyzeCloseVPN() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new CloseVPNChannelAnalyzer());
	}


	private class HelloAnalyzer implements Runnable {
		@Override
		public void run() {
			if(latestMessage instanceof Hello){
				Hello msg = (Hello) latestMessage;
				reactOnHello(msg);
				latestMessage = null;
			}
		}
	}

	private void analyzeHello() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.HELLO_SENT_STATE, new HelloAnalyzer());
	}

	private void analyzeConnectionNACK(){
		protocolState.setStateAction(PeerCommunicationStateContext.HELLO_RESPONSE_SENT_STATE, new Runnable(){
			@Override
			public void run() {
				if(latestMessage instanceof ConnectionNACK){
					Log.d(TAG, "received connection NACK, dying gracefully");
					halt();
							latestMessage = null;
				}
			}
		});
	}

	private class ConnectionACKAnalyzer implements Runnable {
		@Override
		public void run() {
			if(latestMessage instanceof ConnectionACK){
				Log.d(TAG, "received connection ACK, connection elevated to management channel");
								startKeepAliveTimer();
								latestMessage = null;
			}
		}
	}

	private void analyzeConnectionACK() {
		protocolState.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
				new ConnectionACKAnalyzer());
	}

	protected void startKeepAliveTimer() {
		Log.d(TAG, "starting KeepAlive timer");
		TimerTask updateNeighborList = new TimerTask() {
			@Override
			public void run() {
				synchronized (this) {
					KeepAlive.Builder keepAliveBuilder = KeepAlive.newBuilder();
					Quota.Builder quotaBuilder = Quota.newBuilder();
					quotaBuilder.setBatteryState(BatteryState.FULL); // TODO
					quotaBuilder.setDataQuotaInBytes(10000); // TODO
					keepAliveBuilder.setQuota(quotaBuilder.build());
					sendMessage(keepAliveBuilder.build());
				}
			}
		};
		timer = new Timer();
		timer.scheduleAtFixedRate(
				updateNeighborList,
				new Date(),
				Integer.parseInt(config.getProperties().getProperty(
						"keep_alive_delay"))); //

	}

	private class VPNRequestAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof VPNRequest) {
				Log.d(TAG, "received VPN request");
				VPNRequest req = (VPNRequest) latestMessage;

				if (canProvideVPN()) {
					Log.d(TAG, "can provide VPN");
					if (openVPN()) {
						Log.d(TAG, "successfully opened firewall for client");
						sendMessage(VPNGranted.newBuilder().build());
					} else {
						Log.d(TAG, "failed to open firewall for client");
						sendMessage(VPNRefused.newBuilder().build());
					}
				} else {
					Log.d(TAG, "cant provide VPN");
					sendMessage(VPNRefused.newBuilder().build());
				}
			}
		}

	}

	private boolean validate(X509Certificate tempCert)
			throws CertificateException {
		X509Certificate signCert = Utilities.loadCertificate(new File(config
				.getCertificateDir(), config.getProperties().getProperty(
				"ca_cert_dest_loc")));

		if(!signCert.equals(signCert)){
	         try
	         {   //Not your CA's. Check if it has been signed by your CA
				signCert.verify(signCert.getPublicKey());
	         }
	         catch(Exception e){
	              throw new CertificateException("Certificate not trusted",e);
	         }
	    }
	    //If we end here certificate is trusted. Check if it has expired.
	     try{
	    	 signCert.checkValidity();
	      }
	      catch(Exception e){
	            throw new CertificateException("Certificate not trusted. It has expired",e);
	      }
		return true;
	}

	private void analyzeVPNRequest() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.VPN_REQUEST_ISSUED_STATE,
				new VPNRequestAnalyzer());
	}

	/**
	 * Normally the OpenVPN server is running but blocked by iptables rules.
	 * This function should open the connection for the requester.
	 */
	private boolean openVPN() {

		final String realAddress = getRealAddress();


		Log.d(TAG, "opening VPN for real:" + realAddress);

		config.allowOVpnAccessForIP(realAddress);


		new Thread(
			new Runnable() {
				@Override
				public void run() {
						String vpnAddress;
						while ((vpnAddress = getVpnAddress(realAddress)) == null) {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								Log.e(TAG, e.getMessage(), e);
							}
						}
						config.allowMasqueradeForIP(vpnAddress);
				}
				}).start();

		// String vpnAddress = getVpnAddress(realAddress);


		return true;


	}


	private String getVpnAddress(String realAddress) {

		String statusPath = config.getOvpnStatusPath();
		try {
			String command = "grep 'ROUTING TABLE' " + statusPath
					+ " -A 9999 | grep " + realAddress;

			Log.d(TAG, "getVpnAddress(" + realAddress + ") with command:");

			String statusOutput = config.getApp().coretask.runCommandForOutput(
					false, command);

			Log.d(TAG, "output:" + statusOutput);

			if (statusOutput.length() > 0) {
				String vpnAddress = statusOutput.substring(0,
						statusOutput.indexOf(','));
				Log.d(TAG, "found VPNAddress:" + vpnAddress);
				return vpnAddress;
			} else {
				return null;
			}



		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return null;
	}

	private String getRealAddress() {
		return socket.getRemoteSocketAddress().toString().split("/")[0];
	}

	/**
	 * Block the OpenVPN server again for this client, i.e. remove the client's grant.
	 */
	private void closeVPN() {
		String realAddress = getRealAddress();
		String vpnAddress = getVpnAddress(realAddress);

		Log.d(TAG, "closing VPN for real:" + realAddress + " and vpn:"
				+ vpnAddress);

		config.blockOVpnAccessForIP(realAddress);
		config.removeMasqueradeForIP(vpnAddress);
	}

	private boolean canProvideVPN() {
		//TODO implement logic
		return true;
	}

	private void reactOnHello(Hello msg) {
		Log.d(TAG, "reacting on hello message");
		// Capability capa = msg.getCapabilities();
		Capability intersection = getCapabilityIntersection(msg
				.getCapabilities());
		// List<CapabilityItem> cap = msg.getCapabilityList();
		Quota quota = getQuota();

		HelloResponse.Builder builder = HelloResponse.newBuilder();
		builder.setCapabilities(intersection);
		builder.setQuota(quota);
		sendMessage(builder.build());
	}



	private Quota getQuota() {
		// TODO implement
		Quota.Builder builder = Quota.newBuilder();
		BatteryState battery = BatteryState.FULL;
		builder.setBatteryState(battery);
		builder.setDataQuotaInBytes(1000000);
		return builder.build();
	}

	private Capability getCapabilityIntersection(
			Capability capabilities) {
		Log.d(TAG, "reached capability intersection");

		Capability.Builder builder = Capability.newBuilder();

		for (CapabilityItem capItem : capabilities.getCapabilityList()) {

			if ((capItem == CapabilityItem.INTERNET)
					&& Utilities.is3GAvailable(config)) {
				Log.d(TAG,
						"issuer requested Internet and I can provide Internet");
				if (isAnonymousAllowed() || (isFriendAllowed() && isFriend())) {
					builder.addCapability(CapabilityItem.INTERNET);
				}
			}
		}

		return builder.build();
	}


	private boolean isFriend() {
		Log.d(TAG, "checking if requester is friend of r1 or r2");

		String r1Destination = config.getProperty("friendlist_r1");
		String r2Destination = config.getProperty("friendlist_r2");
		String subject = peerCertificate.getSubjectDN().getName().substring(3);
		Log.d(TAG, "requester's subject is " + subject);

		return isFriend(r1Destination, subject)
				|| isFriend(r2Destination, subject);

	}

	private boolean isFriend(String friendFile, String subject) {
		List<String> r1Friends = Utilities.readFromFile(friendFile, config);
		Log.d(TAG, "loaded list" + friendFile);
		for (String friend : r1Friends) {
			Log.d(TAG, "checking on:" + friend);
			if (subject.equals(friend)) {
				Log.d(TAG, "is friend according to " + friendFile);
				return true;
			}
		}
		return false;
	}

	private boolean isFriendAllowed() {

		Log.d(TAG, "checking if friend tethering is granted");

		if (config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING_FRIENDS, false)) {
			Log.d(TAG, "indeed it is");
		} else {
			Log.d(TAG, "its not");
		}
		return config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING_FRIENDS, false);
	}

	private boolean isAnonymousAllowed() {
		Log.d(TAG, "checking if anonymous tethering is granted");

		if (config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING, false)) {
			Log.d(TAG, "indeed it is");
		} else {
			Log.d(TAG, "its not");
		}

		return config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING, false);
	}

	@Override
	public void run(){
		Log.d(TAG,
				"starting Management Server handler, waiting for streams to be retrieved");


		try {

			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			// writer = new DataOutputStream(os);
			// reader = new DataInputStream(is);

			writer = new ObjectOutputStream(os);
			reader = new ObjectInputStream(is);

			Log.d(TAG, "SSL Handshake finished, got Object Streams");
			performProtocol();

		} catch (Exception e) {
			Log.d(TAG, "failed to retrieve streams");
			e.printStackTrace();
			halt();
		}

		// socket.addHandshakeCompletedListener(new HandshakeCompletedListener()
		// {
		// @Override
		// public void handshakeCompleted(HandshakeCompletedEvent event) {
		// try {
		// ois = new ObjectInputStream(socket.getInputStream());
		// oos = new ObjectOutputStream(socket.getOutputStream());
		// Log.d(TAG, "SSL Handshake finished");
		// performProtocol();
		//
		// } catch (Exception e) {
		// e.printStackTrace();
		// halt();
		// }
		// }
		// });
	}

	private void performProtocol() {
		Log.d(TAG, "Connection established on server side");

		while(running){
			receiveMessage();
		}
		halt();
	}

	private void receiveMessage() {
		try {
			latestMessage = reader.readObject();
			Log.d(TAG, "received: " + latestMessage.getClass().getCanonicalName());
			protocolState.receiveEvent(latestMessage.getClass().getCanonicalName());
		} catch (Exception e) {
			Log.d(TAG, "something went wrong, I quit");
			e.printStackTrace();
			halt();
		}

	}

	private void sendMessage(Message msg) {
		try {
			Log.d(TAG, "going to send " + msg.getClass().getCanonicalName());
			writer.writeObject(msg);
			writer.flush();


			protocolState.receiveEvent(msg.getClass().getCanonicalName());
		} catch (IOException e) {
			e.printStackTrace();
			halt();
		}

	}

	public synchronized void halt() {
		// stop this thread, either something went wrong or we are dying
		// gracefully
		try{
			writer.close();
			reader.close();
			socket.close();
			running = false;
			timer.cancel();
			timer.purge();
			timer = null;
		}catch (Exception e){
			//dont care
		}finally{
			closeVPN();
			backreference.deleteHandler(this);
		}
	}

	public void stopMe() {
		synchronized (this) {
			running = false;
		}
	}


}
