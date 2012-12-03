package de.cased.mobilecloud;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.google.protobuf.ByteString;

import de.cased.mobilecloud.common.AbstractStateContext;
import de.cased.mobilecloud.common.RegistrationProtocol.CertificateRequest;
import de.cased.mobilecloud.common.RegistrationProtocol.CertificateResponse;
import de.cased.mobilecloud.common.RegistrationProtocol.DHParams;
import de.cased.mobilecloud.common.RegistrationProtocol.Friendlist;
import de.cased.mobilecloud.common.RegistrationStateContext;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x500.X500NameBuilder;
import ext.org.bouncycastle.asn1.x500.style.BCStyle;
import ext.org.bouncycastle.operator.OperatorCreationException;
import ext.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import ext.org.bouncycastle.pkcs.PKCS10CertificationRequest;
import ext.org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import ext.org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

public class RegistrationWorker extends AbstractServerWorker {

	private static String TAG = "RegistrationWorker";
	// private RuntimeConfiguration config;
	// private ProgressDialog dialog;
	// private Handler handler;
	private HashMap messageData = new HashMap();
	private Context context;
	// private RegistrationCallback callback;
	// private RegistrationStateContext protocolState;
	// private Object latestMessage;

	// private ObjectOutputStream oos;
	// private ObjectInputStream ois;
	// private SSLSocket socket;



	public RegistrationWorker(ProgressDialog dialog, Handler handler,
			AbstractStateContext stateContext, Context context) {
		// config = RuntimeConfiguration.getInstance();
		// // this.callback = callback;
		// this.dialog = dialog;
		// this.handler = handler;
		// Security.addProvider(new BouncyCastleProvider());
		// buildProtocolReactions();
		super(dialog, handler, stateContext);
		this.context = context;
	}

	@Override
	protected void buildProtocolReactions() {
		onCertReceived();
		onDHReceived();
		onFriendlistReceive();
	}

	private void onFriendlistReceive() {

		protocolState.setStateAction(RegistrationStateContext.FINISH,
				new Runnable() {

					@Override
					public void run() {
						if (getLatestMessage() instanceof Friendlist) {
							System.out.println("received and saved friendlist");
							Friendlist params = (Friendlist) getLatestMessage();
							List<String> r1Friends = params.getR1SubjectList();
							List<String> r2Friends = params.getR2SubjectList();

							String r1Destination = config
									.getProperty("friendlist_r1");
							String r2Destination = config
									.getProperty("friendlist_r2");

							Utilities.writeToFile(r1Friends, r1Destination,
									config);
							Utilities.writeToFile(r2Friends, r2Destination,
									config);
							halt();
						}

					}

					// private void writeToFile(List<String> r1Friends,
					// String r1Destination) {
					// FileOutputStream fos;
					// try {
					// fos = config.getApp().openFileOutput(r1Destination,
					// Context.MODE_PRIVATE);
					// for (String r1Friend : r1Friends) {
					// fos.write(r1Friend.getBytes());
					// fos.write('\n');
					// }
					// fos.close();
					// } catch (FileNotFoundException e) {
					// Log.e(TAG, e.getMessage(), e);
					// } catch (IOException e) {
					// Log.e(TAG, e.getMessage(), e);
					// }
					// }

				});
	}



	private void onDHReceived() {
		protocolState.setStateAction(RegistrationStateContext.DHPARAMS_ISSUED,
				new Runnable() {

			@Override
			public void run() {
						if (getLatestMessage() instanceof DHParams) {
							DHParams params = (DHParams) getLatestMessage();
							saveDHParams(params);
				}

			}

		});
	}

	private void onCertReceived() {
		protocolState.setStateAction(RegistrationStateContext.CERTIFICATE_ISSUED, new Runnable() {

			@Override
			public void run() {
						if (getLatestMessage() instanceof CertificateResponse) {
							CertificateResponse cert = (CertificateResponse) getLatestMessage();
					saveCert(cert);
				}
			}
		});

	}



	private void saveCert(CertificateResponse cert){
		try{
			ByteArrayInputStream bis = new ByteArrayInputStream(cert.getX509()
					.toByteArray());
			CertificateFactory fac = CertificateFactory.getInstance("X.509");
			config.setToken((X509Certificate)fac.generateCertificate(bis));
			bis.close();
			Utilities.writePemFile(config.getProperties().getProperty("token_loc"), config.getToken(), config.getCertificateDir());
			Utilities.writePemFile(config.getProperties().getProperty("private_key_loc"), config.getPrivKey(), config.getCertificateDir());
			config.forceContextRebuild();
		}catch(Exception e){
			e.printStackTrace();
			halt();
		}
	}

	private void saveDHParams(DHParams params) {
		try {
			FileWriter writer = new FileWriter(new File(
					config.getCertificateDir(),
					config.getProperties().getProperty("dh_loc")));
			writer.write(params.getParams());
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
			halt();
		}
	}

	private SSLSocket connectToServer() throws Exception{
		Log.d(TAG,
				"connecting to server: "
						+ config.getProperty("registration_server") + ":"
						+ config.getProperty("registration_port"));
    	SSLContext ssl_context = config.createSSLContext(true);
    	SSLSocketFactory socketFactory = ssl_context.getSocketFactory();
		final SSLSocket socket = (SSLSocket) socketFactory.createSocket(
				config.getProperty("registration_server"),
				Integer.parseInt(config.getProperty("registration_port")));
		socket.setNeedClientAuth(false);
		socket.setUseClientMode(true);
		socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {

			@Override
			public void handshakeCompleted(HandshakeCompletedEvent event) {
				Log.d(TAG,
						"Registration Handshake complete >>>>>>>>>>>>>>>>>>>>>>>>>>>");
				try {
					Certificate[] chain = event.getPeerCertificates();
					for (Certificate cert : chain) {
						X509Certificate xcert = (X509Certificate) cert;
						Log.d(TAG, "subject is "
								+ xcert.getSubjectX500Principal().getName());
					}

					Log.d(TAG, "received cert chain!!1!11!1!1one");
				} catch (SSLPeerUnverifiedException e) {
					Log.e(TAG, e.getMessage(), e);
				}


				try {
					oos = new ObjectOutputStream(socket.getOutputStream());
					ois = new ObjectInputStream(socket.getInputStream());



				} catch (StreamCorruptedException e) {
					Log.e(TAG, e.getMessage(), e);
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
				Log.d(TAG, "Connection established");

			}


		});
		socket.startHandshake();

    	return socket;
	}

	private void startProtocol() throws NoSuchAlgorithmException,
			NoSuchProviderException, InvalidKeyException, SignatureException,
			IllegalAccessException, NoSuchFieldException, IOException,
			OperatorCreationException, InvalidAlgorithmParameterException,
			Exception {

		byte[] request = getPKCS10CertReq();
		registerWithServer(request);
		messageData.put("status", true);
		handler.obtainMessage(0x2a, messageData).sendToTarget();
	}

	// Clean up if the thread is cancelled
	@Override
	public void cancel() {
		messageData.put("status", false);
		handler.obtainMessage(0x2a, messageData).sendToTarget();
		if (dialog != null && dialog.isShowing())
			dialog.dismiss();
	}

	@Override
	public void run() {

		try {
			RegistrationNoncePoster poster = new RegistrationNoncePoster(
					config.getProperty("appid"),
					config.getApp());
			String nonce = poster.postNonce();
			persistNonce(nonce);
			socket = connectToServer();
			startProtocol();

		} catch (Exception e) {
			e.printStackTrace();
			messageData.put("status", false);
			handler.obtainMessage(0x2a, messageData).sendToTarget();
		}

		if (dialog != null && dialog.isShowing()) {
			   dialog.dismiss();
		}
	}

	private void persistNonce(String nonce) {
		try {
			String meNonceLocation = config.getProperty("menonce");
			BufferedOutputStream bos = new BufferedOutputStream(
					context.openFileOutput(meNonceLocation,
							Context.MODE_PRIVATE));
			bos.write(nonce.getBytes());
			bos.write('\n');
			bos.flush();
			bos.close();
			Log.d(TAG, "persisted me to" + meNonceLocation);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void registerWithServer(byte[] request) throws Exception{
    	CertificateRequest requestObject = buildRequestObject(request);
		sendMessage(requestObject);

		while (isRunning()) {
			receiveMessage();
		}
		Utilities.closeInputStream(ois);
		Utilities.closeOutputStream(oos);
    }

	private CertificateRequest buildRequestObject(byte[] request) {
		String fbToken = config.getFbAccessToken();
		CertificateRequest.Builder reqBuilder = CertificateRequest.newBuilder();
		reqBuilder.setPkcs10(ByteString.copyFrom(request));
		reqBuilder.setFbaccesstoken(fbToken == null ? "" : fbToken);
		reqBuilder.setFbexpiredate(config.getFbAccessExpire());
    	CertificateRequest requestObject = reqBuilder.build();
		return requestObject;
	}

	private byte[] getPKCS10CertReq() throws NoSuchAlgorithmException,
		NoSuchProviderException, InvalidKeyException, SignatureException, IllegalArgumentException,
		IllegalAccessException, NoSuchFieldException, IOException, OperatorCreationException, InvalidAlgorithmParameterException {

		String keyName = "RSA";
		int keySize = 1024;
		String sigName = "SHA1withRSA";
		String provider = "BC";

		//ECC---------------------------------
		//ECGenParameterSpec     ecGenSpec = new ECGenParameterSpec("prime192v1");
		//
		//KeyPairGenerator    g = KeyPairGenerator.getInstance("ECDSA", "BC");
		//
		//g.initialize(ecGenSpec, new SecureRandom());
		//
		//KeyPair pair = g.generateKeyPair();
		//System.out.println("private key generated with: " + pair.getPrivate().getAlgorithm());
		//ECC--------------------------------------


		KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyName, "BC");
		kpg.initialize(keySize);

		KeyPair kp = kpg.genKeyPair();
		config.setPrivKey(kp.getPrivate());



		X500NameBuilder x500NameBld = new X500NameBuilder(BCStyle.INSTANCE);

		// x500NameBld.addRDN(BCStyle.C, "DE");
		// x500NameBld.addRDN(BCStyle.O, "CASED");
		// x500NameBld.addRDN(BCStyle.L, "Darmstadt");
		// x500NameBld.addRDN(BCStyle.ST, "Hessen");
		// x500NameBld.addRDN(BCStyle.EmailAddress, "stas.stelle@gmail.com");

		X500Name    subject = x500NameBld.build();
		PKCS10CertificationRequestBuilder requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());
		PKCS10CertificationRequest req1 = requestBuilder.build(new JcaContentSignerBuilder(sigName).setProvider(provider).build(kp.getPrivate()));
		return req1.getEncoded();

	}
}
