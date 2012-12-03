package de.cased.mobilecloud;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
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
	private Context context;

	// private PeerServerWorker me = this;

	public PeerServerWorker(Context context) {
		handler = new ArrayList<ManagementServerHandler>();
		config = RuntimeConfiguration.getInstance();
		running = true;
		this.context = context;
	}

	@Override
	public void run(){
		try {
			acquireWakeLock();
			SSLContext ssl_context = config.getSslContex(false);
			SSLServerSocketFactory factory =
					ssl_context.getServerSocketFactory();
			// SSLServerSocketFactory factory = (SSLServerSocketFactory)
			// SSLServerSocketFactory
			// .getDefault();

			port = Integer.parseInt(config.getProperties().getProperty("management_port"));
			//
			server = (SSLServerSocket) factory.createServerSocket();
			server.setReuseAddress(true);
			InetSocketAddress inetsock = new InetSocketAddress(port);
			server.bind(inetsock); // server
																			// bound
			Log.d(TAG, "peer server bound to management port " + port);
			server.setNeedClientAuth(true);

			while(running){
				final SSLSocket client = (SSLSocket) server.accept();
				client.setNeedClientAuth(true);
				Log.d(TAG, "Accepted connection");
				final ManagementServerHandler handle = new ManagementServerHandler(
						client, PeerServerWorker.this, context);
				handler.add(handle);
				client.addHandshakeCompletedListener(new HandshakeCompletedListener() {

					@Override
					public void handshakeCompleted(HandshakeCompletedEvent event) {
						// TODO Auto-generated method stub

						X509Certificate peerCert = null;
						try{

							// X509Certificate[] certs = event
							// .getPeerCertificateChain();

							Certificate[] certsOld = event
									.getPeerCertificates();

							for (Certificate cert : certsOld) {
								java.security.cert.X509Certificate xCert = (java.security.cert.X509Certificate) cert;
								if (xCert.getSubjectDN().getName()
										.startsWith("CN=")) {
									peerCert = xCert;
								}
								Log.d(TAG, "got cert for "
										+ xCert.getSubjectDN().getName());
							}

							// for (X509Certificate cert : certs) {
							// if(cert.getSubjectDN().getName().startsWith("CN=")){
							// peerCert = cert;
							// }
							//
							// Log.d(TAG, "got cert for "
							// + cert.getSubjectDN().getName());
							// }

						}catch(Exception ex){
							Log.d(TAG, "couldnt get cert chain");
							ex.printStackTrace();
						}
						Log.d(TAG, "handshake done>>>>>>>>>>>>>>>>>>>>>>>>");
						handle.setPeerCertificate(peerCert);

					}
				});


				handle.start();

			}
			if (server != null)
				server.close();

		} catch (NumberFormatException e) {
			Log.d(TAG, "the config file has a semantic error at 'management_port'");
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(TAG, "server got killed");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			releaseWakeLock();
		}
	}

	private void releaseWakeLock() {
		if (wl != null && wl.isHeld()) {
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

	public synchronized void shutdownMobileCloudServer() {

		for (ManagementServerHandler handle : handler) {
			handle.stopMe();
			handle.halt();
			deleteHandler(handle); // dirty, isnt it?
		}
		running = false;
		if (server != null) {
			try {

				boolean isBound = server.isBound();
				Log.d(TAG, "server is still bound before close:" + isBound);
				server.close();
				isBound = server.isBound();
				Log.d(TAG, "server is still bound after close:" + isBound);

			} catch (IOException e) {

			} finally {
				server = null;
				// releaseWakeLock();
			}
		}
	}
}
