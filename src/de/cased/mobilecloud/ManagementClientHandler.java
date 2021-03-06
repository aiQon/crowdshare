package de.cased.mobilecloud;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLPeerUnverifiedException;
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
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNGranted;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRefused;
import de.cased.mobilecloud.common.PeerCommunicationProtocol.VPNRequest;
import de.cased.mobilecloud.common.PeerCommunicationStateContext;
import de.cased.mobilecloud.fof.ClientStepContainer;
import de.cased.mobilecloud.fof.FofNonceService;
import de.cased.mobilecloud.fof.IRemoteFofNonceService;
import de.cased.mobilecloud.fof.ServerInitialStepContainer;

public class ManagementClientHandler extends Thread{

	private RuntimeConfiguration config;
	private SSLSocket socket;
	private PeerClientWorker backreference;

	// private DataOutputStream writer;
	// private DataInputStream reader;

	private ObjectOutputStream writer;
	private ObjectInputStream reader;

	private PeerCommunicationStateContext protocolState;
	private Object latestMessage;
	private List<CapabilityItem> requestedCapabilities;
	private RouteEntry routeInfo;

	private boolean running;
	private static String TAG = "ManagementClientHandler";

	private long lastKeepAlive = 0;
	private Timer timer;

	private Status currentStatus;

//	private PrivateSetIntersectionCardinality setIntersection;
	private List<String> friends;
	// private BigInteger Rs;
	// private byte[][] ts;

	private ResourceRequestManager rrManager;
	// private Context context;
	private IRemoteFofNonceService mIRemoteService;

	/**
	 * expects finished SSL handshake.
	 *
	 * @param connection
	 */
	public ManagementClientHandler(SSLSocket connection,
			List<CapabilityItem> capabilities, RouteEntry entry,
			PeerClientWorker peerClientWorker, Context context) {
		Log.d(TAG, "entered ManagementClientHandler constructor");
		socket = connection;
		this.requestedCapabilities = capabilities;
		running = true;
		config = RuntimeConfiguration.getInstance();
		routeInfo = entry;
		buildProtocolReactions();
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			// writer = new DataOutputStream(os);
			// reader = new DataInputStream(is);

			writer = new ObjectOutputStream(os);
			reader = new ObjectInputStream(is);

		} catch (Exception e) {
			Log.d(TAG, "failed to get client streams");
			e.printStackTrace();
			halt(false);
		}

		backreference = peerClientWorker;
		// this.context = context;
		setCurrentStatus(Status.Offline);

		context.bindService(new Intent(context, FofNonceService.class),
				mConnection, Context.BIND_AUTO_CREATE);

		try {
			rrManager = new ResourceRequestManager(this);
		} catch (NoIpqModuleException e) {
			Log.e(TAG, e.getMessage(), e);
			e.printStackTrace();
		}

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		// Called when the connection with the service is established
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// Following the example above for an AIDL interface,
			// this gets an instance of the IRemoteInterface, which we can use
			// to call on the service
			mIRemoteService = IRemoteFofNonceService.Stub.asInterface(service);
			synchronized (ManagementClientHandler.this) {
				ManagementClientHandler.this.notifyAll();
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
		analyzeHelloResponse();
		analyzeNeedSetIntersection();
		analyzePrivateSetIntersectionNACK();
		analyzeCloseManagementChannel();
		analyzeVPNRefused();
		analyzeVPNGranted();
		analyzeCloseVPN();
		analyzeKeepAlive();
		finalStateReaction();
		analyzeRRGrant();
	}

	private void analyzeRRGrant() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new Runnable() {
					@Override
					public void run() {

						if (latestMessage instanceof ResourceRequestGrantMessage) {
							ResourceRequestGrantMessage message = (ResourceRequestGrantMessage) latestMessage;
							Log.d(TAG, "verdict:" + message.getId());
							rrManager.verdict(message.getId());
						}
					}
				});
	}

	private void analyzePrivateSetIntersectionNACK() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.FINISHED_STATE,
				new Runnable() {
					@Override
					public void run() {

						if (latestMessage instanceof PrivateSetIntersectionNACK) {
							Log.d(TAG,
									"cannot connect to this one, we dont have the proper friend relation");
							halt(false);
							latestMessage = null;
						}
					}
				});
	}

	private void analyzeNeedSetIntersection() {
		protocolState.setStateAction(
						PeerCommunicationStateContext.PRIVATE_SET_INTERSECTION_MIDDLE_STATE,
						new Runnable() {
					@Override
					public void run() {
								if (latestMessage instanceof NeedPrivateSetIntersection) {
									NeedPrivateSetIntersection need = (NeedPrivateSetIntersection) latestMessage;
									setCurrentStatus(Status.Connecting);
									Log.d(TAG,
											"received NeedPrivateSetIntersection");

									while (mIRemoteService == null) {
										try {
											synchronized (ManagementClientHandler.this) {
												ManagementClientHandler.this
														.wait();
											}
										} catch (InterruptedException e) {
											Log.d(TAG, e.getMessage());
										}
									}

									try {
										Log.d(TAG,
												"Init client side service with nonces enabled:"
														+ need.getNeedFBbWallNonces());
										mIRemoteService.initEngine(need
												.getNeedFBbWallNonces());
										BigInteger[] a = extractA(need);
										BigInteger ao = extractA0(need);
										ServerInitialStepContainer serverStep = new ServerInitialStepContainer(
												ao, a);
										ClientStepContainer clientContainer = mIRemoteService.clientStep(serverStep);

										PrivateSetIntersectionResponse.Builder responseBuilder = PrivateSetIntersectionResponse
												.newBuilder();
										setATick(clientContainer.getFriends(), responseBuilder);
										setTs(clientContainer.getTs(),
												responseBuilder);
										ByteString capsule = ByteString
												.copyFrom(clientContainer.getMe().toByteArray());
										responseBuilder.setATICK(capsule);
										PrivateSetIntersectionResponse response = responseBuilder
												.build();
										sendMessage(response);
										Log.d(TAG, "send response");


									} catch (RemoteException e) {
										Log.d(TAG, e.getMessage());
									}

//									loadFriends(need.getNeedFBbWallNonces());
//									if(canProvideFriendInformation()){
//										initOwnSetIntersectionInstance();
//										performClientRound1(need
//												.getNeedFBbWallNonces());
//
//										int numberOfServerFriends = need
//												.getNumberOfFriends();
//										Log.d(TAG,
//												"number of server friends is "
//														+ numberOfServerFriends);
//										performClientStep(
//												need, numberOfServerFriends);
//									}else{
//										Log.d(TAG, "dont have a friend list");
//									}
								}
					}

							// private boolean canProvideFriendInformation() {
							// if (friends.size() > 0) {
							// return true;
							// } else {
							// return false;
							// }
							// }

//							private void performClientStep(
//							NeedPrivateSetIntersection need,
//							int numberOfServerFriends) {
//						BigInteger[] a = extractA(need);
//								BigInteger ao = extractA0(need);
//
//								Log.d(TAG, "extracted A");
//								for (BigInteger aPart : a) {
//									Log.d(TAG, aPart.toString());
//								}
//
//						setIntersection
//								.setNumberOfServerFriends(numberOfServerFriends);
//
//								BigInteger[] a_tick = new BigInteger[numberOfServerFriends];
//								BigInteger[] A_TICK = new BigInteger[1];
//						setIntersection.client_round_2(a, Rs,
// a_tick,
//										ao, A_TICK);
//
//								Log.d(TAG, "a_tick");
//								for (BigInteger aPart : a_tick) {
//									Log.d(TAG, aPart.toString());
//								}
//								PrivateSetIntersectionResponse.Builder responseBuilder = PrivateSetIntersectionResponse
//										.newBuilder();
//								setATick(a_tick, responseBuilder);
//								setTs(responseBuilder);
//								setATICK(A_TICK, responseBuilder);
//								PrivateSetIntersectionResponse response = responseBuilder
//										.build();
//								sendMessage(response);
//								Log.d(TAG, "send response");
//
//					}


//							private void setATICK(BigInteger[] a_TICK,
//									Builder responseBuilder) {
//								ByteString capsule = ByteString
//										.copyFrom(a_TICK[0].toByteArray());
//								responseBuilder.setATICK(capsule);
//							}

							private void setTs(
									byte[][] ts,
							PrivateSetIntersectionResponse.Builder responseBuilder) {
								Log.d(TAG, "ts content");
						for (byte[] ts_element : ts) {
							ByteString bs = ByteString
									.copyFrom(ts_element);
									Log.d(TAG, new String(ts_element));
							responseBuilder.addTs(bs);
						}
					}

					private void setATick(
							BigInteger[] a_tick,
							PrivateSetIntersectionResponse.Builder responseBuilder) {
						for (BigInteger bigI : a_tick) {
							byte[] temp = bigI.toByteArray();
							ByteString bs = ByteString
									.copyFrom(temp);
							responseBuilder.addATick(bs);
						}
					}

							private BigInteger[] extractA(
									NeedPrivateSetIntersection need) {
						List<ByteString> aEncoded = need.getAList();
						BigInteger[] a = new BigInteger[aEncoded
								.size()];
						for(int i = 0; i < aEncoded.size(); i ++){
							ByteString bs = aEncoded.get(i);
							byte[] tempArray = new byte[bs.size()];
							bs.copyTo(tempArray, 0);
							a[i] = new BigInteger(tempArray);
						}
								return a;
							}

							private BigInteger extractA0(
									NeedPrivateSetIntersection need) {
								ByteString aoCapsule = need.getA0();
								byte[] ao = new byte[aoCapsule.size()];
								aoCapsule.copyTo(ao, 0);
								return new BigInteger(ao);
					}

				});

	}

//	protected void performClientRound1(boolean needNonces) {
//		ts = new byte[friends.size()][];
//		byte[][] s = new byte[friends.size()][];
//
//		for (int i = 0; i < friends.size(); i++) {
//			BigInteger friendInt = new BigInteger(friends.get(i),
//					needNonces ? 16 : 10);
//			s[i] = friendInt.toByteArray();
//			Log.d(TAG, "adding " + friendInt.toString());
//		}
//
//		Rs = setIntersection.client_round_1(s, ts);
//		Log.d(TAG, "calculated Rs:" + Rs.toString());
//	}

//	protected void initOwnSetIntersectionInstance() {
//		setIntersection = new PrivateSetIntersectionCardinality();
//		setIntersection.setNumberOfClientFriends(friends.size());
//		Log.d(TAG, "set number of own friends to " + friends.size());
//	}

	// private int loadFriends() {
	// String r1Destination = config.getProperty("friendlist_r1");
	// String r2Destination = config.getProperty("friendlist_r2");
	//
	// List<String> r1List = Utilities.readFromFile(r1Destination, config);
	// List<String> r2List = Utilities.readFromFile(r2Destination, config);
	// for (String r1 : r1List) {
	// if (r1 != null && !r1.equals(""))
	// friends.add(new FacebookEntry(r1));
	// }
	// for (String r2 : r2List) {
	// if (r2 != null && !r2.equals(""))
	// friends.add(new FacebookEntry(r2));
	// }
	// return friends.size();
	// }

	// private int loadFriends(boolean noncesNeeded) {
	// if (!noncesNeeded)
	// return loadLocalFriends();
	// else
	// return loadLocalNonces();
	// }
	//
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
	//
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

	private void finalStateReaction() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.FINISHED_STATE,
				new Runnable() {
					@Override
					public void run() {
						halt(false);
					}
				});
	}

	private void analyzeKeepAlive() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new Runnable() {
					@Override
					public void run() {
						if (latestMessage instanceof KeepAlive) {
							analyzeKeepAliveData();
							latestMessage = null;
						}
					}
				});

		protocolState
				.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
						new Runnable() {
							@Override
							public void run() {
								if (latestMessage instanceof KeepAlive) {
									analyzeKeepAliveData();
									latestMessage = null;
								}
							}
						});

		protocolState.setStateAction(
				PeerCommunicationStateContext.VPN_REQUEST_ISSUED_STATE,
				new Runnable() {
					@Override
					public void run() {
						if (latestMessage instanceof KeepAlive) {
							analyzeKeepAliveData();
							latestMessage = null;
						}
					}
				});

	}

	protected void analyzeKeepAliveData() {
		// TODO check for data and timing
		synchronized (this) {
			lastKeepAlive = System.currentTimeMillis();
		}
	}

	private void startKeepAliveTimer() {
		// TODO start keepAlive Timer
		final int toleratedDelay = Integer.parseInt(config.getProperties()
				.getProperty(
				"keep_alive_tolerated_delay"));
		TimerTask keepAliveTask = new TimerTask() {
			@Override
			public void run() {
				synchronized (this) {
					long currentTime = System.currentTimeMillis();
					if (lastKeepAlive + toleratedDelay < currentTime) {
						Log.d(TAG,
								"Keep Alive did not show up recently, terminate connection.");
						backreference.setConnected(false);
						halt(false);
					} else {
						Log.d(TAG, "Keep Alive came in time, all fine");
					}
				}
			}
		};
		timer = new Timer();
		timer.scheduleAtFixedRate(
				keepAliveTask,
				new Date(),
				Integer.parseInt(config.getProperties().getProperty(
						"keep_alive_delay")) / 2 // shannon's law
		); //
	}

	private void analyzeCloseVPN() {
		protocolState.setStateAction(PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE, new Runnable() {
			@Override
			public void run() {
						Log.d(TAG, "about to check for CloseVPN");
				if(latestMessage instanceof CloseVPN){
							Log.d(TAG, "about to close the VPN");
					reportClosedVPN();
							backreference.setConnected(false);
							setCurrentStatus(Status.Offline);
							latestMessage = null;
				}
			}
		});
	}

	private void analyzeVPNGranted() {
		protocolState.setStateAction(
				PeerCommunicationStateContext.OPEN_VPN_CHANNEL_STATE,
				new Runnable() {
			@Override
			public void run() {

						Log.d(TAG, "about to check for VPNGranted");
						if (latestMessage instanceof VPNGranted) {
						// Why is this not evaluated?
							Log.d(TAG, "about to connect to VPN");
							backreference.setConnected(true);
							connectToVPN();
							latestMessage = null;
						}
			}
		});
	}

	private void analyzeVPNRefused() {
		protocolState
				.setStateAction(
						PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE,
						new Runnable() {
			@Override
			public void run() {
				if(latestMessage instanceof VPNRefused){
									Log.d(TAG, "reporting refused VPN");
									setCurrentStatus(Status.EstablishedManagementConnection);
					reportRefusedVPN();
									latestMessage = null;
				}
			}
		});
	}

	private void analyzeCloseManagementChannel() {
		protocolState.setStateAction(PeerCommunicationStateContext.MANAGEMENT_CONNECTION_ESTABLISHED_STATE, new Runnable() {
			@Override
			public void run() {
				if(latestMessage instanceof CloseManagementChannel){
									Log.d(TAG,
											"other side wants to close management connection");
									setCurrentStatus(Status.Offline);
					halt(false);
									latestMessage = null;

				}

			}
		});
	}

	private void analyzeHelloResponse() {
		protocolState.setStateAction(PeerCommunicationStateContext.HELLO_RESPONSE_SENT_STATE, new Runnable(){
			@Override
			public void run(){
				if(latestMessage instanceof HelloResponse){
							Log.d(TAG, "received hello response");
					HelloResponse msg = (HelloResponse) latestMessage;
							Capability capabilities = msg.getCapabilities();
					Quota quota = msg.getQuota();
							boolean needLiability = msg.getNeedLiability();


							if (isWorthConnecting(capabilities, quota)) {
								Log.d(TAG, "it is worth connecting");
								if (needLiability) {
									Log.d(TAG, "need Liability");
									if (canProvideLiability()) {
										Log.d(TAG,
												"can provide Liability, starting RRM and sending ACK");
										startResourceRequestManager();
										sendACK();
									} else {
										sendNack();
									}
								} else {
									sendACK();
								}
							} else {
								Log.d(TAG,
										"its not worth connecting, sending ConnectionNACK");
								sendNack();

					}
							latestMessage = null;
				}else {
					Log.d(TAG, "Expected HelloResponse, got something else:" + latestMessage.getClass().getCanonicalName());
				}
			}

			private void sendACK() {
				Log.d(TAG, "its worth connecting");
				ConnectionACK ack = ConnectionACK
						.newBuilder().build();
				sendMessage(ack);
				setCurrentStatus(Status.EstablishedManagementConnection);
			}

					private void sendNack() {
						ConnectionNACK nack = ConnectionNACK.newBuilder()
								.build();
						backreference
								.reportNotUsefulNeighbor(getRemoteMeshIP());
						backreference.setConnected(false);
						sendMessage(nack);
						setCurrentStatus(Status.Offline);
					}

			private boolean canProvideLiability() {
						File ipQueue = new File(
								"/system/lib/modules/ip_queue.ko");
				return ipQueue.exists();
			}
		});
	}

	private void startResourceRequestManager() {
		rrManager.start();
		rrManager.startRedirect();
	}

	private void stopResourceRequestManager() {
		rrManager.stopRR();
	}

	private void connectToVPN() {
		Log.d(TAG, "VPN connection granted, connecting...");
		try {
			Utilities.haltClientVPNDaemons(config);
			generateClientConfig();
			launchVPN();
			changeRouting();
			startKeepAliveTimer();
			reportToWorkerThread();
			setCurrentStatus(Status.Connected);
			config.setInterfaceAddressNetmask(true);
		} catch (Exception ex) {
			Log.d(TAG, "failed to generate client VPN, dying");
			halt(true);
		}

	}

	private void reportToWorkerThread() {
		backreference.setConnected(this);

	}

	private void changeRouting() {
		Log.d(TAG, "setting low level route entry");
		setDefaultGateway();
		setDNS();

	}

	/**
	 * There is no callback mechanism Im aware of that lets you know if an
	 * interface is up. We expect tun1 to be up, if not we iterate until it is.
	 * After max 8sec (10 * 0.8s) it should be.
	 */
	private void setDefaultGateway() {
		String remoteIP = Utilities.getRemoteIP(config);

		for (int i = 0; i < 30; i++) {
			if ((remoteIP = Utilities.getRemoteIP(config)) != null) {
				break;
			} else {

				try {
					Thread.sleep(850);
				} catch (InterruptedException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}

		String commandAdd = "route add default gw "
				+ remoteIP;
		String commandRemove = "route del default gw";
		Utilities.runCommand(new String[] {
				commandRemove, commandAdd
		});
	}

	private void setDNS() {
		// String dnsServer = "8.8.8.8";

		Log.d(TAG, "Setting WiFi DNS record");
		// android.provider.Settings.System.putString(config.getContentResolver(),
		// android.provider.Settings.System.WIFI_STATIC_DNS1,
		// dnsServer);
		// android.provider.Settings.System.putString(config.getContentResolver(),
		// android.provider.Settings.System.WIFI_STATIC_DNS2,
		// dnsServer);


		try {
			config.getApp().coretask.runRootCommand("setprop net.dns1 8.8.8.8");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

// for (int i = 0; i < 5; i++) {
		// String dnsCommand = "setprop dhcp." + interfaceName + ".dns"
		// + i
		// + " " + dnsServer;
		// try {
		// config.getApp().coretask.runRootCommand(dnsCommand);
		// } catch (IOException e) {
		// Log.e(TAG, e.getMessage(), e);
		// }
		// }
	}

	private void launchVPN() {
		File clientConfig = new File(config.getConfigurationDir(),
				getRemoteMeshIP() + ".ovpn");
		if(clientConfig.exists()){
			Log.d(TAG, "found client config, going to use it to launch OpenVPN");


			String args = "--config "
					+ new File(config.getConfigurationDir(),
							getRemoteMeshIP()
									+ ".ovpn")
							.getAbsolutePath() + " --daemon";
			String command = config.getProperties().getProperty("path_ovpn_bin") + " " + args;
			Log.d(TAG, "starting openvpn with:" + command);
			Log.d(TAG, Utilities.runCommand(new String[]{command}));
			Log.d(TAG, "OpenVPN client started hopefully");

		}else{
			Log.d(TAG, "could not find the client config, dying");
			halt(true);
		}

	}

	private void generateClientConfig() {
		try {
			Log.d(TAG, "generating VPN connection");
			String skeleton = Utilities.readSkeleton(config.getAssetManager(), config.getProperties().getProperty("vpn_client_skeleton"));
			Log.d(TAG, "read client VPN skeleton successfully:");
			Log.d(TAG, skeleton);

			skeleton = skeleton.replace("<REMOTE_IP>",
					getRemoteMeshIP());
			skeleton = skeleton.replace("<REMOTE_CN>", "/" // the slash is
															// necessary because
															// OpenVPN adds it
															// when extracting
															// the CN from the
															// cert
					+ getPeerCommonName());
			skeleton = skeleton.replace("<CA_CERT>", new File(config.getCertificateDir(), config.getProperties().getProperty("ca_cert_dest_loc")).getAbsolutePath());
			skeleton = skeleton.replace("<USER_KEY>", new File(config.getCertificateDir(), config.getProperties().getProperty("private_key_loc")).getAbsolutePath());
			skeleton = skeleton.replace("<USER_CERT>", new File(config.getCertificateDir(), config.getProperties().getProperty("token_loc")).getAbsolutePath());

			if (Utilities.isExternalStorageWriteable()) {
				File status = new File(new File(config.getProperties()
						.getProperty("sdcard_loc")),
						config.getProperties().getProperty("ovpn_client_status_file"));

				File log = new File(new File(config.getProperties()
						.getProperty("sdcard_loc")),
						config.getProperties().getProperty("ovpn_client_log_file"));
				skeleton = skeleton.replaceAll("<LOG>", log.getAbsolutePath());
				skeleton = skeleton.replaceAll("<STATUS>",
						status.getAbsolutePath());
			} else {
				Log.d(TAG,
						"There is no external storage available to write to. Writing to " + config.getProperties()
										.getProperty("local_temp"));
				File status = new File(new File(config.getProperties()
						.getProperty("local_temp")),
						config.getProperties().getProperty("ovpn_client_status_file"));

				File log = new File(new File(config.getProperties()
						.getProperty("local_temp")),
						config.getProperties().getProperty("ovpn_client_log_file"));
				skeleton = skeleton.replaceAll("<LOG>", log.getAbsolutePath());
				skeleton = skeleton.replaceAll("<STATUS>",
						status.getAbsolutePath());

			}
			Log.d(TAG, skeleton);

			FileOutputStream fos = new FileOutputStream(new File(
					config.getConfigurationDir(), getRemoteMeshIP()
							+ ".ovpn"));
			fos.write(skeleton.getBytes());
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
			Log.d(TAG, "issues with reading an asset file: client vpn skeleton, dying");
			halt(true);
		}
	}

	public String getRemoteMeshIP() {
		return socket.getRemoteSocketAddress().toString().split("/")[0];
	}

	private String getPeerCommonName() throws SSLPeerUnverifiedException {
		//we only issue one certificate to authenticate
		X509Certificate cert = (X509Certificate) socket.getSession().getPeerCertificates()[0];
		return cert.getSubjectDN().getName();
	}



	// private String getOwnTun1IP() {
	// try {
	// String ifconfig = config.getApp().coretask.runCommandForOutput(
	// true,
	// "/system/bin/ifconfig");
	// String networkAndMask[] = Utilities.findIPandNetmask(ifconfig,
	// "tun1");
	// return networkAndMask[0];
	//
	// } catch (Exception e) {
	//
	// }
	// return null;
	// // return socket.getInetAddress().getHostAddress();
	// }

	/**
	 * We do not need the management connection anymore if it cannot provide the
	 * VPN due to internal reasons.
	 */
	private void reportRefusedVPN(){
		Log.d(TAG, "VPN connection refused, dropping connection");
		closeManagementChannel();
	}

	/**
	 * Stops the client side of the VPN connection if necessary and disconnects
	 * the streams.
	 */
	private void closeManagementChannel() {
//		sendMessage(CloseManagementChannel.newBuilder().build());

		Utilities.haltClientVPNDaemons(config);
		halt(true);
	}

	private void reportClosedVPN() {
		Log.d(TAG, "VPN connection was closed from other side");
		backreference.setConnected(false);
		closeManagementChannel();
	}

	private boolean isWorthConnecting(Capability capabilities,
			Quota quota) {
		// TODO add logic here

		List<CapabilityItem> receivedCapas = capabilities.getCapabilityList();

		for (CapabilityItem item : receivedCapas) {
			if (item == CapabilityItem.INTERNET) {
				Log.d(TAG, "found offered Internet tethering capability");
				return true;
			}
		}
		// if (receivedCapas.contains(CapabilityItem.INTERNET)) {
		// Log.d(TAG, "found offered Internet tethering capability");
		// return true;
		// }
		Log.d(TAG, "did not find offered Internet tethering capability");
		return false;
	}



	@Override
	public void run(){
		Log.d(TAG, "entered ManagementClientHandler's run()");
		Hello helloMsg = buildHelloMessage();
		sendMessage(helloMsg);
		setCurrentStatus(Status.SearchingManagementConnections);
		Log.d(TAG, "sent Hello");
		while(running){
			receiveMessage();
		}
		halt(false);
	}

	private void receiveMessage() {
		try {
			// byte[] temp = new byte[4096]; // should be more than enough
			// reader.read(temp);
			//
			// ByteArrayInputStream bis = new ByteArrayInputStream(temp);
			// ObjectInputStream ois = new ObjectInputStream(bis);
			// latestMessage = ois.readObject();
			// ois.close();
			// bis.close();
			// Log.d(TAG, "received object is of class:"
			// + latestMessage.getClass().getCanonicalName());

			latestMessage = reader.readObject();

			Log.d(TAG, "current state is:"
					+ protocolState.getState().toString());
			Log.d(TAG, "received "
					+ latestMessage.getClass().getCanonicalName());

			if(latestMessage.getClass().getCanonicalName().equals("de.cased.mobilecloud.common.PeerCommunicationProtocol.PrivateSetIntersectionNACK")){
				Log.d(TAG, "received the NACK");
			}

			protocolState.receiveEvent(latestMessage.getClass().getCanonicalName());
			Log.d(TAG, "current state is:"
					+ protocolState.getState().toString());
		} catch (Exception e) {
			Log.d(TAG, "something went wrong, I quit");
			e.printStackTrace();
			halt(false);
			if (protocolState.getState() == "vpn") {
				backreference.setConnected(false);
			}
		}

	}

	public synchronized void sendMessage(Message msg) {
		try {
			writer.writeObject(msg);
			writer.flush();
			protocolState.receiveEvent(msg.getClass().getCanonicalName());
		} catch (IOException e) {
			e.printStackTrace();
			halt(false);
		}

	}

	private Hello buildHelloMessage() {
		Capability.Builder req = Capability.newBuilder();
		req.addAllCapability(requestedCapabilities);
		Capability request = req.build();
		Hello helloMsg = Hello.newBuilder().setCapabilities(request).build();
		return helloMsg;
	}


	public synchronized SSLSocket getSocket() {
		return socket;
	}

	public void setSocket(SSLSocket socket) {
		this.socket = socket;
	}

	public synchronized String getTargetIP(){
		if(socket != null){
			return Utilities.getRemoteIP(config);
		}else
			return null;
	}

	public synchronized void setBackReference(PeerClientWorker peerClientWorker) {

	}

	public synchronized void halt(boolean sendBackCloseManagementChannel) {
		if(sendBackCloseManagementChannel){
			sendMessage(CloseManagementChannel.newBuilder().build());
		}
		stopResourceRequestManager();
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}

		if (writer != null && reader != null && socket != null) {
			try{
				writer.close();
				reader.close();
				socket.close();
				running = false;
				Utilities.haltClientVPNDaemons(config);
			}catch (Exception e){
				//dont care
			}finally{
				deleteClientConfig();
				Utilities.haltClientVPNDaemons(config);
				setCurrentStatus(Status.Offline);
				backreference.deleteHandler(this);
			}
		}
	}



	private boolean deleteClientConfig() {
		return new File(config.getConfigurationDir(),
				Utilities.getRemoteIP(config) + ".ovpn").delete();

	}

	public RouteEntry getRouteInfo() {
		return routeInfo;
	}

	public void requestVPN() {
		Log.d(TAG, "requesting VPN");
		setCurrentStatus(Status.Connecting);
		// X509Certificate token = config.getToken();
		VPNRequest.Builder vpnReqBuilder = VPNRequest.newBuilder();
		// try {
		// ByteString encodedToken = ByteString.copyFrom(token.getEncoded());
		// vpnReqBuilder.setCertificate(encodedToken);
		// } catch (CertificateEncodingException e) {
		// Log.e(TAG, e.getMessage(), e);
		// }
		sendMessage(vpnReqBuilder.build());

	}

	public Status getCurrentStatus() {
		return currentStatus;
	}

	private void setCurrentStatus(Status status) {
		currentStatus = status;
		backreference.reportManagementStatusChange();
	}

}
