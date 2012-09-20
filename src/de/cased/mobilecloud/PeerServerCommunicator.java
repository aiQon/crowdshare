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
		config.setSslContex(config.createSSLContext(false));
		server = new PeerServerWorker();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "PeerServerCommunicator shutting down");
		server.shutdownMobileCloudServer();
	}

	@Override
	public void onStart(Intent intent, int startid) {
		Log.d(TAG, "PeerServerCommunicator starting");
		if (server.isAlive()) {
			server.shutdownMobileCloudServer();
			server = new PeerServerWorker();
		}
		server.start();
	}


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
