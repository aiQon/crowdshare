package de.cased.mobilecloud.setintersection;
/**
 * This is an implementation of a protocol for private set intersection cardinality.
 * Client's input is a list c of v elements and Server's input is a list s of w elements.
 * Client obtains as output |c \cap v| and Server obtains no output.
 *
 * It implements the protocol in Fig. 1 of http://eprint.iacr.org/2011/141
 *
 * (c) 2012 by Thomas Schneider <thomas.schneider@ec-spride.de>
 */

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class ByteComparator implements Comparator<byte[]> {
	  @Override
	public int compare(byte[] a, byte[] b) {
	    int n = Math.min(a.length, b.length);
	    for (int i = 0; i < n; ++i) {
	      int delta = a[i] - b[i];  // OK since bytes are smaller than ints.
	      if (delta != 0) { return delta; }
	    }
	    return 0;
	  }
}

public class PrivateSetIntersectionCardinality {
	final static int qLength = 160;
	final static int pLength = 1024;

	static SecureRandom rnd = null;
	MessageDigest md = null;

	BigInteger p, q, g;
	int numberOfServerFriends, numberOfClientFriends;

	public void setNumberOfClientFriends(int number) {
		numberOfClientFriends = number;
	}

	public void setNumberOfServerFriends(int number) {
		numberOfServerFriends = number;
	}

	public PrivateSetIntersectionCardinality() {
		init();
	}

	private void init() {
		try {
			rnd = SecureRandom.getInstance("SHA1PRNG");
		}
		catch (NoSuchAlgorithmException e) {
			System.out.println("Error: SHA1PRNG not found."+e);
		}

		try {
			md=MessageDigest.getInstance("SHA-1");
		}
		catch (Exception e) {
			System.out.println("Error: SHA-1 not found."+e);
		}

		p = new BigInteger("B10B8F96A080E01DDE92DE5EAE5D54EC52C99FBCFB06A3C69A6A9DCA52D23B616073E28675A23D189838EF1E2EE652C013ECB4AEA906112324975C3CD49B83BFACCBDD7D90C4BD7098488E9C219A73724EFFD6FAE5644738FAA31A4FF55BCCC0A151AF5F0DC8B4BD45BF37DF365C1A65E68CFDA76D4DA708DF1FB2BC2E4A4371", 16);
		g = new BigInteger("A4D1CBD5C3FD34126765A442EFB99905F8104DD258AC507FD6406CFF14266D31266FEA1E5C41564B777E690F5504F213160217B4B01B886A5E91547F9E2749F4D7FBD7D3B9A92EE1909D0D2263F80A76A6A24C087A091F531DBF0A0169B6A28AD662A4D18E73AFA32D779D5918D08BC8858F4DCEF97C2A24855E6EEB22B3B2E5", 16);
		q = new BigInteger("F518AA8781A8DF278ABA4E7D64B7CB9D49462353", 16);

		/*
    	private static final int certainty = 80;
		assert(p.isProbablePrime(certainty));
		assert(q.isProbablePrime(certainty));
		assert(g.modPow(q,p).equals(BigInteger.ONE));
		*/
	}

    private BigInteger H(byte[] v) { // Full Domain Hash
    	md.reset();
    	md.update(v);
    	byte[] hash=md.digest();
    	BigInteger hv = new BigInteger(1, hash);
    	BigInteger res = g.modPow(hv, p);
    	return res;
    }

    private byte[] Hprime(BigInteger v) {
    	byte[] vb=v.toByteArray();
		md.reset();
		md.update(vb);
		byte[] digest=md.digest();
		byte[] buf = new byte[160/8];
        System.arraycopy(digest, 0, buf, 0, 160/8);
		return buf;
	}

    void permute(BigInteger[] a) { // permutes a in place
    	List<BigInteger> list = new ArrayList<BigInteger>(a.length);
    	for(int i=0; i<a.length; i++)
    		list.add(a[i]);
    	java.util.Collections.shuffle(list);
    	for(int i=0; i<a.length; i++)
    		a[i]=list.get(i);
    }

	public BigInteger server_round_1(byte[][] c, BigInteger[] out) {
    	// choose random Rc
    	BigInteger Rc;
    	do {
    	    Rc = new BigInteger(qLength, rnd);
    	} while (Rc.compareTo(q) >= 0);

		for (int i = 0; i < numberOfServerFriends; i++) {
    		out[i] = H(c[i]).modPow(Rc, p);
    	}
    	return Rc;
    }

	public BigInteger client_round_1(byte[][] s, byte[][] ts) {
		// choose random Rs
		BigInteger Rs;
		do {
			Rs = new BigInteger(qLength, rnd);
		} while (Rs.compareTo(q) >= 0);

		// permute(s); // instead of permuting s we sort ts in the very end
		for (int i = 0; i < numberOfClientFriends; i++) {
			ts[i] = Hprime(H(s[i]).modPow(Rs, p));
		}
		java.util.Arrays.sort(ts, new ByteComparator());

		return Rs;
	}

	public BigInteger server_round_2(BigInteger Rc) {
		return Rc.modInverse(q);
    }

	public void client_round_2(BigInteger[] a, BigInteger Rs,
			BigInteger[] a_tick) {
		for (int i = 0; i < numberOfServerFriends; i++) {
    		a_tick[i] = a[i].modPow(Rs, p);
    	}
		permute(a_tick);
    }

	public int server_round_3(BigInteger[] a_tick, byte[][] ts,
			BigInteger Rc_inv) {
		byte[][] tc = new byte[numberOfServerFriends][];
		for (int i = 0; i < numberOfServerFriends; i++) {
    		tc[i]=Hprime(a_tick[i].modPow(Rc_inv, p));
    	}

    	ByteComparator myComparator = new ByteComparator();
    	java.util.Arrays.sort(tc, myComparator);

    	// count how many elements are in common
    	int i=0; int j=0;
    	int found=0;
		while (i < numberOfServerFriends & j < numberOfClientFriends) {
    		int cmp=myComparator.compare(tc[i],ts[j]);
    		if(cmp==0){
    			i+=1;
    			j+=1;
    			found++;
    		}
    		else if(cmp<0) {
    			i+=1;
    		}
    		else {
    			j+=1;
    		}
    	}
    	return found;
    }

	public static void main(String[] args) {
		doComputation();
	}

	private static void perform(String[] serverIds, String[] clientIds) {
		PrivateSetIntersectionCardinality serverEngine = new PrivateSetIntersectionCardinality();
		serverEngine.numberOfServerFriends = serverIds.length; // number of
																// inputs
		// provided by server
		serverEngine.numberOfClientFriends = clientIds.length; // number of
																// inputs
		// provided by client

		byte[][] c = new byte[serverEngine.numberOfServerFriends][]; // Server's
																		// inputs
		byte[][] s = new byte[serverEngine.numberOfClientFriends][]; // Client's
																		// inputs

		// initialize inputs
		for (int i = 0; i < serverEngine.numberOfServerFriends; i++) {
			BigInteger serverFriend = new BigInteger(serverIds[i]);
			c[i] = serverFriend.toByteArray();
		}
		for (int i = 0; i < serverEngine.numberOfClientFriends; i++) {
			BigInteger clientFriend = new BigInteger(clientIds[i]);
			s[i] = clientFriend.toByteArray();
		}


		// myClass.init();

		serverEngine
				.setNumberOfServerFriends(serverEngine.numberOfServerFriends);
		serverEngine
				.setNumberOfClientFriends(serverEngine.numberOfClientFriends);


		// ROUND 1 ----------------------------------------------------		// ROUND 1 ----------------------------------------------------------
		long tc1s = System.currentTimeMillis();
		BigInteger[] a = new BigInteger[serverEngine.numberOfServerFriends];
		BigInteger Rc = serverEngine.server_round_1(c, a); // done
		long tc1e = System.currentTimeMillis();

		long tc2s = System.currentTimeMillis();
		BigInteger Rc_inv = serverEngine.server_round_2(Rc); // done
		long tc2e = System.currentTimeMillis();

		// send "a" and #ofServerFriends

		long ts1s = System.currentTimeMillis();
		byte[][] ts = new byte[serverEngine.numberOfClientFriends][];
		BigInteger Rs = serverEngine.client_round_1(s, ts); // done
		long ts1e = System.currentTimeMillis();

		// ROUND 2 ----------------------------------------------------------


		long ts2s = System.currentTimeMillis();
		BigInteger[] a_tick = new BigInteger[serverEngine.numberOfServerFriends];
		serverEngine.client_round_2(a, Rs, a_tick); // done
		long ts2e = System.currentTimeMillis();

		// TODO: send a_tick and ts to server


		// ROUND 3 ----------------------------------------------------------
		long tc3s = System.currentTimeMillis();
		int found = serverEngine.server_round_3(a_tick, ts, Rc_inv);
		long tc3e = System.currentTimeMillis();


		System.out.println("Size of intersection: " + found);

		System.out.println("Server round 1: " + (tc1e - tc1s) + " ms");
		System.out.println("Client round 1: " + (ts1e - ts1s) + " ms");
		System.out.println("Server round 2: " + (tc2e - tc2s) + " ms");
		System.out.println("Client round 2: " + (ts2e - ts2s) + " ms");
		System.out.println("Server round 3: " + (tc3e - tc3s) + " ms");

		// System.out.println("Total time: "+(Math.max(tc1e-tc1s,ts1e-ts1s)+Math.max(tc2e-tc2s,ts2e-ts2s)+(tc3e-tc1s))+" ms");

		System.out.println("Total time: "
				+ (Math.max(tc1e - tc1s, ts1e - ts1s)
						+ Math.max(tc2e - tc2s, ts2e - ts2s) + (tc3e - tc3s))
				+ " ms");

	}

	public static void doComputation() {
		String[] serverIds;
		String[] clientIds;

		serverIds = generateIDs(32);
		clientIds = generateIDs(32);
		perform(serverIds, clientIds);
		serverIds = generateIDs(64);
		clientIds = generateIDs(64);
		perform(serverIds, clientIds);
		serverIds = generateIDs(128);
		clientIds = generateIDs(128);
		perform(serverIds, clientIds);
		serverIds = generateIDs(256);
		clientIds = generateIDs(256);
		perform(serverIds, clientIds);
	}

	private static String[] generateIDs(int friendsCount) {
		String[] ids = new String[friendsCount];
		for (int i = 0; i < friendsCount; i++) {
			ids[i] = String.valueOf(i);
		}
		return ids;
	}
}
