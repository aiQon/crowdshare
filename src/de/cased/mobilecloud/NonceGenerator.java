package de.cased.mobilecloud;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class NonceGenerator {
	
	public NonceGenerator(){
		String nonce = newNonce();
		System.out.println("nonce:" + nonce);
	}
	
	public static String newNonce(){
		Double nonceD = Math.random() * System.currentTimeMillis();
		byte[] nonceB = nonceD.toString().getBytes();
		String nonce = toSHA1(nonceB);
		return nonce;
	}
	
	public static void main(String[] args) {
		new NonceGenerator();
	}
	public static String toSHA1(byte[] convertme) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return byteArrayToHexString(md.digest(convertme));
	}

	public static String byteArrayToHexString(byte[] b) {
		String result = "";
		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}
}
