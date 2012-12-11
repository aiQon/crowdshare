package de.cased.mobilecloud;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class IpqWorker extends Thread{
	private List<String> workspace =  new ArrayList<String>();
	private List<Long> pendingIds = new ArrayList<Long>();
	private IpqReader reader;
	private BufferedWriter writer;
	private boolean running = true;
	private LocalSocket domainSocket;
	private ResourceRequestManager manager;
	private RuntimeConfiguration config;
	private static String TAG = "IPQWorker";

	public IpqWorker(String socket, ResourceRequestManager manager) {
		this.manager = manager;
		config = RuntimeConfiguration.getInstance();
		LocalSocketAddress clientSocketAddress = new LocalSocketAddress(socket,
				LocalSocketAddress.Namespace.FILESYSTEM);

	   domainSocket = new LocalSocket();
		int retries = 15;
		while (!domainSocket.isConnected() && retries > 0) {
			try {
				domainSocket.connect(clientSocketAddress);
				reader = new IpqReader(domainSocket.getInputStream(), this);
				reader.start();
				writer = new BufferedWriter(new OutputStreamWriter(
						domainSocket.getOutputStream()));

			} catch (Exception e) {
				Log.d(TAG, "couldnt bind to domain socket");
				e.printStackTrace();
			}
			retries--;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
		Log.d(TAG, "bound to domain socket");
	}

	@Override
	public void run() {
		Log.d(TAG, "reached IpqWorker run()");
		while(running){
			if(isWorkspaceReady()){
				String nextElement = getNextElementFromWorkspace();
				int equalSign = nextElement.indexOf('=');
				if(nextElement.length() > 0 && equalSign != -1){
					long packetId = Long.parseLong(nextElement.substring(0, equalSign));
					String sourceIp = nextElement.substring(equalSign+1, nextElement.indexOf('>'));
					String destinationIp = nextElement.substring(nextElement.indexOf('>')+1, nextElement.indexOf(':'));
					String destinationPort = nextElement.substring(
							nextElement.indexOf(':') + 1,
							nextElement.indexOf(','));
					int layer4protoInt = Integer.parseInt(nextElement
							.substring(nextElement.indexOf(',') + 1));
					String layer4proto = layer4protoInt == 17 ? "UDP" : "TCP";

					if (!destinationIp.startsWith("127")
							&& Utilities.isTargetForTetheringToServer(
									destinationIp, config)
							&& !pendingIds.contains(packetId)) {
						// Log.d(TAG, "yes, its meant for the internet");
						ResourceRequestHistoryElement element = new ResourceRequestHistoryElement(
								destinationIp, layer4proto,
								Integer.parseInt(destinationPort), new Date(),
								packetId);
						pendingIds.add(packetId);
						manager.addEntry(element);


					} else {
						verdict(packetId, destinationIp,
								Integer.parseInt(destinationPort));
					}
				}

			}else{
				try {
					synchronized(this){
						wait();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private int ipStringToInt(String ipString) {
		StringTokenizer token = new StringTokenizer(ipString, ".");
		int first = Integer.parseInt((String) token.nextElement());
		int second = Integer.parseInt((String) token.nextElement()) << 8;
		int third = Integer.parseInt((String) token.nextElement()) << 16;
		int fourth = Integer.parseInt((String) token.nextElement()) << 24;
		return first + second + third + fourth;

	}

	public void verdict(long id, String ip, int port) {
		try {
			pendingIds.remove(id);
			writer.write(id + ";" + ipStringToInt(ip) + ";" + port + "\n");
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public void addElementToWorkspace(String element){
		synchronized (workspace) {
			workspace.add(element);
			synchronized(IpqWorker.this){
				IpqWorker.this.notify();
			}
		}
	}

	public String getNextElementFromWorkspace(){
		synchronized (workspace) {
			if(workspace.size() > 0){
				String element = workspace.get(0);
				workspace.remove(0);
				return element;
			}else{
				return null;
			}
		}
	}

	public boolean isWorkspaceReady(){
		synchronized(workspace){
			if(workspace.size()>0){
				return true;
			}else{
				return false;
			}
		}
	}

	public void halt(){
		try {
			domainSocket.shutdownInput(); //this has to take place before we stop the reader, otherwise we get a segfault, dunno why
			reader.halt();
			running = false;
			domainSocket.shutdownOutput();
			domainSocket.close();
		} catch (IOException e) {
		} finally{
			domainSocket = null;
		}
	}
}
