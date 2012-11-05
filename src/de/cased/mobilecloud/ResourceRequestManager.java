package de.cased.mobilecloud;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.util.Log;

import com.google.protobuf.ByteString;

import de.cased.mobilecloud.common.PeerCommunicationProtocol.ResourceRequestMessage;
import ext.org.bouncycastle.asn1.ASN1Integer;
import ext.org.bouncycastle.asn1.ASN1UTCTime;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import ext.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class ResourceRequestManager {

	// maybe this needs to be lowered, but I dont want to put more stress on the
	// mobile
	// private static final int defaultRefreshRate = 300;
	private RuntimeConfiguration config;

	private static final String TAG = "ResourceRequestManager";

	// private Pattern p;

	// private TCPdump tcpdump;
	private IpqWorker ipqworker;

	private static String ipqserverLocation = "/data/data/org.servalproject/bin/ipqserver";
	private static String socketLocation = "/data/data/org.servalproject/var/ipq.socket";

	// private Handler isHandler;
	private DataInputStream is;

	private boolean running = true;

	private List<ResourceRequestHistoryElement> history = new ArrayList<ResourceRequestHistoryElement>();
	private PeerClientWorker worker;

	public synchronized void addEntry(ResourceRequestHistoryElement entry) {
		// Log.d(TAG, "read addEntry()");
		if (!isAlreadyInHistory(entry)) {
			ResourceRequest request = generateResourceRequest(entry.getIp(),
					entry.getPort(),
					entry.getLayer4());

			Log.d(TAG,
					"RR for ip:" + entry.getIp() + "; layer4:"
							+ entry.getLayer4() + "; port:" + entry.getPort());

			try {
				byte[] encodedRequest = request.getEncoded();

				ResourceRequestMessage.Builder messageBuilder = ResourceRequestMessage
						.newBuilder();
				ByteString payload = ByteString.copyFrom(encodedRequest);

				messageBuilder.setPayload(payload);
				messageBuilder.setId(entry.getPacketId());
				ResourceRequestMessage message = messageBuilder.build();
				worker.sendMessageToMainHandler(message);
				history.add(entry);
				Log.d(TAG, "issue new RR with id:" + message.getId());

				// ipqworker.verdict(entry.getPacketId()); // TODO this needs to
				// be
														// moved!

			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		} else {
			Log.d(TAG, "its already in history");
			ipqworker.verdict(entry.getPacketId());

		}


	}

	private boolean isAlreadyInHistory(ResourceRequestHistoryElement entry) {
		// Log.d(TAG, "its already in history");
		for (ResourceRequestHistoryElement current : history) {
			if (current.getIp().equals(entry.getIp())
					&& current.getLayer4().equals(entry.getLayer4())
					&& current.getPort() == entry.getPort()) {
				// element already in list, check for freshness
				// Log.d(TAG, "element already in list, check for freshness");
				if (entry.getTimestamp().getTime()
						+ Long.parseLong(config
								.getProperty("resource_request_time_to_live")) > System
							.currentTimeMillis()) {
					// old element is still valid, dont do anything
					// Log.d(TAG, "element is still valid, dont do anything");
					return true;

				} else {
					history.remove(current);
					return false;
				}

			}
		}
		return false;
	}


	public ResourceRequestManager(PeerClientWorker worker)
			throws NoIpqModuleException {
		this.worker = worker;

		String ipqKernelModule = "/system/lib/modules/ip_queue.ko";
		String answer = Utilities.runCommand(new String[] { ipqKernelModule });
		if (answer.indexOf("No such file or directory") > 0) {
			throw new NoIpqModuleException();
		} else {
			// all fine
			Log.d(TAG, "ipq module loaded and we are ready to roll");
			config = RuntimeConfiguration.getInstance();
		}

	}

	// private void readTcpLine(String line) {
	// try {
	// int firstSpace = line.indexOf(' ');
	// String layer3proto = line.substring(firstSpace + 1,
	// line.indexOf(' ', firstSpace + 1));
	//
	// if (layer3proto.equals("IP")) {
	// int indexOfProto = line.indexOf("proto");
	// String layer4proto = line.substring(indexOfProto + 6,
	// line.indexOf('(', indexOfProto) - 1);
	//
	// String intermediateDestination = line.substring(
	// line.indexOf(") ") + 2, line.indexOf(" >"));
	//
	// String sourceIP = intermediateDestination.substring(0,
	// intermediateDestination.lastIndexOf('.'));
	//
	// String sourcePort = intermediateDestination
	// .substring(intermediateDestination.lastIndexOf('.') + 1);
	//
	// int indexOfTag = line.indexOf(">");
	// int startIndex = indexOfTag + 2;
	// int endIndex = line.indexOf(':', indexOfTag);
	// String realDestTemp = line.substring(startIndex,
	// endIndex);
	// String destinationIP = realDestTemp.substring(0,
	// realDestTemp.lastIndexOf('.'));
	//
	// String destinationPort = realDestTemp.substring(realDestTemp
	// .lastIndexOf('.') + 1);
	//
	// // Log.d(TAG, "check if destination IP is tetherable:"
	// // + destinationIP); // TODO hier das gesamte
	// // paket anzeigen lassen und
	// // auswerten
	//
	// if (!destinationIP.startsWith("127")
	// && Utilities.isTargetForTetheringToServer(
	// destinationIP,
	// config)) {
	// // Log.d(TAG, "yes, its meant for the internet");
	// ResourceRequestHistoryElement element = new
	// ResourceRequestHistoryElement(
	// destinationIP, layer4proto,
	// Integer.parseInt(destinationPort),
	// new Date(), 0);
	// addEntry(element);
	//
	// }
//
	// } else {
	// Log.d(TAG, "skipping non IP packet");
	// Log.d(TAG, line);
	// }
//
	// } catch (Exception e) {
	// Log.d(TAG, "failed parsing tcpdump");
	// e.printStackTrace();
	// Log.d(TAG, line);
//		}
	//
	// // Matcher m = p.matcher(line);
	// // if (m.matches()) {
	// // String layer3proto = m.group(1);
	// // String layer4proto = m.group(2);
	// //
	// // String intermediateDestination = m.group(3);
	// // String destinationIP = intermediateDestination.substring(0,
	// // intermediateDestination.lastIndexOf('.'));
	// // String destinationPort = intermediateDestination
	// // .substring(intermediateDestination.lastIndexOf('.') + 1);
	// //
	// // Log.d(TAG, layer3proto + "/" + layer4proto + "->"
	// // + destinationIP + ":" + destinationPort);
	// // } else {
	// // Log.d(TAG, "fail:" + line);
	// // }
	// }

	private ResourceRequest generateResourceRequest(String ip, int port,
			String layer4) {
		ASN1Integer serial = new ASN1Integer(0);
		SubjectPublicKeyInfo pubK = SubjectPublicKeyInfo.getInstance(config
				.getToken().getPublicKey().getEncoded());

		X500Name subject = new X500Name(config.getToken().getSubjectDN().toString());
		ASN1UTCTime time = new ASN1UTCTime(new Date());
		ASN1Integer ipASN1 = new ASN1Integer(Utilities.stringIPtoInt(ip));
		ASN1Integer portASN1 = new ASN1Integer(port);
		ASN1Integer layer4ASN1 = new ASN1Integer(
				Utilities.ipProtocolToInt(layer4));

		 ResourceRequestBuilder rrBuilder = new ResourceRequestBuilder(serial, pubK,
 subject, time, ipASN1, portASN1, layer4ASN1);

		String sigName = "SHA1withRSA";
		String provider = "BC";

		try {
			ResourceRequest request = rrBuilder
					.build(new JcaContentSignerBuilder(sigName).setProvider(
							provider).build(config.getPrivKey()));
			return request;

		} catch (Exception e) {
			Log.d(TAG, "Request failed to be generated");
			e.printStackTrace();
			Log.e(TAG, e.getMessage(), e);
			return null;
		}

	}

	private void startIpqServer(String socket) {
		String command = ipqserverLocation + " " + socketLocation;
		Utilities.runCommand(new String[] { command });
	}

	private void stopIpqServer() {
		String stopCommand = "killall ipqserver";
		Utilities.runCommand(new String[] { stopCommand });
	}

	public void start() {
		Log.d(TAG, "reached ResourceRequestManager's start()");
		startIpqServer(socketLocation);
		ipqworker = new IpqWorker(socketLocation, this);
		ipqworker.start();
		Log.d(TAG, "ipqworker started");

		// while(running){
		// try {
		// while (is.available() > 0) {
		// String line = is.readLine();
		// if (line != null) {
		// // Log.d(TAG, "read line:" + line);
		// readTcpLine(line);
		// }
		// }
		//
		// try {
		// Thread.sleep(100);
		// } catch (InterruptedException ex) {
		// }
		//
		// } catch (Exception e) {
		// Log.d(TAG, "dropped in ResourceRequestManager's run()");
		// e.printStackTrace();
		// return;
		// }
		// }
		// Log.d(TAG,
		// "dropping out of tcpdump reader due to running != true or tcpdump object being null");
	}

	// public void startTCPdump() {
	// int returnValue = tcpdump.start(" -i any -vvv -n");
	// is = tcpdump.getInputStream();
	// Log.d(TAG, "starting tcpdump:" + returnValue);
	// // startRefreshing();
	// }

	public void startRedirect() {
		String command = "iptables -A OUTPUT -o tun1 -j QUEUE";
		Utilities.runCommand(new String[] { command });
	}

	public void stopRR() {
		// stopRefreshing();
		running = false;
		stopIpqServer();
		stopRedirect();
	}

	private void stopRedirect() {
		String command = "iptables -D OUTPUT -o tun1 -j QUEUE";
		Utilities.runCommand(new String[] { command });

	}

	public void verdict(long id) {
		if (ipqworker != null) {
			ipqworker.verdict(id);
		}
	}

	// private int bufferSize = 4096;
	// private Runnable updateOutputText = new Runnable() {
	// @Override
	// public void run() {
	//
	// if (tcpdump != null) {
	// try {
	// // if ((tcpdump.getInputStream().available() > 0) == true) {
	// // byte[] buffer = new byte[bufferSize];
	// //
	// // try {
	// // tcpdump.getInputStream()
	// // .read(buffer, 0, bufferSize);
	// // } catch (IOException e) {
	// // return;
	// // }
	// //
	// // Log.d(TAG, new String(buffer));
	// //
	// // }
	// while (is.available() > 0) {
	// String line = is.readLine();
	// // Log.d(TAG, line);
	// readTcpLine(line);
	// }
	//
	// } catch (Exception e) {
	// return;
	// }
	// // isHandler.postDelayed(updateOutputText, defaultRefreshRate);
	// } else {
	// Log.d(TAG,
	// "internal variables are not initialized, did the constructor run?");
	// }
	// }
	// };
	// private boolean refreshingActive = false;
	//
	// // private void startRefreshing() {
	// // if (!refreshingActive) {
	// // // isHandler.post(updateOutputText);
	// // refreshingActive = true;
	// // }
	// // }
	//
	// private void stopRefreshing() {
	// // if (refreshingActive) {
	// // // isHandler.removeCallbacks(updateOutputText);
	// // refreshingActive = false;
	// // }
	// }
}
