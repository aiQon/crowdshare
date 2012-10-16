package de.cased.mobilecloud;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;
import ext.org.bouncycastle.openssl.PEMReader;
import ext.org.bouncycastle.openssl.PEMWriter;

public class Utilities {

	private static String TAG = "Utilities";

	public static int exec(String command){
		Process proc = null;
		BufferedReader error = null;
		try{
			proc = Runtime.getRuntime().exec(command);
			error = toReader(proc.getErrorStream());
			int retVal = proc.waitFor();
			if(retVal != 0){
				String line;
				while((line=error.readLine()) != null){
					Log.d(TAG, "exec error:" + line);
				}
			}
			return retVal;
		}catch(Exception e){
			Log.d(TAG, e.getMessage());
			if(proc != null) {
				proc.destroy();
			}
		}
		return -1;
	}

	public static BufferedReader toReader(InputStream is) {
        return new BufferedReader(new InputStreamReader(is), 8192);
    }

	public static ArrayList<String> readLinesFromFile(String filename) {
        ArrayList<String> lines = new ArrayList<String>();
        try {
            BufferedReader br = toReader(new FileInputStream(filename));
            String line;
            while((line = br.readLine()) != null) {
                lines.add(line.trim());
            }
        } catch (Exception e) {
            return null;
        }
        return lines;
    }

	public static class TrafficData {
        long rx_bytes;
        long rx_pkts;
        long tx_bytes;
        long tx_pkts;
        void diff(TrafficData ref, TrafficData cur) {
            rx_bytes = cur.rx_bytes - ref.rx_bytes;
            rx_pkts  = cur.rx_pkts  - ref.rx_pkts;
            tx_bytes = cur.tx_bytes - ref.tx_bytes;
            tx_pkts  = cur.tx_pkts  - ref.tx_pkts;
        }
        void minus(TrafficData ref) {
            diff(ref, this);
        }
        void div(long val) {
            if (val == 0) return;
            rx_bytes /= val;
            rx_pkts /= val;
            tx_bytes /= val;
            tx_pkts /= val;
        }
    }

	public static TrafficData fetchTrafficData(String device) {
        // Returns traffic usage for all interfaces starting with 'device'.
        TrafficData d = new TrafficData();
        if (device == "")
            return d;
        for (String line : readLinesFromFile("/proc/net/dev")) {
            if (line.startsWith(device)) {
                line = line.replace(':', ' ');
                String[] values = line.split(" +");
                d.rx_bytes += Long.parseLong(values[1]);
                d.rx_pkts  += Long.parseLong(values[2]);
                d.tx_bytes += Long.parseLong(values[9]);
                d.tx_pkts  += Long.parseLong(values[10]);
            }
        }
        return d;
    }

	public static class TrafficStats {
        private TrafficData start = new TrafficData();
        TrafficData total = new TrafficData();
        TrafficData rate  = new TrafficData();
        private long t_last = 0;

        void init(TrafficData td) {
            start = td;
            t_last = new java.util.Date().getTime(); // in ms
        }
        void update(TrafficData td) {
            td.minus(start);
            rate.diff(total, td);
            total = td;
            long now = new java.util.Date().getTime();
            rate.div((now - t_last) / 1000); // per second
            t_last = now;
        }
    }

	 /** returns the first wireless interface -- the kernel module must be loaded */
    public static String findWifiIface() {
        try {
            String line = readLinesFromFile("/proc/net/wireless").get(2);
            return line.substring(0,line.indexOf(":"));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isExternalStorageWriteable(){
    	boolean mExternalStorageAvailable = false;
    	boolean mExternalStorageWriteable = false;
    	String state = Environment.getExternalStorageState();

    	if (Environment.MEDIA_MOUNTED.equals(state)) {
    	    // We can read and write the media
    	    mExternalStorageAvailable = mExternalStorageWriteable = true;
    	} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
    	    // We can only read the media
    	    mExternalStorageAvailable = true;
    	    mExternalStorageWriteable = false;
    	} else {
    	    // Something else is wrong. It may be one of many other states, but all we need
    	    //  to know is we can neither read nor write
    	    mExternalStorageAvailable = mExternalStorageWriteable = false;
    	}

    	return mExternalStorageAvailable & mExternalStorageWriteable;
    }

    //http://stackoverflow.com/questions/10069756/execute-su-c-from-java-code-in-android-application
    public static String runCommand(String[] commands)
    {
    DataOutputStream outStream = null;
    DataInputStream responseStream;
    try {
        ArrayList<String> logs = new ArrayList<String>();
        Process process = Runtime.getRuntime().exec("su");
        Log.i(TAG, "Executed su");
        outStream = new DataOutputStream(process.getOutputStream());
        responseStream = new DataInputStream(process.getInputStream());

        for (String single : commands)
        {
            Log.i(TAG, "Command = " + single );
            outStream.writeBytes(single + "\n");
            outStream.flush();
            if(responseStream.available() > 0)
            {
                Log.i(TAG, "Reading response");
                logs.add(responseStream.readLine());
                Log.i(TAG, "Read response");
				} else {
					Log.i(TAG, "No response available");
            }
        }
        outStream.writeBytes("exit\n");
        outStream.flush();
        String log = "";
        for(int i=0;i<logs.size();i++)
        {
            log += logs.get(i) + "\n";
        }
        Log.i(TAG, "Execution compeleted");
        return log;
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
        return null;
    }



    /**
     * searches routing table for ip addresses matching the issued interface name and the issued prefix.
     * Maybe encapsulate the result in a host object and add the metric, we will see.
     *
     * @param iface the interface to filter on
     * @param olsr_prefix the prefix of the ip addresses which to search for. should be something like "172.29"
     * @return list of matched ip addresses
     */
	public static ArrayList<RouteEntry> readRoutingTable(
			RuntimeConfiguration config) {
    	ArrayList<RouteEntry> results = new ArrayList<RouteEntry>();
		String iface = Utilities.findWifiIface();
		Log.d(TAG, "wifi interface is:" + iface);
		String ifconfig = "";

		try {
			ifconfig = config.getApp().coretask.runCommandForOutput(
					false,
					"/system/bin/ifconfig");
		} catch (IOException e) {
			Log.d(TAG, e.getMessage());
		}
		// Log.d(TAG, "ifconfig info is: " + ifconfig);
		String ipInfo[] = findIPandNetmask(ifconfig, iface);

		try {
			// String iface = "eth0";
			// String olsr_prefix = "172.29";
			BufferedReader reader = toReader(new FileInputStream(new
					File("/proc/net/route")));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith(iface)) {
					RouteEntry entry = new RouteEntry();
					StringTokenizer tokens = new StringTokenizer(line);
					tokens.nextToken(); // waste the first one since its eth0
					String ip = littleEndianToIPAddress(tokens.nextToken());
					// String gateway =
					// littleEndianToIPAddress(tokens.nextToken());
					tokens.nextToken(); // flags
					tokens.nextToken(); // RefCount
					tokens.nextToken(); // Use
					int metric = Integer.parseInt(tokens.nextToken());
					entry.setDestination(ip);
					// entry.setNextHop(gateway);
					entry.setMetric(metric);
					if (isInRange(ip, ipInfo) && !ip.equals(ipInfo[0])) {
						Log.d(TAG, "added new entry:" + ip);
						results.add(entry);
					}
				}
			}
		} catch (Exception e) {
			Log.d(TAG, e.getMessage());
			e.printStackTrace();
		}
    	return results;
    }
    /**
     * Converts 32 bit little endian input to an IP address.
     * @param input
     */
    public static String littleEndianToIPAddress(String input){
		try{
	    	return Integer.parseInt(input.substring(6, 8), 16) + "." +
	    			Integer.parseInt(input.substring(4, 6), 16) + "." +
	    			Integer.parseInt(input.substring(2, 4), 16) + "." +
	    			Integer.parseInt(input.substring(0, 2), 16);
		}catch(NumberFormatException e){

		}
		return "";
    }


    public static X509Certificate loadCertificate(File location){
		try{
			PEMReader tokenReader = new PEMReader(new FileReader(location));
			Object rawToken = tokenReader.readObject();
			if(rawToken instanceof Certificate){
				X509Certificate extractedToken = (X509Certificate) rawToken;
				Log.d(TAG, "extracted token dn:" + extractedToken.getSubjectDN());
				return extractedToken;
			}
		}catch(Exception e){
			Log.d(TAG, "Failed loading token");
		}
		Log.d(TAG, "failed finding the requested certificate at: " + location);
		return null;
	}

    public static String readSkeleton(AssetManager am, String filename) throws FileNotFoundException, IOException {
		BufferedReader skeletonFile = new BufferedReader(new InputStreamReader(am.open(filename)));
		StringBuilder builder = new StringBuilder();
		String line;

		while ((line = skeletonFile.readLine()) != null) {
			builder.append(line);
			builder.append('\n');
		}
		skeletonFile.close();
		return builder.toString();
	}

    public static boolean writePemFile(String location, Object data, File certdir){
    	Log.d(TAG, "writing PEM file to " + location);
		try {
			PEMWriter privatepemWriter = new PEMWriter(new FileWriter(new File(certdir, location)));
			privatepemWriter.writeObject(data);
			privatepemWriter.flush();
			privatepemWriter.close();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    }

    public static void closeOutputStream(OutputStream os) {
		if(os != null){
			try {
				os.close();
			} catch (IOException e) {
				//pass
			}finally{
				os = null;
			}
		}
	}

	public static void closeInputStream(InputStream is) {
		if(is != null){
			try {
				is.close();
			} catch (IOException e) {
				//pass
			}finally{
				is = null;
			}
		}
	}

	public static HashMap<String, ArrayList<String>> getLocalIpAddresses() {
		try {
			HashMap<String, ArrayList<String>> result = new HashMap<String, ArrayList<String>>();
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();

				String name = intf.getName();
				ArrayList<String> addresses = new ArrayList<String>();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {

					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						addresses.add(inetAddress.getHostAddress().toString());
					}
				}
				result.put(name, addresses);
			}
			return result;
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}

	public static String getIdentifier(
			HashMap<String, ArrayList<String>> addresses) {
		String wifi_address = addresses.get("eth0").get(0);	//for now we expect there to be only 1 address, the one OLSRd has assigned
		//need to extract the 3rd and 4th octet of the address
		StringTokenizer tokens = new StringTokenizer(wifi_address, ".");
		tokens.nextElement();
		tokens.nextElement();
		String identifier = tokens.nextToken() + "." + tokens.nextToken();
		return identifier;
	}

	public static boolean deleteVpnConfig(RuntimeConfiguration config) {
		return new File(config.getConfigurationDir(), config.getProperties()
				.getProperty("vpn_server_config_loc")).delete();
	}

	public static boolean buildOpenVpnConfig(RuntimeConfiguration config,
			String identifier) {
		try {
			Log.d(TAG, "Trying to build openvpn server conf");
			String skeleton = Utilities
					.readSkeleton(config.getAssetManager(), config
					.getProperties().getProperty("vpn_server_skeleton"));

			Log.d(TAG, "read skeleton file is:");
			Log.d(TAG, skeleton);

			skeleton = skeleton.replaceAll("<IDENTIFIER>", identifier);
			skeleton = skeleton
					.replaceAll(
							"<CA_CERT>",
							new File(config.getCertificateDir(), config
									.getProperties().getProperty(
											"ca_cert_dest_loc"))
									.getAbsolutePath());
			skeleton = skeleton.replaceAll("<USER_KEY>",
					new File(config.getCertificateDir(), config.getProperties()
							.getProperty("private_key_loc")).getAbsolutePath());
			skeleton = skeleton.replaceAll("<USER_CERT>",
					new File(config.getCertificateDir(), config.getProperties()
							.getProperty("token_loc")).getAbsolutePath());
			skeleton = skeleton.replaceAll("<DH_PARAMS>",
					new File(config.getCertificateDir(), config.getProperties()
							.getProperty("dh_loc")).getAbsolutePath());

			if (Utilities.isExternalStorageWriteable()) {
				File status = new File(new File(config.getProperties()
						.getProperty("sdcard_loc")),
						config.getProperties().getProperty("ovpn_status_file"));
				config.setOvpnStatusPath(status.getAbsolutePath());

				File log = new File(new File(config.getProperties()
						.getProperty("sdcard_loc")),
						config.getProperties().getProperty("ovpn_log_file"));
				skeleton = skeleton.replaceAll("<LOG>", log.getAbsolutePath());
				skeleton = skeleton.replaceAll("<STATUS>",
						status.getAbsolutePath());
			} else {
				Log.d(TAG,
						"There is no external storage available to write to. Writing to " + config.getProperties()
										.getProperty("local_temp"));
				File status = new File(new File(config.getProperties()
						.getProperty("local_temp")),
						config.getProperties().getProperty("ovpn_status_file"));
				config.setOvpnStatusPath(status.getAbsolutePath());

				File log = new File(new File(config.getProperties()
						.getProperty("local_temp")),
						config.getProperties().getProperty("ovpn_log_file"));
				skeleton = skeleton.replaceAll("<LOG>", log.getAbsolutePath());
				skeleton = skeleton.replaceAll("<STATUS>",
						status.getAbsolutePath());

			}
			String destination = config.getConfigurationDir()
					+ "/" + config.getProperties().getProperty(
							"vpn_server_config_loc");

			Log.d(TAG, "going to write server config to:"+destination);

			FileOutputStream fos = new FileOutputStream(new File(destination));
			fos.write(skeleton.getBytes());
			Utilities.closeOutputStream(fos);
			Log.d(TAG, skeleton);
			return true;
		} catch (IOException e) {
			Log.d(TAG, "Error writing the config:" + e.getMessage());
			e.printStackTrace();
		}

		return false;
	}

	public static boolean launchOpenVPN(RuntimeConfiguration config) {
		String args = "--config "
				+ new File(config.getConfigurationDir(), config.getProperties()
						.getProperty("vpn_server_config_loc"))
						.getAbsolutePath() + " --daemon";
		String command = config.getProperties().getProperty("path_ovpn_bin")
				+ " " + args;
		try {
			config.getApp().coretask.runRootCommand(command);
			return true;
		} catch (Exception e) {
			Log.d(TAG, "failed to start OpenVPN, " + e.getMessage());
		}
		return false;

	}

	public static boolean killOpenVPN(RuntimeConfiguration config) {
		try {
			config.getApp().coretask.killProcess(config.getProperties()
					.getProperty("path_ovpn_bin"), true);
			return true;
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return false;
	}

	public static boolean isTokenAvailable(RuntimeConfiguration config) {
		File token_file = new File(config.getCertificateDir(), config.getProperties()
				.getProperty("token_loc"));

		File key_file = new File(config.getCertificateDir(), config
				.getProperties()
				.getProperty("private_key_loc"));

		File dh_loc = new File(config.getCertificateDir(), config
				.getProperties()
				.getProperty("dh_loc"));

		if (token_file.exists() && key_file.exists() && dh_loc.exists())
			return true;
		else
			return false;

	}

	public static boolean isInRange(String ip, String[] netInfo) {


		StringTokenizer maskTokens = new StringTokenizer(netInfo[1], ".");

		StringTokenizer ipTokens = new StringTokenizer(ip, ".");
		int ipArray[] = new int[4];
		ipArray[0] = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		ipArray[1] = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		ipArray[2] = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		ipArray[3] = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());

		String networkAddress = ipArray[0] + "." + ipArray[1] + "."
				+ ipArray[2] + "." + ipArray[3];

		return netInfo[0].equals(networkAddress);

	}

	public static int stringIPtoInt(String ip) {
		StringTokenizer ipTokens = new StringTokenizer(ip, ".");
		int ipArray[] = new int[4];
		ipArray[0] = Integer.parseInt(ipTokens.nextToken());
		ipArray[1] = Integer.parseInt(ipTokens.nextToken());
		ipArray[2] = Integer.parseInt(ipTokens.nextToken());
		ipArray[3] = Integer.parseInt(ipTokens.nextToken());

		return (ipArray[0] << 24) + (ipArray[1] << 16) + (ipArray[2] << 8)
				+ ipArray[3];
	}

	public static int ipProtocolToInt(String proto) {
		if (proto.equals("TCP")) {
			return 6;
		} else if (proto.equals("UDP")) {
			return 17;
		}
		return -1;
	}


	/**
	 *
	 * @param source
	 *            ifconfig output
	 * @param device
	 *            device of interest
	 * @return
	 */
	public static String[] findIPandNetmask(String source, String device) {
		int deviceIndex = source.indexOf(device);
		source = source.substring(deviceIndex);
		int nextLine = source.indexOf("\n");
		source = source.substring(nextLine);
		int startOfIP = source.indexOf(":");
		int endOfIP = source.indexOf(" ", startOfIP);
		String ip = source.substring(startOfIP + 1, endOfIP);

		int startOfMask = source.indexOf("Mask:");
		int endOfMask = source.indexOf("\n", startOfMask);
		String mask = source.substring(startOfMask + 5, endOfMask);

		StringTokenizer ipTokens = new StringTokenizer(ip, ".");
		StringTokenizer maskTokens = new StringTokenizer(mask, ".");

		int firstOctet = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		int secondOctet = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		int thirdOctet = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());
		int fourthOctet = Integer.parseInt(ipTokens.nextToken())
				& Integer.parseInt(maskTokens.nextToken());

		String networkAddress = firstOctet + "." +
				secondOctet + "." +
				thirdOctet + "." +
				fourthOctet;

		return new String[] {
				networkAddress, mask
		};

	}

	public static String getRemoteIP(RuntimeConfiguration config) {
		try {
			String ifconfig = config.getApp().coretask.runCommandForOutput(
					false,
					"/system/bin/ifconfig");
			String networkAndMask[] = Utilities.findIPandNetmask(ifconfig,
					"tun1");
			int lastIn = networkAndMask[0].lastIndexOf('.');
			String remoteIP = networkAndMask[0].substring(0, lastIn) + ".1";
			return remoteIP;

		} catch (Exception e) {

		}
		return null;
		// return socket.getInetAddress().getHostAddress();
	}

	public static String getTun0IP(RuntimeConfiguration config) {
		try {
			String ifconfig = config.getApp().coretask.runCommandForOutput(
					false,
					"/system/bin/ifconfig");
			String networkAndMask[] = Utilities.findIPandNetmask(ifconfig,
					"tun0");
			int lastIn = networkAndMask[0].lastIndexOf('.');
			String remoteIP = networkAndMask[0].substring(0, lastIn) + ".1";
			return remoteIP;

		} catch (Exception e) {

		}
		return null;
		// return socket.getInetAddress().getHostAddress();
	}

	public static void haltClientVPNDaemons(RuntimeConfiguration config) {
		Log.d(TAG, "killing VPN Clients");
		String ovpnName = ".ovpn";
		String command = "ps w | grep " + ovpnName;

		try {
			String outcome = config.getApp().coretask.runCommandForOutput(
					false,
					command);
			String[] lines = outcome.split("\r\n|\r|\n");
			if (lines.length > 1) {
				// get first element of each line and use it to kill the process

				for (String line : lines) {
					Log.d(TAG, "read line:" + line);
					String pid = line.trim().split(" ")[0];
					Log.d(TAG, "going to kill pid:" + pid);
					config.getApp().coretask.runRootCommand("kill " + pid);
				}
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}

	}

	public static boolean is3GAvailable(RuntimeConfiguration config) {
		// TODO check for rmnet0 interface
		try {
			String interface3G = "rmnet0";
			String ifconfig = config.getApp().coretask.runCommandForOutput(
					false,
					"/system/bin/ifconfig");
			return (ifconfig.indexOf(interface3G) >= 0);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return false;
	}


	public static boolean isTargetForTetheringToServer(String ip,
			RuntimeConfiguration config) {

		if (!isInRange(ip, config.getIpAndNetmaskEth0())
				&& !isInRange(ip, config.getIpAndNetmaskTun0())) {
			return true;
		} else {
			return false;
		}
	}

	public static String intIpToString(int intIp) {
		int firstOctet = (intIp >> 24) & 0xFF;
		int secondOctet = (intIp >> 16) & 0xFF;
		int thirdOctet = (intIp >> 8) & 0xFF;
		int fourthOctet = intIp & 0xFF;
		return firstOctet + "." + secondOctet + "." + thirdOctet + "."
				+ fourthOctet;
	}

	public static String ipHeaderIdToString(int id) {
		if (id == 6) {
			return "tcp";
		} else if (id == 17) {
			return "udp";
		}

		return null;
	}

	public static void writeToFile(List<String> r1Friends,
			String r1Destination, RuntimeConfiguration config) {
		FileOutputStream fos;
		try {
			fos = config.getApp().openFileOutput(r1Destination,
					Context.MODE_PRIVATE);
			for (String r1Friend : r1Friends) {
				fos.write(r1Friend.getBytes());
				fos.write('\n');
			}
			fos.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public static List<String> readFromFile(String file,
			RuntimeConfiguration config) {
		List<String> readLines = new ArrayList<String>();
		try {
			// File readFile = new File(config.getApp().getDir("files",
			// Context.MODE_WORLD_READABLE), file);


			BufferedReader reader = toReader(config.getApp()
					.openFileInput(file));
			String read = null;
			while ((read = reader.readLine()) != null) {
				readLines.add(read);
			}

		} catch (IOException e) {

		}
		return readLines;
	}
}