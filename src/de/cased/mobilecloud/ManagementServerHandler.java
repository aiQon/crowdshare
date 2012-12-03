package de.cased.mobilecloud;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import de.cased.mobilecloud.common.PeerCommunicationProtocol.BatteryState;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Capability;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CapabilityItem;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CloseManagementChannel;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.CloseVPN;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ConnectionACK;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ConnectionNACK;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Hello;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.HelloResponse;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.KeepAlive;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.NeedPrivateSetIntersection;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.PrivateSetIntersectionNACK;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.PrivateSetIntersectionResponse;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.Quota;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ResourceRequestGrantMessage;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.ResourceRequestMessage;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNGranted;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRefused;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRequest;
import de.cased.mobilecloud.common.PeerCommunicationStateContext;
import de.cased.mobilecloud.fof.ClientStepContainer;
import de.cased.mobilecloud.fof.FofNonceService;
import de.cased.mobilecloud.fof.IRemoteFofNonceService;
import de.cased.mobilecloud.fof.ServerFinalStepContainer;
import de.cased.mobilecloud.fof.ServerInitialStepContainer;
import ext.org.bouncycastle.operator.ContentVerifierProvider;
import ext.org.bouncycastle.operator.OperatorCreationException;
import ext.org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import ext.org.bouncycastle.pkcs.PKCSException;

/**
 * The handler class of the p2p management server.
 * It handles clients.
 *
 * @author stas
 *
 */
public class ManagementServerHandler extends Thread {

	private static String TAG = "ManagementHandler";

	private SSLSocket socket;

	// private DataOutputStream writer;
	// private DataInputStream reader;

	private ObjectOutputStream writer;
	private ObjectInputStream reader;

	private List<Long> grantedPacketIDs = new ArrayList<Long>();

	private PeerCommunicationStateContext protocolState;
	private boolean running;
	private PeerServerWorker backreference;
	private Object latestMessage;
	private RuntimeConfiguration config;

	private Timer timer;

	private X509Certificate peerCertificate;

	// private PrivateSetIntersectionCardinality setIntersection;
	// private BigInteger Rc;
	// private BigInteger Rc_inv;

	// private List<String> friends;
	// private String me;

	private long tsStart;
	private long tsStop;

	private String vpnAddress;

	// private Context context;
	private IRemoteFofNonceService mIRemoteService;

	public ManagementServerHandler(SSLSocket client, PeerServerWorker backref,
			Context context) {
		this.socket = client;
		running = true;
		backreference = backref;
		config = RuntimeConfiguration.getInstance();
		// this.context = context;
		buildProtocolReactions();
		context.bindService(new Intent(context, FofNonceService.class),
				mConnection,
				Context.BIND_AUTO_CREATE);
	}

	public void setPeerCertificate(X509Certificate peerCert) {
		this.peerCertificate = peerCert;
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		// Called when the connection with the service is established
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// Following the example above for an AIDL interface,
			// this gets an instance of the IRemoteInterface, which we can use
			// to call on the service
			mIRemoteService = IRemoteFofNonceService.Stub.asInterface(service);
			synchronized (ManagementServerHandler.this) {
				ManagementServerHandler.this.notifyAll();
			}
		}

		// Called when the connection with the service disconnects unexpectedly
		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.e(TAG, "Service has unexpectedly disconnected");
			mIRemoteService = null;
		}
	};

	private void buildProtocolReactions() {
		protocolState = new PeerCommunicationStateContext();
		analyzeHello();
		analyzePrivateSetIntersectionResponse();
		analyzeConnectionNACK();
		analyzeConnectionACK();
		analyzeVPNRequest();
		analyzeCloseVPN();
		analyzeCloseManagementChannel();
		analyzeResourceRequest();
	}

	private void analyzePrivateSetIntersectionResponse() {
		protocolState
				.setStateAction(
						PeerCommunicationStateContext.PRIVATE_SET_INTERSECTION_DONE_STATE,
						new Runnable() {
							@Override
							public void run() {
								if (latestMessage instanceof PrivateSetIntersectionResponse) {
									Log.d(TAG,
											"received PrivateSetIntersectionResponse");
									PrivateSetIntersectionResponse received = (PrivateSetIntersectionResponse) latestMessage;
									List<ByteString> aTickList = received
											.getATickList();
									List<ByteString> tsList = received
											.getTsList();
									BigInteger ATICK = extractATICK(received);
									BigInteger[] aTick = extractATick(aTickList);
									byte[][] ts = extractTs(tsList);

									ClientStepContainer container = new ClientStepContainer(
											ts, ATICK, aTick);

									try {
										ServerFinalStepContainer result = mIRemoteService
												.finalServerStep(container);

										tsStop = System.currentTimeMillis();

										long totalTime = tsStop - tsStart;
										Log.d(TAG, "total time:" + totalTime
												+ " ms");

										int requiredFoFs = config
												.getPreferences()
												.getInt(SecuritySetupActivity.ENABLE_FOF_TETHERING,
														0);

										Log.d(TAG, "Direct Friend is "
														+ result.isDirectFriend());

										if (result.isDirectFriend()
												&& config
														.getPreferences()
														.getBoolean(
																SecuritySetupActivity.ENABLE_DIRECT_FRIENDS_TETHERING,
																false)) {
											Log.d(TAG, "got direct friend");
											sendPositiveResponse();
										} else if (requiredFoFs > 0
												&& result.getCommonFriends() >= requiredFoFs) {
											Log.d(TAG,
													"got enough indirect friends");
											sendPositiveResponse();
										} else {
											Log.d(TAG,
													"dont have enough friends nor a direct friend");
											sendNegativeRespnse();
										}

									} catch (RemoteException e) {
										Log.d(TAG, e.getMessage());
									}

									// Boolean[] FOUND = new Boolean[1];
									// setIntersection
									// .setNumberOfClientFriends(tsList
									// .size());

									// int found =
									// setIntersection.server_round_3(
									// aTick,
									// ts, Rc_inv, ATICK, FOUND);

								}
							}

							private BigInteger extractATICK(
									PrivateSetIntersectionResponse received) {
								ByteString ATICKcapsule = received
										.getATICK();
								byte[] tempATICK = new byte[ATICKcapsule
										.size()];
								ATICKcapsule.copyTo(tempATICK, 0);
								BigInteger ATICK = new BigInteger(tempATICK);
								return ATICK;
							}

							private void sendNegativeRespnse() {
								PrivateSetIntersectionNACK nack = PrivateSetIntersectionNACK
										.newBuilder().build();
								sendMessage(nack);
							}

							private void sendPositiveResponse() {


								Capability.Builder capBuilder = Capability
										.newBuilder();
								capBuilder
										.addCapability(CapabilityItem.INTERNET);

								sendHelloResponse(capBuilder.build());
							}

							private byte[][] extractTs(List<ByteString> tsList) {
								byte ts[][] = new byte[tsList.size()][];
								for (int i = 0; i < tsList.size(); i++) {
									ts[i] = new byte[tsList
											.get(i).size()];
									tsList.get(i).copyTo(ts[i], 0);
								}
								return ts;
							}

							private BigInteger[] extractATick(
									List<ByteString> aTickList) {
								BigInteger[] aTick = new BigInteger[aTickList
										.size()];
								for (int i = 0; i < aTickList.size(); i++) {
									ByteString extractedBS = aTickList
											.get(i);
									byte[] tempHolder = new byte[extractedBS
											.size()];
									extractedBS.copyTo(tempHolder, 0);
									aTick[i] = new BigInteger(tempHolder);
								}
								return aTick;
							}
						});
	}

	private void addToGrantedIDs(long id) {
		synchronized (grantedPacketIDs) {
			grantedPacketIDs.add(id);
		}
	}

	// private boolean isAlreadyGranted(long id) {
	// synchronized (grantedPacketIDs) {
	// return grantedPacketIDs.contains(id);
	// }
	// }

	private class ResourceRequestAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof ResourceRequestMessage) {


				final ResourceRequestMessage message = (ResourceRequestMessage) latestMessage;

				Log.d(TAG,
						"Received ResourceRequestMessage: " + message.getId());
				// if (isAlreadyGranted(message.getId())) {
				// return;
				// }

				new Thread() {
					@Override
					public void run() {
						long messageId = message.getId();
						byte[] tempTarget = new byte[message.getPayload()
								.size()];
						Log.d(TAG, "received encapsulated payload with size:"
								+ message.getPayload().size());
						message.getPayload().copyTo(tempTarget, 0);
						try {
							ResourceRequest request = new ResourceRequest(
									tempTarget);

							SSLSession session = socket.getSession();
							Log.d(TAG,
									"used cipher suite is:"
											+ session.getCipherSuite());

							ContentVerifierProvider contentVerifierProvider = new JcaContentVerifierProviderBuilder()
									.setProvider("BC").build(
											peerCertificate.getPublicKey());
							if (!request
									.isSignatureValid(contentVerifierProvider)) {
								Log.d(TAG, "invalid signature");
							} else {
								Log.d(TAG,
										"Resource Request signed correctly, retrieving information");
								final int port = request.getPort();
								final String transportLayer = Utilities
										.ipHeaderIdToString(request
												.getTransportLayer());
								final String destinationAddress = Utilities
										.intIpToString(request.getIP());

								ResourceRequestGrantMessage grantMessage = ResourceRequestGrantMessage
										.newBuilder().setId(messageId)
										.build();


								persistRR(request, destinationAddress);
								final String sourceAddress = getVpnAddress(getRealAddress());

								config.allowMasqueradeFor(sourceAddress,
										destinationAddress, transportLayer,
										port + "");
								new Thread(new Runnable() {
									@Override
									public void run() {
										try {
											Thread.sleep(Integer.parseInt(config
													.getProperty("resource_request_time_to_live")));
											config.removeMasqueradeFor(
													sourceAddress,
													destinationAddress,
													transportLayer, port + "");
										} catch (NumberFormatException e) {
											Log.e(TAG, e.getMessage(), e);
										} catch (InterruptedException e) {
											Log.e(TAG, e.getMessage(), e);
										}
									}
								}).start();
								addToGrantedIDs(messageId);
								latestMessage = null;
								Log.d(TAG, "sending verdict for:" + messageId);
								sendMessage(grantMessage);

							}

						} catch (IOException e) {
							Log.e(TAG, e.getMessage(), e);
							Log.d(TAG,
									"failed retrieving ResourceRequest from byte array");
						} catch (OperatorCreationException e) {
							Log.e(TAG, e.getMessage(), e);
						} catch (PKCSException e) {
							Log.e(TAG, e.getMessage(), e);
						}
					}
				}.start();



				latestMessage = null;
			}
		}

		private void persistRR(ResourceRequest request, String ipAddress)
				throws FileNotFoundException, IOException {
			File rrDir = new File(config.getProperty("rr_dir"),
 peerCertificate
					.getSubjectDN().getName().substring(3));
			rrDir.mkdirs();
			String filename = System.currentTimeMillis() + "";

			File destinationFile = new File(rrDir, filename);
			FileOutputStream fos = new FileOutputStream(
					destinationFile);
			fos.write(request.getEncoded());
			fos.close();
		}
	}

	private void analyzeResourceRequest() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new ResourceRequestAnalyzer());
	}

	private class CloseManagementChannelAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof CloseManagementChannel) {
				Log.d(TAG, "Closing management channel gracefully");
				halt();
				latestMessage = null;
			}
		}
	}

	private void analyzeCloseManagementChannel() {
		protocolState
				.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
						new CloseManagementChannelAnalyzer());
	}

	private class CloseVPNChannelAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof CloseVPN) {
				closeVPN();
				latestMessage = null;
			}
		}
	}

	private void analyzeCloseVPN() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new CloseVPNChannelAnalyzer());
	}


	private class HelloAnalyzer implements Runnable {
		@Override
		public void run() {
			if(latestMessage instanceof Hello){
				Hello msg = (Hello) latestMessage;
				reactOnHello(msg);
				latestMessage = null;
			}
		}
	}

	private void analyzeHello() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.HELLO_SENT_STATE, new HelloAnalyzer());
	}

	private void analyzeConnectionNACK(){
		protocolState.setStateAction(PeerCommunicationStateContext.HELLO_RESPONSE_SENT_STATE, new Runnable(){
			@Override
			public void run() {
				if(latestMessage instanceof ConnectionNACK){
					Log.d(TAG, "received connection NACK, dying gracefully");
					halt();
							latestMessage = null;
				}
			}
		});
	}

	private class ConnectionACKAnalyzer implements Runnable {
		@Override
		public void run() {
			if(latestMessage instanceof ConnectionACK){
				Log.d(TAG, "received connection ACK, connection elevated to management channel");
								startKeepAliveTimer();
								latestMessage = null;
			}
		}
	}

	private void analyzeConnectionACK() {
		protocolState.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
				new ConnectionACKAnalyzer());
	}

	protected void startKeepAliveTimer() {
		Log.d(TAG, "starting KeepAlive timer");
		TimerTask updateNeighborList = new TimerTask() {
			@Override
			public void run() {
				synchronized (this) {
					KeepAlive.Builder keepAliveBuilder = KeepAlive.newBuilder();
					Quota.Builder quotaBuilder = Quota.newBuilder();
					quotaBuilder.setBatteryState(BatteryState.FULL); // TODO
					quotaBuilder.setDataQuotaInBytes(10000); // TODO
					keepAliveBuilder.setQuota(quotaBuilder.build());
					sendMessage(keepAliveBuilder.build());
				}
			}
		};
		timer = new Timer();
		timer.scheduleAtFixedRate(
				updateNeighborList,
				new Date(),
				Integer.parseInt(config.getProperties().getProperty(
						"keep_alive_delay"))); //

	}

	private class VPNRequestAnalyzer implements Runnable {
		@Override
		public void run() {
			if (latestMessage instanceof VPNRequest) {
				Log.d(TAG, "received VPN request");
				VPNRequest req = (VPNRequest) latestMessage;

				if (canProvideVPN()) {
					Log.d(TAG, "can provide VPN");
					if (openVPN()) {
						Log.d(TAG, "successfully opened firewall for client");
						sendMessage(VPNGranted.newBuilder().build());
					} else {
						Log.d(TAG, "failed to open firewall for client");
						sendMessage(VPNRefused.newBuilder().build());
					}
				} else {
					Log.d(TAG, "cant provide VPN");
					sendMessage(VPNRefused.newBuilder().build());
				}
			}
		}

	}

	// private boolean validate(X509Certificate tempCert)
	// throws CertificateException {
	// X509Certificate signCert = Utilities.loadCertificate(new File(config
	// .getCertificateDir(), config.getProperties().getProperty(
	// "ca_cert_dest_loc")));
	//
	// if(!signCert.equals(signCert)){
	// try
	// { //Not your CA's. Check if it has been signed by your CA
	// signCert.verify(signCert.getPublicKey());
	// }
	// catch(Exception e){
	// throw new CertificateException("Certificate not trusted",e);
	// }
	// }
	// //If we end here certificate is trusted. Check if it has expired.
	// try{
	// signCert.checkValidity();
	// }
	// catch(Exception e){
	// throw new
	// CertificateException("Certificate not trusted. It has expired",e);
	// }
	// return true;
	// }

	private void analyzeVPNRequest() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.VPN_REQUEST_ISSUED_STATE,
				new VPNRequestAnalyzer());
	}

	/**
	 * Normally the OpenVPN server is running but blocked by iptables rules.
	 * This function should open the connection for the requester.
	 */
	private boolean openVPN() {

		final String realAddress = getRealAddress();


		Log.d(TAG, "opening VPN for real:" + realAddress);

		config.allowOVpnAccessForIP(realAddress);


		new Thread(
			new Runnable() {
				@Override
				public void run() {
						String vpnAddress;
				int count = 120;
				while ((vpnAddress = getVpnAddress(realAddress)) == null
						&& count != 0) {
							try {
						count--;
								Thread.sleep(500);
							} catch (InterruptedException e) {
								Log.e(TAG, e.getMessage(), e);
							}
						}
				if (!config.getPreferences().getBoolean(
						SecuritySetupActivity.ENABLE_LIABILITY, false)) {
					config.allowMasqueradeForIP(vpnAddress);
				}
				// config.allowMasqueradeForIP(vpnAddress);
				}
				}).start();

		// String vpnAddress = getVpnAddress(realAddress);


		return true;


	}


	private String getVpnAddress(String realAddress) {

		if (vpnAddress != null) {
			return vpnAddress;
		} else {

			String statusPath = config.getOvpnStatusPath();
			try {
				String command = "grep 'ROUTING TABLE' " + statusPath
						+ " -A 9999 | grep " + realAddress;

				Log.d(TAG, "getVpnAddress(" + realAddress + ") with command:");

				String statusOutput = config.getApp().coretask
						.runCommandForOutput(false, command);

				Log.d(TAG, "output:" + statusOutput);

				if (statusOutput.length() > 0) {
					String vpnAddress = statusOutput.substring(0,
							statusOutput.indexOf(','));
					Log.d(TAG, "found VPNAddress:" + vpnAddress);
					this.vpnAddress = vpnAddress;
					return vpnAddress;
				} else {
					return null;
				}

			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			return null;
		}
	}

	private String getRealAddress() {
		return socket.getRemoteSocketAddress().toString().split("/")[0];
	}

	/**
	 * Block the OpenVPN server again for this client, i.e. remove the client's grant.
	 */
	private void closeVPN() {
		String realAddress = getRealAddress();
		String vpnAddress = getVpnAddress(realAddress);

		Log.d(TAG, "closing VPN for real:" + realAddress + " and vpn:"
				+ vpnAddress);

		config.blockOVpnAccessForIP(realAddress);
		config.removeMasqueradeForIP(vpnAddress);
	}

	private boolean canProvideVPN() {
		//TODO implement logic
		return true;
	}

	private void reactOnHello(Hello msg) {
		Log.d(TAG, "reacting on hello message");

		while (mIRemoteService == null) {
			synchronized (this) {
				try {
					wait();
				} catch (InterruptedException e) {
					Log.d(TAG, e.getMessage());
				}
			}
		}

		// Branching here: either its anonymous or server side FB friend list
		// then just send a hello
		// Response or if its private set intersection, send a
		// "NeedPrivateSetIntersection" Signal

		boolean christofaroWithIDs = config
				.getPreferences()
				.getBoolean(
						SecuritySetupActivity.ENABLE_TETHERING_FRIENDS_SET_INTERSECTION,
						false);
		boolean christofaroWithNonces = config
				.getPreferences()
				.getBoolean(
						SecuritySetupActivity.ENABLE_TETHERING_NONCE,
						false);


		if (christofaroWithIDs || christofaroWithNonces) {
			try {
				Log.d(TAG, "start private set intersection procedure");
				mIRemoteService.initEngine(christofaroWithNonces);
			} catch (RemoteException e) {
				Log.d(TAG, e.getMessage());
			}

			// loadMe(christofaroWithNonces);
			// loadFriends(christofaroWithNonces);
			tsStart = System.currentTimeMillis();
			// setIntersection = new PrivateSetIntersectionCardinality();
			// setIntersection.setNumberOfServerFriends(friends.size());
//			serverInitialStep(christofaroWithNonces);
			try {
				ServerInitialStepContainer initialStep = mIRemoteService
						.serverInitialStep();

				NeedPrivateSetIntersection.Builder needBuilder = NeedPrivateSetIntersection
						.newBuilder();
				// List<ByteString> aEncoded = new ArrayList<ByteString>();
				for (BigInteger bigI : initialStep.getFriends()) {
					byte[] temp = bigI.toByteArray();
					ByteString bs = ByteString.copyFrom(temp);
					needBuilder.addA(bs);
				}

				ByteString aoCapsule = ByteString.copyFrom(initialStep.getMe()
						.toByteArray());
				needBuilder.setA0(aoCapsule);

				// needBuilder.setNumberOfFriends(friends.size());
				needBuilder.setNeedFBbWallNonces(christofaroWithNonces);

				sendMessage(needBuilder.build());

			} catch (RemoteException e) {
				Log.d(TAG, e.getMessage());
			}
		} else {

			// Capability capa = msg.getCapabilities();
			Capability intersection = getCapabilityIntersection(msg
					.getCapabilities());
			// List<CapabilityItem> cap = msg.getCapabilityList();
			sendHelloResponse(intersection);
		}
	}

	private void sendHelloResponse(Capability caps) {
		HelloResponse.Builder builder = HelloResponse.newBuilder();
		Quota quota = getQuota();
		builder.setCapabilities(caps);
		builder.setQuota(quota);
		if (config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_LIABILITY, false)) {
			builder.setNeedLiability(true);
		} else {
			builder.setNeedLiability(false);
		}
		sendMessage(builder.build());
	}


	// private void loadMe(boolean needNonce) {
	// if (!needNonce)
	// loadFacebookIdMe();
	// else
	// loadNonceMe();
	// }

	// private void loadNonceMe() {
	// String localme = config.getProperty("menonce");
	// List<String> myInfo = Utilities.readFromFile(localme, config.getApp());
	// me = myInfo.get(0);
	//
	// }
	//
	// private void loadFacebookIdMe() {
	// String localme = config.getProperty("myInfo");
	// List<String> myInfo = Utilities.readFromFile(localme, config.getApp());
	// me = myInfo.get(0).substring(0, myInfo.get(0).indexOf(':'));
	// }

	// private int loadFriends(boolean noncesNeeded) {
	// if (!noncesNeeded)
	// return loadLocalFriends();
	// else
	// return loadLocalNonces();
	// }

	// private int loadLocalNonces() {
	// String localfriends = config.getProperty("nonce_location");
	//
	// friends = new ArrayList<String>();
	// List<String> friendList = Utilities.readFromFile(localfriends,
	// config.getApp());
	// for (String r1 : friendList) {
	// if (r1 != null && !r1.equals("")) {
	//
	// friends.add(r1);
	// }
	// }
	// return friends.size();
	// }

	// private int loadLocalFriends() {
	// String localfriends = config.getProperty("localfriends");
	//
	// friends = new ArrayList<String>();
	// List<String> friendList = Utilities.readFromFile(localfriends,
	// config.getApp());
	// for (String r1 : friendList) {
	// int colon = -1;
	// if (r1 != null && !r1.equals("") && (colon = r1.indexOf(":")) > 0) {
	//
	// friends.add(r1.substring(0, colon));
	// }
	// }
	// return friends.size();
	// }

	// private void serverInitialStep(boolean needNonces) {
	// BigInteger[] a = new BigInteger[friends.size()];
	// byte[][] c = new byte[friends.size()][];
	// byte[] C = new BigInteger(me, needNonces ? 16 : 10).toByteArray();
	// BigInteger[] A = new BigInteger[1];
	//
	// for (int i = 0; i < friends.size(); i++) {
	// BigInteger friendInt = new BigInteger(friends.get(i),
	// needNonces ? 16 : 10);
	// Log.d(TAG, "added friend " + friends.get(i));
	// c[i] = friendInt.toByteArray();
	// }
	// BigInteger Rc = setIntersection.server_round_1(c, a, C, A);
	//
	// Rc_inv = setIntersection.server_round_2(Rc);
	//
	// NeedPrivateSetIntersection.Builder needBuilder =
	// NeedPrivateSetIntersection
	// .newBuilder();
	// // List<ByteString> aEncoded = new ArrayList<ByteString>();
	// for (BigInteger bigI : a) {
	// byte[] temp = bigI.toByteArray();
	// ByteString bs = ByteString.copyFrom(temp);
	// needBuilder.addA(bs);
	// }
	//
	// ByteString aoCapsule = ByteString.copyFrom(A[0].toByteArray());
	// needBuilder.setA0(aoCapsule);
	//
	// needBuilder.setNumberOfFriends(friends.size());
	// needBuilder.setNeedFBbWallNonces(needNonces);
	//
	// sendMessage(needBuilder.build());
	// }

	private Quota getQuota() {
		// TODO implement
		Quota.Builder builder = Quota.newBuilder();
		BatteryState battery = BatteryState.FULL;
		builder.setBatteryState(battery);
		builder.setDataQuotaInBytes(1000000);
		return builder.build();
	}

	private Capability getCapabilityIntersection(
			Capability capabilities) {
		Log.d(TAG, "reached capability intersection");

		Capability.Builder builder = Capability.newBuilder();

		for (CapabilityItem capItem : capabilities.getCapabilityList()) {

			if ((capItem == CapabilityItem.INTERNET)
					&& Utilities.is3GAvailable(config)) {
				Log.d(TAG,
						"issuer requested Internet and I can provide Internet");
				if (isAnonymousAllowed() || (isFriendAllowed() && isFriend())) {
					builder.addCapability(CapabilityItem.INTERNET);
				}
			}
		}

		return builder.build();
	}


	private boolean isFriend() {
		String r1Destination = config.getProperty("friendlist_r1");
		String r2Destination = config.getProperty("friendlist_r2");
		String subject = peerCertificate.getSubjectDN().getName().substring(3);
		Log.d(TAG, "requester's subject is " + subject);

		return isFriend(r1Destination, subject)
				|| isFriend(r2Destination, subject);

	}

	private boolean isFriend(String friendFile, String subject) {
		List<String> r1Friends = Utilities.readFromFile(friendFile,
				config.getApp());
		Log.d(TAG, "loaded list" + friendFile);
		for (String friend : r1Friends) {
			Log.d(TAG, "checking on:" + friend);
			if (subject.equals(friend)) {
				Log.d(TAG, "is friend according to " + friendFile);
				return true;
			}
		}
		return false;
	}

	private boolean isFriendAllowed() {

		Log.d(TAG, "checking if friend tethering is granted");

		if (config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING_FRIENDS, false)) {
			Log.d(TAG, "indeed it is");
		} else {
			Log.d(TAG, "its not");
		}
		return config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING_FRIENDS, false);
	}

	private boolean isAnonymousAllowed() {
		Log.d(TAG, "checking if anonymous tethering is granted");

		if (config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING, false)) {
			Log.d(TAG, "indeed it is");
		} else {
			Log.d(TAG, "its not");
		}

		return config.getPreferences().getBoolean(
				SecuritySetupActivity.ENABLE_TETHERING, false);
	}

	@Override
	public void run(){
		Log.d(TAG,
				"starting Management Server handler, waiting for streams to be retrieved");


		try {

			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			// writer = new DataOutputStream(os);
			// reader = new DataInputStream(is);

			writer = new ObjectOutputStream(os);
			reader = new ObjectInputStream(is);

			Log.d(TAG, "SSL Handshake finished, got Object Streams");
			performProtocol();

		} catch (Exception e) {
			Log.d(TAG, "failed to retrieve streams");
			e.printStackTrace();
			halt();
		}

		// socket.addHandshakeCompletedListener(new HandshakeCompletedListener()
		// {
		// @Override
		// public void handshakeCompleted(HandshakeCompletedEvent event) {
		// try {
		// ois = new ObjectInputStream(socket.getInputStream());
		// oos = new ObjectOutputStream(socket.getOutputStream());
		// Log.d(TAG, "SSL Handshake finished");
		// performProtocol();
		//
		// } catch (Exception e) {
		// e.printStackTrace();
		// halt();
		// }
		// }
		// });
	}

	private void performProtocol() {
		Log.d(TAG, "Connection established on server side");

		while(running){
			receiveMessage();
		}
		halt();
	}

	private void receiveMessage() {
		try {
			latestMessage = reader.readObject();
			Log.d(TAG, "received: " + latestMessage.getClass().getCanonicalName());
			protocolState.receiveEvent(latestMessage.getClass().getCanonicalName());
		} catch (Exception e) {
			Log.d(TAG, "something went wrong, I quit");
			e.printStackTrace();
			halt();
		}

	}

	private synchronized void sendMessage(Message msg) {
		try {
			Log.d(TAG, "going to send " + msg.getClass().getCanonicalName());
			writer.writeObject(msg);
			writer.flush();


			protocolState.receiveEvent(msg.getClass().getCanonicalName());
		} catch (IOException e) {
			e.printStackTrace();
			halt();
		}

	}

	public synchronized void halt() {
		// stop this thread, either something went wrong or we are dying
		// gracefully
		try{
			writer.close();
			reader.close();
			socket.close();
			running = false;
			timer.cancel();
			timer.purge();
			timer = null;
		}catch (Exception e){
			//dont care
		}finally{
			closeVPN();
			backreference.deleteHandler(this);
		}
	}

	public void stopMe() {
		synchronized (this) {
			running = false;
		}
	}


}
