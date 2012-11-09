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
	private List<ResourceRequestHistoryElement> pending = new ArrayList<ResourceRequestHistoryElement>();
	private ManagementClientHandler handler;

	public synchronized void addEntry(ResourceRequestHistoryElement entry) {
		if (!isAlreadyInHistory(entry) && !isAlreadyPending(entry)) {
			ResourceRequest request = generateResourceRequest(entry.getIp(),
					entry.getPort(),
					entry.getLayer4());
			try {
				byte[] encodedRequest = request.getEncoded();

				ResourceRequestMessage.Builder messageBuilder = ResourceRequestMessage
						.newBuilder();
				ByteString payload = ByteString.copyFrom(encodedRequest);

				messageBuilder.setPayload(payload);
				messageBuilder.setId(entry.getPacketId());
				ResourceRequestMessage message = messageBuilder.build();
				pending.add(entry);
				Log.d(TAG,
						"issue new RR with id:" + message.getId() + " -> "
								+ entry.getIp() + ":" + entry.getPort() + "("
								+ entry.getLayer4() + ")");
				handler.sendMessage(message);
				// ipqworker.verdict(entry.getPacketId()); // TODO this needs to
				// be
														// moved!

			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		} else {
			Log.d(TAG, "its already in history");
			ipqworker.verdict(entry.getPacketId(), entry.getIp(),
					entry.getPort());
		}
	}

	private boolean isAlreadyPending(ResourceRequestHistoryElement entry) {
		for (ResourceRequestHistoryElement current : pending) {
			if (current.getIp().equals(entry.getIp())
					&& current.getLayer4().equals(entry.getLayer4())
					&& current.getPort() == entry.getPort()) {
				if (entry.getTimestamp().getTime()
						+ Long.parseLong(config
								.getProperty("resource_request_pending_time")) > System
							.currentTimeMillis()) {
					return true;

				} else {
					pending.remove(current);
					return false;
				}

			}
		}
		return false;
	}

	private boolean isAlreadyInHistory(ResourceRequestHistoryElement entry) {
		for (ResourceRequestHistoryElement current : history) {
			if (current.getIp().equals(entry.getIp())
					&& current.getLayer4().equals(entry.getLayer4())
					&& current.getPort() == entry.getPort()) {
				if (entry.getTimestamp().getTime()
						+ Long.parseLong(config
								.getProperty("resource_request_time_to_live")) > System
							.currentTimeMillis()) {
					return true;

				} else {
					history.remove(current);
					return false;
				}

			}
		}
		return false;
	}


	public ResourceRequestManager(
			ManagementClientHandler managementClientHandler)
			throws NoIpqModuleException {
		this.handler = managementClientHandler;

		String ipqKernelModule = "insmod /system/lib/modules/ip_queue.ko";
		String answer = Utilities.runCommand(new String[] { ipqKernelModule });
		if (answer.indexOf("No such file or directory") > 0) {
			throw new NoIpqModuleException();
		} else {
			Log.d(TAG, "ipq module loaded and we are ready to roll");
			config = RuntimeConfiguration.getInstance();
		}
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

	}

	public void startRedirect() {
		String command = "iptables -A OUTPUT -o tun1 -j QUEUE";
		Utilities.runCommand(new String[] { command });
	}

	public void stopRR() {
		running = false;
		stopIpqServer();
		stopRedirect();
	}

	private void stopRedirect() {
		String command = "iptables -D OUTPUT -o tun1 -j QUEUE";
		Utilities.runCommand(new String[] { command });

	}

	public void verdict(long id) {
		moveFromPendingToHistory(id);

	}

	private void moveFromPendingToHistory(long id) {

		for (int i = 0; i < pending.size(); i++) {
			if (pending.get(i).getPacketId() == id) {
				ResourceRequestHistoryElement element = pending.get(i);
				history.add(element);
				pending.remove(element);
				if (ipqworker != null) {
					ipqworker.verdict(element.getPacketId(), element.getIp(),
							element.getPort());
				}
				break;
			}
		}
	}
}
