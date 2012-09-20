package de.cased.mobilecloud;

import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.util.Log;

public class SecurityHelper {

	private RuntimeConfiguration config = RuntimeConfiguration.getInstance();
	private static String TAG = "SecurityHelper";
	private KeyManager keyManagerCache = null;
	private TrustManager trustManagerCache = null;

	public SecurityHelper(){
	}

	public void createInitialManagerFactories() throws KeyStoreException,
		NoSuchAlgorithmException, CertificateException,
		IOException, UnrecoverableKeyException {

		trustManagerCache = buildTrustManagerFactory();
		keyManagerCache = buildEmptyKeyManagerFactory();
	}



	private KeyManager buildEmptyKeyManagerFactory()
			throws KeyStoreException, NoSuchAlgorithmException, IOException,
			CertificateException, UnrecoverableKeyException {
		            KeyStore keyStore = KeyStore.getInstance("BKS");
		            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		            keyStore.load(null, null);
		            keyManagerFactory.init(keyStore, "".toCharArray());
		return keyManagerFactory.getKeyManagers()[0];
	}

	private TrustManager buildTrustManagerFactory() throws KeyStoreException,
	IOException, NoSuchAlgorithmException, CertificateException {
		KeyStore trustStore = KeyStore.getInstance("BKS");
		trustStore.load(null,null);
		// X509Certificate serverCert = Utilities.loadCertificate(new
		// File(config
		// .getCertificateDir(), config.getProperties().getProperty(
		// "server_cert_dest_loc")));
		final X509Certificate signCert = Utilities.loadCertificate(new File(
				config
				.getCertificateDir(), config.getProperties().getProperty(
				"ca_cert_dest_loc")));

		// trustStore.setCertificateEntry("server cert", serverCert);
		trustStore.setCertificateEntry("signature cert", signCert);
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		// TODO make this smarter
		TrustManager tm = new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[] {
					signCert
				};
			}
		};


		// return trustManagerFactory.getTrustManagers()[0];
		return tm;
	}

	public SSLContext createSSLContext(boolean registerContext){
        SSLContext ssl_cont = null;
        try {
        	if(registerContext){
        		createInitialManagerFactories();
        	}else{
        		if(trustManagerCache == null){
        			trustManagerCache = buildTrustManagerFactory();
        		}
        		createFinalKeyManagerFactory();
        	}
            ssl_cont = SSLContext.getInstance("TLS");
			ssl_cont.init(new KeyManager[] {
				keyManagerCache
			}, new TrustManager[] {
				trustManagerCache
			}, null);
        } catch (Exception e) {
        	System.out.println("ERROR: " + e.getMessage());
        	e.printStackTrace();
        }
        return ssl_cont;
    }

	public void createFinalKeyManagerFactory() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException{

		KeyStore keyStore = KeyStore.getInstance("BKS");
	    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	    keyStore.load(null, null);

		X509Certificate token = config.getToken();
		X509Certificate signCert = Utilities.loadCertificate(new File(config
				.getCertificateDir(), config.getProperties().getProperty(
				"ca_cert_dest_loc")));

		if (token == null || signCert == null) {
			throw new IOException(
					"Couldnt find either token or the signing cert.");
		}

		keyStore.setKeyEntry("identification", config.getPrivKey(),
				"".toCharArray(), new X509Certificate[] {
						token,
						signCert
				});
		Log.d(TAG, "added token and signCert to key store");
	    keyManagerFactory.init(keyStore, "".toCharArray());
		keyManagerCache = keyManagerFactory.getKeyManagers()[0];
	}
}
