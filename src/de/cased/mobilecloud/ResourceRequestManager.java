package de.cased.mobilecloud;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import android.util.Log;

import com.google.protobuf.ByteString;

import de.cased.mobilecloud.common.PeerCommunicationProtocol.ResourceRequestMessage;
import de.cased.mobilecloud.tcpdump.TCPdump;
import ext.org.bouncycastle.asn1.ASN1Integer;
import ext.org.bouncycastle.asn1.ASN1UTCTime;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import ext.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class ResourceRequestManager extends Thread {

	// maybe this needs to be lowered, but I dont want to put more stress on the
	// mobile
	private static final int defaultRefreshRate = 300;
	private RuntimeConfiguration config;

	private static final String TAG = "TCPDumpREADER";

	private Pattern p;

	private TCPdump tcpdump;
	// private Handler isHandler;
	private DataInputStream is;

	private boolean running = true;

	private List<ResourceRequestHistoryElement> history = new ArrayList<ResourceRequestHistoryElement>();
	private PeerClientWorker worker;

	public void addEntry(ResourceRequestHistoryElement entry) {
		if (!isAlreadyInHistory(entry)) {
			ResourceRequest request = generateResourceRequest(entry.getIp(),
					entry.getPort(),
					entry.getLayer4());
			try {
				byte[] encodedRequest = request.getEncoded();
				Log.d(TAG, "going to send encodedRequest of size:"
						+ encodedRequest.length);

				ResourceRequestMessage.Builder messageBuilder = ResourceRequestMessage
						.newBuilder();
				ByteString payload = ByteString.copyFrom(encodedRequest);
				Log.d(TAG, "encapsulated its:" + payload.size());

				messageBuilder.setPayload(payload);
				ResourceRequestMessage message = messageBuilder.build();
				worker.sendMessageToMainHandler(message);
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}


	}

	private boolean isAlreadyInHistory(ResourceRequestHistoryElement entry) {
		for (ResourceRequestHistoryElement current : history) {
			if (current.getIp().equals(entry.getIp())
					&& current.getLayer4().equals(entry.getLayer4())
					&& current.getPort() == entry.getPort()) {
				// element already in list, check for freshness
				if (entry.getTimestamp().getTime()
						+ Long.parseLong(config
								.getProperty("resource_request_time_to_live")) < System
							.currentTimeMillis()) {
					// old element is still valid, dont do anything
					return true;

				} else {
					history.remove(current);
					return false;
				}

			}
		}
		return false;
	}


	public ResourceRequestManager(PeerClientWorker worker) {
		this.worker = worker;
		tcpdump = new TCPdump();
		config = RuntimeConfiguration.getInstance();

	}

	private void readTcpLine(String line) {
		try {
			int firstSpace = line.indexOf(' ');
			String layer3proto = line.substring(firstSpace + 1,
					line.indexOf(' ', firstSpace + 1));

			if (layer3proto.equals("IP")) {
				int indexOfProto = line.indexOf("proto");
				String layer4proto = line.substring(indexOfProto + 6,
						line.indexOf('(', indexOfProto) - 1);

				String intermediateDestination = line.substring(
						line.indexOf(") ") + 2, line.indexOf(" >"));

				String destinationIP = intermediateDestination.substring(0,
						intermediateDestination.lastIndexOf('.'));

				String destinationPort = intermediateDestination
						.substring(intermediateDestination.lastIndexOf('.') + 1);

				if (Utilities.isTargetForTetheringToServer(destinationIP,
						config)) {
					ResourceRequestHistoryElement element = new ResourceRequestHistoryElement(
							destinationIP, layer4proto,
							Integer.parseInt(destinationPort),
							new Date());
					addEntry(element);

				} else {
					// Log.d(TAG, "packet is not going to be tethered");
				}

			} else {
				Log.d(TAG, "unrecognized layer3 protocol:");
				Log.d(TAG, line);
			}

		} catch (Exception e) {
			Log.d(TAG, "failed parsing tcpdump");
			Log.d(TAG, e.getMessage());
			Log.d(TAG, line);
		}

//		Matcher m = p.matcher(line);
//		if (m.matches()) {
//			String layer3proto = m.group(1);
//			String layer4proto = m.group(2);
//
//			String intermediateDestination = m.group(3);
//			String destinationIP = intermediateDestination.substring(0,
//					intermediateDestination.lastIndexOf('.'));
//			String destinationPort = intermediateDestination
//					.substring(intermediateDestination.lastIndexOf('.') + 1);
//
//			Log.d(TAG, layer3proto + "/" + layer4proto + "->"
//					+ destinationIP + ":" + destinationPort);
//		} else {
//			Log.d(TAG, "fail:" + line);
//		}
	}

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

	@Override
	public void run() {
		Log.d(TAG, "reached ResourceRequestManager's run()");
		int returnValue = tcpdump.start(" -i any -vvv -n");
		is = tcpdump.getInputStream();
		Log.d(TAG, "starting tcpdump:" + returnValue);


		while(running){
			if (tcpdump != null) {
				try {
					// if ((tcpdump.getInputStream().available() > 0) == true) {
					// byte[] buffer = new byte[bufferSize];
					//
					// try {
					// tcpdump.getInputStream()
					// .read(buffer, 0, bufferSize);
					// } catch (IOException e) {
					// return;
					// }
					//
					// Log.d(TAG, new String(buffer));
					//
					// }
					while (is.available() > 0) {
						String line = is.readLine();
						Log.d(TAG, line);
						readTcpLine(line);
					}

					try {
						Thread.sleep(100);
					} catch (InterruptedException ex) {
					}

				} catch (Exception e) {
					Log.d(TAG, "dropped in TCPdumpReader's run()");
					return;
				}
			}
		}
		Log.d(TAG,
				"dropping out of tcpdump reader due to running != true or tcpdump object being null");
	}

	// public void startTCPdump() {
	// int returnValue = tcpdump.start(" -i any -vvv -n");
	// is = tcpdump.getInputStream();
	// Log.d(TAG, "starting tcpdump:" + returnValue);
	// // startRefreshing();
	// }

	public void stopTCPdump() {
		// stopRefreshing();
		running = false;
		int TCPdumpReturn = tcpdump.stop();
		Log.d(TAG, "stopping tcpdump:" + TCPdumpReturn);
	}

	private int bufferSize = 4096;
	private Runnable updateOutputText = new Runnable() {
		@Override
		public void run() {

			if (tcpdump != null) {
				try {
					// if ((tcpdump.getInputStream().available() > 0) == true) {
					// byte[] buffer = new byte[bufferSize];
					//
					// try {
					// tcpdump.getInputStream()
					// .read(buffer, 0, bufferSize);
					// } catch (IOException e) {
					// return;
					// }
					//
					// Log.d(TAG, new String(buffer));
					//
					// }
					while (is.available() > 0) {
						String line = is.readLine();
						// Log.d(TAG, line);
						readTcpLine(line);
					}

				} catch (Exception e) {
					return;
				}
				// isHandler.postDelayed(updateOutputText, defaultRefreshRate);
			} else {
				Log.d(TAG,
						"internal variables are not initialized, did the constructor run?");
			}
		}
	};
	private boolean refreshingActive = false;

	// private void startRefreshing() {
	// if (!refreshingActive) {
	// // isHandler.post(updateOutputText);
	// refreshingActive = true;
	// }
	// }

	private void stopRefreshing() {
		// if (refreshingActive) {
		// // isHandler.removeCallbacks(updateOutputText);
		// refreshingActive = false;
		// }
	}
}
