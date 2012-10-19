package de.cased.mobilecloud;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CapabilityItem;

public class PeerClientCommunicator extends Service {

	private static final String TAG = "PeerComunicator";
	private RuntimeConfiguration config = null;
	// private PeerClientWorker worker = new PeerClientWorker();
	private PeerClientWorker worker;

	private final IBinder mBinder = new LocalBinder();
	private List<ConnectionStateListener> connectionStateListener = new ArrayList<ConnectionStateListener>();

	// public static final String EXTRA_MESSENGER =
	// "de.cased.mobilecloud.EXTRA_MESSENGER";

	private Status currentStatus = Status.Offline;
	private String gateway;

	public ArrayList<ManagementClientHandler> getManagementConnections() {
		if (worker != null) {
			return worker.getManagementConnections();
		} else {
			return null;
		}
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "PeerClientCommunicator created");
		config = RuntimeConfiguration.getInstance();
		if (config.getToken() == null || config.getPrivKey() == null) {
			return;
		}

		// try {
		// config.setSslContex(config.createSSLContext(false));
		// } catch (UnrecoverableKeyException e) {
		// Log.e(TAG, e.getMessage(), e);
		// } catch (KeyManagementException e) {
		// Log.e(TAG, e.getMessage(), e);
		// } catch (KeyStoreException e) {
		// Log.e(TAG, e.getMessage(), e);
		// } catch (NoSuchAlgorithmException e) {
		// Log.e(TAG, e.getMessage(), e);
		// } catch (CertificateException e) {
		// Log.e(TAG, e.getMessage(), e);
		// } catch (IOException e) {
		// Log.e(TAG, e.getMessage(), e);
		// }
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "reached onDestroy");
		stopService();
	}

	public void stopService() {
		Log.d(TAG, "PeerClientCommunicator shutting down");
		if (worker != null) {
			worker.halt();
			worker = null;
		}
		Utilities.haltClientVPNDaemons(config);
		setCurrentStatus(Status.Offline);
	}

	@Override
	public void onStart(Intent intent, int startid) {

		// Bundle extras=intent.getExtras();
		// Messenger messenger = null;
		//
		// if (extras!=null) {
		// messenger = (Messenger) extras.get(EXTRA_MESSENGER);
		// }
		config.setInterfaceAddressNetmask(false);
		CapabilityItem item = CapabilityItem.INTERNET;
		ArrayList<CapabilityItem> capabilities = new ArrayList<CapabilityItem>();
		capabilities.add(item);
		worker = new PeerClientWorker(capabilities, this);
		// worker = new PeerClientWorker(capabilities, messenger, this);
		Log.d(TAG, "PeerClientCommunicator starting with Internet request");
		worker.start();
	}



	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	public class LocalBinder extends Binder {
		public PeerClientCommunicator getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return PeerClientCommunicator.this;
		}
	}

	public PeerClientWorker getWorker() {
		return worker;
	}

	public Status getCurrentStatus() {
		return currentStatus;
	}

	public void setCurrentStatus(Status currentStatus) {
		this.currentStatus = currentStatus;
		for (ConnectionStateListener listener : connectionStateListener) {
			if (listener != null) {
				listener.notifyAboutConnectionChange();
			}
		}
		// notify the overview
	}

	public void setGateway(String remoteMeshIP) {
		gateway = remoteMeshIP;

	}

	public String getGateway() {
		return gateway;
	}

	public void addConnectionStateListener(
			ConnectionStateListener mobileCloudOverviewParticipating) {
		connectionStateListener.add(mobileCloudOverviewParticipating);
	}

	public void removeConnectionStateListener(
			ConnectionStateListener mobileCloudOverviewParticipating) {
		connectionStateListener.remove(mobileCloudOverviewParticipating);
	}

	public void reportManagementStatusChange() {
		for (ConnectionStateListener listener : connectionStateListener) {
			if (listener != null) {
				listener.notifyAboutManagementChange();
			}
		}
	}

}
