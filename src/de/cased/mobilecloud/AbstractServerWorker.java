package de.cased.mobilecloud;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Security;

import javax.net.ssl.SSLSocket;

import android.app.ProgressDialog;
import android.os.Handler;

import com.google.protobuf.Message;

import de.cased.mobilecloud.common.AbstractStateContext;
import ext.org.bouncycastle.jce.provider.BouncyCastleProvider;

abstract public class AbstractServerWorker extends Thread {

	protected RuntimeConfiguration config;
	protected ProgressDialog dialog;
	protected AbstractStateContext protocolState;

	private Object latestMessage;

	protected ObjectOutputStream oos;
	protected ObjectInputStream ois;
	protected SSLSocket socket;
	protected Handler handler;
	protected boolean running = true;

	private static String TAG = "AbstractServerWorker";

	public AbstractServerWorker(ProgressDialog dialog, Handler handler,
			AbstractStateContext stateContext) {
		config = RuntimeConfiguration.getInstance();
		// this.callback = callback;
		this.dialog = dialog;
		this.handler = handler;
		this.protocolState = stateContext;
		Security.addProvider(new BouncyCastleProvider());
		buildProtocolReactions();
	}

	protected abstract void buildProtocolReactions();

	protected abstract void cancel();

	@Override
	public abstract void run();

	protected Message getLatestMessage(){
		return (Message) latestMessage;
	}

	protected boolean isRunning() {
		return running;
	}

	protected void halt() {
		if (oos != null && ois != null && socket != null) {
			try {
				oos.close();
				ois.close();
				socket.close();
			} catch (Exception e) {
			} finally {
				running = false;
				socket = null;
				oos = null;
				ois = null;
			}
		}
	}

	protected void receiveMessage() {
		try {
			latestMessage = ois.readObject();
			// Log.d(TAG,
			// "received message "
			// + latestMessage.getClass().getCanonicalName());
			protocolState.receiveEvent(latestMessage.getClass()
					.getCanonicalName());
		} catch (Exception e) {
			e.printStackTrace();
			halt();
		}

	}

	protected void sendMessage(Object msg) {
		try {
			oos.writeObject(msg);
			protocolState.receiveEvent(msg.getClass().getCanonicalName());
		} catch (IOException e) {
			e.printStackTrace();
			halt();
		}

	}
}
