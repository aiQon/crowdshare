package de.cased.mobilecloud;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class PeerServerCommunicator extends Service {

	private static final String TAG = "PeerComunicator";
	private RuntimeConfiguration config = null;
//	private PeerClientWorker worker = new PeerClientWorker();
	private PeerServerWorker server;



	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate");
		config = RuntimeConfiguration.getInstance();
		// try {
		// config.setSslContex(config.createSSLContext(false));
		server = new PeerServerWorker(this);
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
		Log.d(TAG, "PeerServerCommunicator shutting down");
		server.shutdownMobileCloudServer();
	}

	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "PeerServerCommunicator starting");
		if (server != null && server.isAlive()) {
			server.shutdownMobileCloudServer();
			server = new PeerServerWorker(this);
		}
		server.start();
	}


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
