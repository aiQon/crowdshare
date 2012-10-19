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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import org.servalproject.ServalBatPhoneApplication;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

public class RuntimeConfiguration {

	private static String TAG = "RuntimeConfiguration";
	private static RuntimeConfiguration singleton = null;

	private Properties properties = new Properties();
	private X509Certificate token = null;
	private PrivateKey privKey = null;
	private KeyStore trustStore = null;
	private KeyStore keyStore = null;
	// private SSLContext sslContex = null;

	private File certificateDir = null;
	private File configurationDir = null;

	private SSLContextHolder security = null;

	private AssetManager assetManager = null;
	private ContentResolver contentResolver;

	private String ovpnStatusPath;

	// private boolean securityRequested = false;
	// private boolean enableMobileCloud = false;
	private Activity hostActivity = null;
	private ServalBatPhoneApplication app;

	private boolean isReadyToCloud = false;

	private SharedPreferences preferences;

	private String fbAccessToken;
	private long fbAccessExpire;

	String[] ipAndNetmaskEth0;

	public String[] getIpAndNetmaskEth0() {
		return ipAndNetmaskEth0;
	}

	String[] ipAndNetmaskTun0;
	String[] ipAndNetmaskTun1;

	public String[] getIpAndNetmaskTun0() {
		return ipAndNetmaskTun0;
	}

	public String[] getIpAndNetmaskTun1() {
		return ipAndNetmaskTun1;
	}

	/**
	 * place this somewhere else maybe
	 */
	public void startMobileCloudServices() {
		// need to find the IP address of the WiFi device. On desire and nexus
		// one its 'eth0'. Need a cleaner way to get it.
		// maybe:
		// http://www.gubatron.com/blog/2010/09/19/android-programming-how-to-obtain-the-wifis-corresponding-networkinterface/

		Log.d(TAG, "startMobileCloudServices");
		if (!preferences.getBoolean(SecuritySetupActivity.ENABLE_MOBILE_CLOUD,
				false)) {
			return; // nothing to do here
		}

		if (privKey == null || token == null) {
			Log.d(TAG,
					"not starting MobileCloud services because there are no private and/or private key files");
			return;
		}

		HashMap<String, ArrayList<String>> addresses = Utilities
				.getLocalIpAddresses();
		if (addresses.get("eth0") != null) {
			String identifier = Utilities.getIdentifier(addresses);
			Log.d(TAG, "identifier is " + identifier);
			Utilities.deleteVpnConfig(this);
			if (Utilities.buildOpenVpnConfig(this, identifier)) {
				if (Utilities.launchOpenVPN(this)) {
					Log.d(TAG, "successfully started OpenVPN");

				} else {
					Log.d(TAG, "failed to start OpenVPN server");

				}
				hostActivity.startService(
						new Intent(hostActivity, PeerServerCommunicator.class));

				flushRules();
				// cleanMasquerade();
				// blockAllForwarding();
				// blockIncomingTraffic();
				// allowServalServices();
				// allowRmnet0Traffic();
				blockOVpnAccess();

				// masqueradeTraffic(identifier);
			} else {
				Log.d(TAG, "failed to build the OpenVPN server config");
			}
		} else {
			Log.d(TAG, "eth0 is not available, is the routing daemon running?");
		}

	}

	private void blockOVpnAccess() {
		String allowCommand = "iptables -A INPUT -p udp --destination-port 1194 -m state --state NEW,ESTABLISHED -j DROP";
		try {
			getApp().coretask.runRootCommand(allowCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void allowOVpnAccessForIP(String ip) {
		String allowCommand = "iptables -I INPUT 1 -s "
				+ ip
				+ " -p udp --destination-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";

		String allow2Command = "iptables -I INPUT 1 -s "
				+ ip
				+ " -p udp --source-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";
		try {
			getApp().coretask.runRootCommand(allowCommand);
			getApp().coretask.runRootCommand(allow2Command);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void allowMasqueradeForIP(String ip) {
		String allowCommand = "iptables -t nat -A POSTROUTING -s "
				+ ip + " -o rmnet0 -j MASQUERADE";
		try {
			getApp().coretask.runRootCommand(allowCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void allowMasqueradeFor(String sourceIP, String destinationIP,
			String layer4proto, String destinationPort) {
		String allowCommand = "iptables -t nat -A POSTROUTING -s " + sourceIP
				+ " -d " + destinationIP + " -p " + layer4proto + " --dport "
				+ destinationPort
				+ " -o rmnet0 -j MASQUERADE";
		try {
			getApp().coretask.runRootCommand(allowCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void removeMasqueradeFor(String sourceIP, String destinationIP,
			String layer4proto, String destinationPort) {
		String allowCommand = "iptables -t nat -D POSTROUTING -s " + sourceIP
				+ " -d " + destinationIP + " -p " + layer4proto + " --dport "
				+ destinationPort + " -o rmnet0 -j MASQUERADE";
		try {
			getApp().coretask.runRootCommand(allowCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void removeMasqueradeForIP(String ip) {
		String allowCommand = "iptables -t nat -D POSTROUTING -s "
				+ ip + " -o rmnet0 -j MASQUERADE";
		try {
			getApp().coretask.runRootCommand(allowCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public void blockOVpnAccessForIP(String ip) {
		String allowCommand = "iptables -D INPUT -s "
				+ ip
				+ " -p udp --destination-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";
		String allow2Command = "iptables -D INPUT -s "
				+ ip
				+ " -p udp --destination-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";
		try {
			getApp().coretask.runRootCommand(allowCommand);
			getApp().coretask.runRootCommand(allow2Command);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	// private void allowRmnet0Traffic() {
	// String allowCommand = "iptables -A INPUT -i rmnet0 -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(allowCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	// private void allowServalServices() {
	//
	// allowManagementConnectionsForServer();
	// allowVpnServerTraffic();
	// allowBatmanTraffic();
	// allowAsterisk();
	// allowRhizome();
	// }

	// private void allowVpnServerTraffic() {
	// String command =
	// "iptables -A INPUT -p udp --destination-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(command);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	private void flushRules() {

		flushFirewall();
		cleanMasquerade();
		// unblockAllForwarding();

	}


	private void flushFirewall() {
		String flushCommand = "iptables -F";
		try {
			getApp().coretask.runRootCommand(flushCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

	}

	// public void allowVpnForClient() {
	// String command =
	// "iptables -A INPUT -p udp --source-port 1194 -m state --state NEW,ESTABLISHED -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(command);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	// private void allowRhizome() {
	// String allowCommand =
	// "iptables -A INPUT -p udp --source-port 6666 -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(allowCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	//
	// }

	// private void allowAsterisk() {
	// String allowCommand =
	// "iptables -A INPUT -p udp --source-port 5060 -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(allowCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	//
	// }

	// private void allowBatmanTraffic() {
	// String allow1Command =
	// "iptables -A INPUT -p udp --destination-port 4305 -j ACCEPT";
	// String allow2Command =
	// "iptables -A INPUT -p udp --destination-port 35064 -j ACCEPT";
	//
	// try {
	// getApp().coretask.runRootCommand(allow1Command);
	// getApp().coretask.runRootCommand(allow2Command);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	//
	// }

	// public void allowManagementConnectionsForClient() {
	//
	// String allowCommand = "iptables -A INPUT -p tcp --source-port "
	// + getProperty("management_port") + " -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(allowCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	//
	// }

	// private void allowManagementConnectionsForServer() {
	//
	// String allowCommand = "iptables -A INPUT -p tcp --destination-port "
	// + getProperty("management_port") + " -j ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(allowCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	//
	// }

	// private void blockIncomingTraffic() {
	// String blockCommand = "iptables -P INPUT DROP";
	// try {
	// getApp().coretask.runRootCommand(blockCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	// private void blockAllForwarding() {
	//
	// String blockCommand = "iptables -P FORWARD DROP";
	// try {
	// getApp().coretask.runRootCommand(blockCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }
	//
	// private void unblockAllForwarding() {
	// String blockCommand = "iptables -P FORWARD ACCEPT";
	// try {
	// getApp().coretask.runRootCommand(blockCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	// private void masqueradeTraffic(String identifier) {
	// String masqueradeCommand =
	// "iptables -t nat -A POSTROUTING -o rmnet0 -j MASQUERADE";
	// try {
	// getApp().coretask.runRootCommand(masqueradeCommand);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	public void stopMobileCloudService() {

		//stop VPN
		if (Utilities.killOpenVPN(this)) {
			Log.d(TAG, "killed openvpn successfully");

		} else {
			Log.d(TAG, "failed to kill Openvpn");

		}
		//stop PeerCommunicator
		hostActivity.stopService(new Intent(hostActivity,
				PeerServerCommunicator.class));

		flushRules();

	}

	private void cleanMasquerade() {
		String masqueradeCommand = "iptables -t nat -F POSTROUTING";
		try {
			getApp().coretask.runRootCommand(masqueradeCommand);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public SSLContext createSSLContext(boolean registerContext)
			throws UnrecoverableKeyException, KeyManagementException,
			KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException {
		return security.getSSLContext(registerContext);
	}

	private RuntimeConfiguration(){

	}

	public static synchronized RuntimeConfiguration getInstance(){
		if(singleton == null){
			singleton = new RuntimeConfiguration();
			singleton.security = new SSLContextHolder();
		}
		return singleton;
	}

	public Properties getProperties(){
		return properties;
	}

	public String getProperty(String key){
		return properties.getProperty(key);
	}

	public void setProperties(Properties props){
		properties = props;
	}

	public X509Certificate getToken() {
		return token;
	}

	public void setToken(X509Certificate token) {
		this.token = token;
	}

	public PrivateKey getPrivKey() {
		return privKey;
	}

	public void setPrivKey(PrivateKey privKey) {
		this.privKey = privKey;
	}

	public KeyStore getTrustStore() {
		return trustStore;
	}

	public KeyStore getKeyStore() {
		return keyStore;
	}

	public File getCertificateDir() {
		return certificateDir;
	}

	public void setCertificateDir(File certificateDir) {
		this.certificateDir = certificateDir;
	}

	public File getConfigurationDir() {
		return configurationDir;
	}

	public void setConfigurationDir(File configurationDir) {
		this.configurationDir = configurationDir;
	}


	public SSLContext getSslContex(boolean regstate)
			throws UnrecoverableKeyException, KeyManagementException,
			KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException {
		return security.getSSLContext(regstate);
	}

	public void forceContextRebuild() throws UnrecoverableKeyException,
			KeyManagementException, NoSuchAlgorithmException,
			KeyStoreException, CertificateException, IOException {
		security.forceRebuildContext();
	}

	// public void setSslContex(SSLContext sslContex) {
	// this.sslContex = sslContex;
	// }

	public AssetManager getAssetManager() {
		return assetManager;
	}

	public void setAssetManager(AssetManager assetManager) {
		this.assetManager = assetManager;
	}

	public ContentResolver getContentResolver() {
		return contentResolver;
	}

	public void setContentResolver(ContentResolver contentResolver) {
		this.contentResolver = contentResolver;
	}

	// public boolean isSecurityRequested() {
	// return securityRequested;
	// }
	//
	// public void setSecurityRequested(boolean security_requested) {
	// this.securityRequested = security_requested;
	// }

	// public boolean isEnableMobileCloud() {
	// return enableMobileCloud;
	// }
	//
	// public void setEnableMobileCloud(boolean enableMobileCloud) {
	// this.enableMobileCloud = enableMobileCloud;
	// }

	public Activity getHostActivity() {
		return hostActivity;
	}

	public void setHostActivity(Activity hostActivity) {
		this.hostActivity = hostActivity;
		this.setApp((ServalBatPhoneApplication) hostActivity.getApplication());
	}

	public ServalBatPhoneApplication getApp() {
		return app;
	}

	public void setApp(ServalBatPhoneApplication app) {
		this.app = app;
	}

	public boolean isReadyToCloud() {
		return isReadyToCloud;
	}

	public void setReadyToCloud(boolean isReadyToCloud) {
		this.isReadyToCloud = isReadyToCloud;
	}

	public SharedPreferences getPreferences() {
		return preferences;
	}

	public void setPreferences(SharedPreferences preferences) {
		this.preferences = preferences;
	}

	public String getOvpnStatusPath() {
		return ovpnStatusPath;
	}

	public void setOvpnStatusPath(String ovpnStatusPath) {
		this.ovpnStatusPath = ovpnStatusPath;
	}

	public void setInterfaceAddressNetmask(boolean withTun1) {
		try {
			String ifconfig = app.coretask.runCommandForOutput(false,
					"/system/bin/ifconfig");
			ipAndNetmaskEth0 = Utilities.findIPandNetmask(ifconfig,
					"eth0");
			ipAndNetmaskTun0 = Utilities.findIPandNetmask(ifconfig, "tun0");
			if (withTun1) {
				ipAndNetmaskTun1 = Utilities.findIPandNetmask(ifconfig, "tun1");
			}
		} catch (Exception e) {
			Log.d(TAG, "failed setting network infos:" + e.getMessage());
			e.printStackTrace();
		}

		Log.d(TAG, "eth0:" + ipAndNetmaskEth0[0] + ipAndNetmaskEth0[1]);
		Log.d(TAG, "tun0:" + ipAndNetmaskTun0[0] + ipAndNetmaskTun0[1]);
	}

	public String getFbAccessToken() {
		return fbAccessToken;
	}

	public void setFbAccessToken(String fbAccessToken) {
		this.fbAccessToken = fbAccessToken;
	}

	public long getFbAccessExpire() {
		return fbAccessExpire;
	}

	public void setFbAccessExpire(long fbAccessExpire) {
		this.fbAccessExpire = fbAccessExpire;
	}

}
