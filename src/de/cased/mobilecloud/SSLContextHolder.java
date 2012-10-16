package de.cased.mobilecloud;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextHolder {

	private RuntimeConfiguration config = RuntimeConfiguration.getInstance();
	private KeyManagerFactory keyManagerFactory;// = null; // what are my keys
	private TrustManagerFactory trustManagerFactory;// = null; // who do I trust

	private SSLContext context = null;

	private enum State {
		UNINITIALIZED, REGSTATE, FINALSTATE
	};

	private State currentState = State.UNINITIALIZED;

	public SSLContextHolder(){
	}

	public void createInitialManagerFactories() throws KeyStoreException,
		NoSuchAlgorithmException, CertificateException,
		IOException, UnrecoverableKeyException {

		trustManagerFactory = buildTrustManagerFactory();
		keyManagerFactory = buildKeyManagerFactory(true);
	}

	private KeyStore loadKeyStorePEM(boolean registerContext)
			throws NoSuchAlgorithmException, CertificateException, IOException,
			KeyStoreException
 {
		KeyStore localKeyStore = KeyStore.getInstance("BKS");
		localKeyStore.load(null, null);

		if (registerContext) {
			return localKeyStore;
		} else {
			X509Certificate token = config.getToken();
			X509Certificate signCert = Utilities.loadCertificate(new File(
					config.getCertificateDir(), config.getProperties()
							.getProperty("ca_cert_dest_loc")));

			PrivateKey privKey = config.getPrivKey();
			localKeyStore.setCertificateEntry("1", token);
			localKeyStore.setKeyEntry("1", privKey, "".toCharArray(),
					new X509Certificate[] { token, signCert });

			return localKeyStore;
		}
	}

	private KeyStore loadTrustStorePEM() throws KeyStoreException,
			NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore localTrustStore = KeyStore.getInstance("BKS");
		localTrustStore.load(null, null);
		X509Certificate regServerCert = Utilities.loadCertificate(new File(config
				.getCertificateDir(), config.getProperties().getProperty(
				"server_cert_dest_loc")));

		X509Certificate signCert = Utilities.loadCertificate(new File(config
				.getCertificateDir(), config.getProperties().getProperty(
				"ca_cert_dest_loc")));

		localTrustStore.setCertificateEntry("RegServerCert", regServerCert);
		localTrustStore.setCertificateEntry("ClientCertRootOfTrust", signCert);

		return localTrustStore;
	}

	private TrustManagerFactory buildTrustManagerFactory()
			throws KeyStoreException,
	IOException, NoSuchAlgorithmException, CertificateException {

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
		tmf.init(loadTrustStorePEM());
		return tmf;
	}

	private KeyManagerFactory buildKeyManagerFactory(boolean regContext)
			throws NoSuchAlgorithmException, UnrecoverableKeyException,
			KeyStoreException, CertificateException, IOException {
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
		kmf.init(loadKeyStorePEM(regContext), null);
		return kmf;
	}

	public SSLContext getSSLContext(boolean registrationContext)
			throws UnrecoverableKeyException, KeyStoreException,
			NoSuchAlgorithmException, CertificateException, IOException,
			KeyManagementException {

		switch(currentState){
		case UNINITIALIZED:
			if (registrationContext) {
				createInitialManagerFactories();
				buildContext();
				currentState = State.REGSTATE;
				return context;
			} else {
				buildFinalFactories();
				buildContext();
				currentState = State.FINALSTATE;
				return context;
			}
		case REGSTATE:
			if (registrationContext) {
				return context;
			} else {
				buildFinalFactories();
				buildContext();
				currentState = State.FINALSTATE;
				return context;
			}
		case FINALSTATE:
			return context;
		}
		return context;
	}

	public void forceRebuildContext() throws UnrecoverableKeyException,
			NoSuchAlgorithmException, KeyStoreException, CertificateException,
			IOException, KeyManagementException {
		buildFinalFactories();
		buildContext();
		currentState = State.FINALSTATE;
	}

	private void buildContext() throws NoSuchAlgorithmException,
			KeyManagementException {
		context = SSLContext.getInstance("TLS");
		context.init(keyManagerFactory.getKeyManagers(),
				trustManagerFactory.getTrustManagers(), null);
	}

	private void buildFinalFactories() throws NoSuchAlgorithmException,
			UnrecoverableKeyException, KeyStoreException, CertificateException,
			IOException {
		keyManagerFactory = buildKeyManagerFactory(false);
		trustManagerFactory = buildTrustManagerFactory();
	}
}
