package de.cased.mobilecloud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

public class PeerServerWorker extends Thread implements
		PeerHandlerBackReference {

	private static String TAG = "PerServerWorker";
	private RuntimeConfiguration config;
	private boolean running;
	private int port;
	private List<ManagementServerHandler> handler;
	private SSLServerSocket server;
	private PowerManager.WakeLock wl;

	// private PeerServerWorker me = this;

	public PeerServerWorker() {
		handler = new ArrayList<ManagementServerHandler>();
		config = RuntimeConfiguration.getInstance();
		running = true;
	}

	@Override
	public void run(){
		try {
			acquireWakeLock();
			SSLContext ssl_context = config.getSslContex();
			SSLServerSocketFactory factory =
					ssl_context.getServerSocketFactory();
			// SSLServerSocketFactory factory = (SSLServerSocketFactory)
			// SSLServerSocketFactory
			// .getDefault();

			port = Integer.parseInt(config.getProperties().getProperty("management_port"));
			server = (SSLServerSocket) factory.createServerSocket(port); // server
																			// bound
			Log.d(TAG, "peer server bound to management port " + port);

			while(running){
				final SSLSocket client = (SSLSocket) server.accept();
				Log.d(TAG, "Accepted connection");
				client.addHandshakeCompletedListener(new HandshakeCompletedListener() {

					@Override
					public void handshakeCompleted(HandshakeCompletedEvent event) {
						// TODO Auto-generated method stub

						Log.d(TAG, "handshake done>>>>>>>>>>>>>>>>>>>>>>>>");

					}
				});
				ManagementServerHandler handle = new ManagementServerHandler(
						client, this);
				handler.add(handle);
				handle.start();

			}

			server.close();

		} catch (NumberFormatException e) {
			Log.d(TAG, "the config file has a semantic error at 'management_port'");
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(TAG, "Couldnt create server socket on:" + port);
			e.printStackTrace();
		} finally {
			releaseWakeLock();
		}
	}

	private void releaseWakeLock() {
		if (wl != null) {
			wl.release();
		}

	}

	private void acquireWakeLock() {
		PowerManager pm = (PowerManager) config.getHostActivity()
				.getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(
				PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag"); // maybe
																// PARTIAL_WAKE_LOCK

		wl.acquire();

	}

	@Override
	public synchronized void deleteHandler(Object handle) {
		handler.remove(handle);
	}

	public void shutdownMobileCloudServer() {

		for (ManagementServerHandler handle : handler) {
			handle.stopMe();
			handle.halt();
			deleteHandler(handle); // dirty, isnt it?
		}
		running = false;
		try {
			server.close();

		} catch (IOException e) {

		} finally {
			server = null;
		}
	}
}
