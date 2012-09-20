package de.cased.mobilecloud;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import android.app.ProgressDialog;
import android.os.Handler;
import android.util.Log;

import com.google.protobuf.ByteString;

import de.cased.mobilecloud.common.RegistrationProtocol.CertificateRequest;
import de.cased.mobilecloud.common.RegistrationProtocol.CertificateResponse;
import de.cased.mobilecloud.common.RegistrationProtocol.DHParams;
import de.cased.mobilecloud.common.RegistrationStateContext;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x500.X500NameBuilder;
import ext.org.bouncycastle.asn1.x500.style.BCStyle;
import ext.org.bouncycastle.jce.provider.BouncyCastleProvider;
import ext.org.bouncycastle.operator.OperatorCreationException;
import ext.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import ext.org.bouncycastle.pkcs.PKCS10CertificationRequest;
import ext.org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import ext.org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

public class RegistrationWorker extends Thread {

	private static String TAG = "RegistrationWorker";
	private RuntimeConfiguration config;
	private ProgressDialog dialog;
	private Handler handler;
	private HashMap messageData = new HashMap();
	// private RegistrationCallback callback;
	private RegistrationStateContext protocolState;
	private Object latestMessage;

	private ObjectOutputStream oos;
	private ObjectInputStream ois;
	private SSLSocket socket;



	public RegistrationWorker(ProgressDialog dialog, Handler handler) {
		config = RuntimeConfiguration.getInstance();
		// this.callback = callback;
		this.dialog = dialog;
		this.handler = handler;
		Security.addProvider(new BouncyCastleProvider());
		buildProtocolReactions();
	}

	private void buildProtocolReactions() {
		protocolState = new RegistrationStateContext();
		onCertReceived();
		onDHReceived();
	}

	private void onDHReceived() {
		protocolState.setStateAction(RegistrationStateContext.FINISH,
				new Runnable() {

			@Override
			public void run() {
						if (latestMessage instanceof DHParams) {
							DHParams params = (DHParams) latestMessage;
							saveDHParams(params);
				}

			}

		});
	}

	private void onCertReceived() {
		protocolState.setStateAction(RegistrationStateContext.CERTIFICATE_ISSUED, new Runnable() {

			@Override
			public void run() {
				if(latestMessage instanceof CertificateResponse){
					CertificateResponse cert = (CertificateResponse) latestMessage;
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
		SSLSocket socket = (SSLSocket) socketFactory.createSocket(
				config.getProperty("registration_server"),
				Integer.parseInt(config.getProperty("registration_port")));
    	oos = new ObjectOutputStream(socket.getOutputStream());
		ois = new ObjectInputStream(socket.getInputStream());
    	Log.d(TAG, "Connection established");
    	return socket;
	}

	// Clean up if the thread is cancelled
	public void cancel() {
		messageData.put("status", false);
		handler.obtainMessage(0x2a, messageData).sendToTarget();
		if (dialog != null && dialog.isShowing())
			dialog.dismiss();
	}

	@Override
	public void run() {

		// config.getHostActivity().runOnUiThread(new Runnable() {
		// @Override
		// public void run() {
		// dialog = ProgressDialog.show(config.getHostActivity(),
		// "Performing Registration",
		// "Please wait...", true);
		// }
		// });

		try {
			byte[] request = getPKCS10CertReq();
			Log.d(TAG, "got PKCS10 request");
			socket = connectToServer();
			registerWithServer(request);
			messageData.put("status", true);
			handler.obtainMessage(0x2a, messageData).sendToTarget();
		} catch (Exception e) {
			e.printStackTrace();
			messageData.put("status", false);
			handler.obtainMessage(0x2a, messageData).sendToTarget();
		}

		if (dialog != null && dialog.isShowing()) {
			   dialog.dismiss();
		}
	}

	private void receiveMessage() {
		try {
			latestMessage = ois.readObject();
			protocolState.receiveEvent(latestMessage.getClass().getCanonicalName());
		} catch (Exception e) {
			e.printStackTrace();
			halt();
		}

	}

	private void sendMessage(Object msg) {
		try {
			oos.writeObject(msg);
			protocolState.receiveEvent(msg.getClass().getCanonicalName());
		} catch (IOException e) {
			e.printStackTrace();
			halt();
		}

	}

	private void halt() {
		if(oos != null && ois != null && socket != null){
			try{
				oos.close();
				ois.close();
				socket.close();
			}catch (Exception e){
				//dont care
			}
		}
	}

	private void registerWithServer(byte[] request) throws Exception{
    	CertificateRequest requestObject = buildRequestObject(request);
		sendMessage(requestObject);

		receiveMessage();// certificate
		receiveMessage();// dhparams

		Log.d(TAG, "received and saved Certificate, hopefully");
		Utilities.closeInputStream(ois);
		Utilities.closeOutputStream(oos);
    }

	private CertificateRequest buildRequestObject(byte[] request) {
		CertificateRequest.Builder reqBuilder = CertificateRequest.newBuilder();
		reqBuilder.setPkcs10(ByteString.copyFrom(request));
    	CertificateRequest requestObject = reqBuilder.build();
		return requestObject;
	}

	private byte[] getPKCS10CertReq() throws NoSuchAlgorithmException,
		NoSuchProviderException, InvalidKeyException, SignatureException, IllegalArgumentException,
		IllegalAccessException, NoSuchFieldException, IOException, OperatorCreationException, InvalidAlgorithmParameterException {


		Log.d(TAG, "attempting to build a PKCS10");

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
		Log.d(TAG, "got BC KeyGenerator instance");
		kpg.initialize(keySize);

		KeyPair kp = kpg.genKeyPair();
		Log.d(TAG, "generated keys");
		config.setPrivKey(kp.getPrivate());



		X500NameBuilder x500NameBld = new X500NameBuilder(BCStyle.INSTANCE);

		x500NameBld.addRDN(BCStyle.C, "DE");
		x500NameBld.addRDN(BCStyle.O, "CASED");
		x500NameBld.addRDN(BCStyle.L, "Darmstadt");
		x500NameBld.addRDN(BCStyle.ST, "Hessen");
		x500NameBld.addRDN(BCStyle.EmailAddress, "stas.stelle@gmail.com");

		X500Name    subject = x500NameBld.build();
		Log.d(TAG, "subject built");

		PKCS10CertificationRequestBuilder requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, kp.getPublic());

		PKCS10CertificationRequest req1 = requestBuilder.build(new JcaContentSignerBuilder(sigName).setProvider(provider).build(kp.getPrivate()));
		Log.d(TAG, "PKCS10 built and ready to encode");
		return req1.getEncoded();

	}
}
