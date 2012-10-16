package de.cased.mobilecloud;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.app.ProgressDialog;
import android.os.Handler;
import android.util.Log;
import de.cased.mobilecloud.common.AbstractStateContext;
import de.cased.mobilecloud.common.FriendUpdateStateContext;
import de.cased.mobilecloud.common.FriendlistUpdateProtocol.FriendlistUpdateRequest;
import de.cased.mobilecloud.common.RegistrationProtocol.Friendlist;

public class UpdateWorker extends AbstractServerWorker {

	private static String TAG = "UpdateWorker";

	public UpdateWorker(ProgressDialog dialog, Handler handler,
			AbstractStateContext stateContext) {
		super(dialog, handler, stateContext);
	}

	@Override
	public void run() {
		try {
			socket = connectToServer();
			sendRequest();
			while (isRunning()) {
				receiveMessage();
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void sendRequest() {
		FriendlistUpdateRequest.Builder builder = FriendlistUpdateRequest
				.newBuilder();

		builder.setFbaccesstoken(config.getFbAccessToken());
		builder.setFbexpiredate(config.getFbAccessExpire());

		FriendlistUpdateRequest request = builder
				.build();
		sendMessage(request);

	}

	@Override
	protected void buildProtocolReactions() {
		analyzeFriendlist();

	}

	private void analyzeFriendlist() {
		protocolState.setStateAction(FriendUpdateStateContext.FINISHED_STATE,
				new Runnable() {

					@Override
					public void run() {
						if (getLatestMessage() instanceof Friendlist) {
							Log.d(TAG, "received friendlist, saving it");
							Friendlist list = (Friendlist) getLatestMessage();
							List<String> r1List = list.getR1SubjectList();
							List<String> r2List = list.getR2SubjectList();

							String r1Destination = config
									.getProperty("friendlist_r1");
							String r2Destination = config
									.getProperty("friendlist_r2");

							Utilities
									.writeToFile(r1List, r1Destination,
									config);
							Utilities
									.writeToFile(r2List, r2Destination,
									config);
							dialog.dismiss();
							halt();
						}
					}

				});
	}

	@Override
	protected void cancel() {

	}

	private SSLSocket connectToServer() throws Exception {
		Log.d(TAG,
				"connecting to update server: "
						+ config.getProperty("update_server") + ":"
						+ config.getProperty("update_port"));
		SSLContext ssl_context = config.createSSLContext(false);
		SSLSocketFactory socketFactory = ssl_context.getSocketFactory();
		SSLSocket socket = (SSLSocket) socketFactory.createSocket(
				config.getProperty("update_server"),
				Integer.parseInt(config.getProperty("update_port")));
		// socket.setNeedClientAuth(true); // not sure if this matches in here
		// socket.setWantClientAuth(true);
		socket.setUseClientMode(true);
		socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {

			@Override
			public void handshakeCompleted(HandshakeCompletedEvent event) {
				Log.d(TAG,
						"handshake complete>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
			}
		});
		socket.startHandshake();
		oos = new ObjectOutputStream(socket.getOutputStream());
		ois = new ObjectInputStream(socket.getInputStream());
		Log.d(TAG, "Connection established");
		return socket;
	}
}
