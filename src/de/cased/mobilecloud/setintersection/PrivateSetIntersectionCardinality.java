/**
 * This is an implementation of a protocol for private set intersection cardinality.
 * Client's input is a list c of v elements and Server's input is a list s of w elements.
 * Client obtains as output |c \cap v| and Server obtains no output.
 *
 * It implements the protocol in Fig. 1 of http://eprint.iacr.org/2011/141
 *
 * (c) 2012 by Thomas Schneider <thomas.schneider@ec-spride.de>
 */

package de.cased.mobilecloud.setintersection;

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
			int delta = a[i] - b[i]; // OK since bytes are smaller than ints.
			if (delta != 0) {
				return delta;
			}
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
	int v, w;

	private void init(int v, int w) {
		this.v = v;
		this.w = w;
		try {
			rnd = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Error: SHA1PRNG not found." + e);
		}

		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (Exception e) {
			System.out.println("Error: SHA-1 not found." + e);
		}

		p = new BigInteger(
				"B10B8F96A080E01DDE92DE5EAE5D54EC52C99FBCFB06A3C69A6A9DCA52D23B616073E28675A23D189838EF1E2EE652C013ECB4AEA906112324975C3CD49B83BFACCBDD7D90C4BD7098488E9C219A73724EFFD6FAE5644738FAA31A4FF55BCCC0A151AF5F0DC8B4BD45BF37DF365C1A65E68CFDA76D4DA708DF1FB2BC2E4A4371",
				16);
		g = new BigInteger(
				"A4D1CBD5C3FD34126765A442EFB99905F8104DD258AC507FD6406CFF14266D31266FEA1E5C41564B777E690F5504F213160217B4B01B886A5E91547F9E2749F4D7FBD7D3B9A92EE1909D0D2263F80A76A6A24C087A091F531DBF0A0169B6A28AD662A4D18E73AFA32D779D5918D08BC8858F4DCEF97C2A24855E6EEB22B3B2E5",
				16);
		q = new BigInteger("F518AA8781A8DF278ABA4E7D64B7CB9D49462353", 16);

		/*
		 * private static final int certainty = 80;
		 * assert(p.isProbablePrime(certainty));
		 * assert(q.isProbablePrime(certainty));
		 * assert(g.modPow(q,p).equals(BigInteger.ONE));
		 */
	}

	private BigInteger H(byte[] v) { // Full Domain Hash
		md.reset();
		md.update(v);
		byte[] hash = md.digest();
		BigInteger hv = new BigInteger(1, hash);
		BigInteger res = g.modPow(hv, p);
		return res;
	}

	private byte[] Hprime(BigInteger v) {
		byte[] vb = v.toByteArray();
		md.reset();
		md.update(vb);
		byte[] digest = md.digest();
		byte[] buf = new byte[160 / 8];
		System.arraycopy(digest, 0, buf, 0, 160 / 8);
		return buf;
	}

	BigInteger client_round_1(byte[][] c, BigInteger[] out) {
		// choose random Rc
		BigInteger Rc;
		do {
			Rc = new BigInteger(qLength, rnd);
		} while (Rc.compareTo(q) >= 0);

		for (int i = 0; i < v; i++) {
			out[i] = H(c[i]).modPow(Rc, p);
		}
		return Rc;
	}

	void permute(BigInteger[] a) { // permutes a in place
		List<BigInteger> list = new ArrayList<BigInteger>(a.length);
		for (int i = 0; i < a.length; i++)
			list.add(a[i]);
		java.util.Collections.shuffle(list);
		for (int i = 0; i < a.length; i++)
			a[i] = list.get(i);
	}

	void server_round_1(BigInteger[] a, byte[][] s, BigInteger[] a_tick,
			byte[][] ts) {
		// choose random Rs
		BigInteger Rs;
		do {
			Rs = new BigInteger(qLength, rnd);
		} while (Rs.compareTo(q) >= 0);

		for (int i = 0; i < v; i++) {
			a_tick[i] = a[i].modPow(Rs, p);
		}
		permute(a_tick);

		// permute(s); // instead of permuting s we sort ts in the very end
		for (int i = 0; i < w; i++) {
			ts[i] = Hprime(H(s[i]).modPow(Rs, p));
		}
		java.util.Arrays.sort(ts, new ByteComparator());
	}

	int client_round_2(BigInteger[] a_tick, byte[][] ts, BigInteger Rc) {
		BigInteger Rc_inv = Rc.modInverse(q);

		byte[][] tc = new byte[v][];
		for (int i = 0; i < v; i++) {
			tc[i] = Hprime(a_tick[i].modPow(Rc_inv, p));
		}

		ByteComparator myComparator = new ByteComparator();
		java.util.Arrays.sort(tc, myComparator);

		// count how many elements are in common
		int i = 0;
		int j = 0;
		int found = 0;
		while (i < v & j < w) {
			int cmp = myComparator.compare(tc[i], ts[j]);
			if (cmp == 0) {
				i += 1;
				j += 1;
				found++;
			} else if (cmp < 0) {
				i += 1;
			} else {
				j += 1;
			}
		}
		return found;
	}

	public static void doComputation() {
		int v = 100; // number of inputs provided by client
		int w = 100; // number of inputs provided by server

		byte[][] c = new byte[v][]; // Client's inputs
		byte[][] s = new byte[w][]; // Server's inputs

		// initialize inputs
		for (int i = 0; i < v; i++) {
			c[i] = new BigInteger(String.valueOf(i)).toByteArray();
		}
		for (int i = 0; i < w; i++) {
			s[i] = new BigInteger(String.valueOf(i)).toByteArray();
		}

		PrivateSetIntersectionCardinality myClass = new PrivateSetIntersectionCardinality();
		myClass.init(v, w);

		long t1s = System.currentTimeMillis();
		BigInteger[] a = new BigInteger[v];
		BigInteger Rc = myClass.client_round_1(c, a);
		long t1e = System.currentTimeMillis();

		// TODO: send a to server

		long t2s = System.currentTimeMillis();
		BigInteger[] a_tick = new BigInteger[v];
		byte[][] ts = new byte[w][];
		myClass.server_round_1(a, s, a_tick, ts);
		long t2e = System.currentTimeMillis();

		// TODO: send a_tick and ts to client

		long t3s = System.currentTimeMillis();
		int found = myClass.client_round_2(a_tick, ts, Rc);
		long t3e = System.currentTimeMillis();

		System.out.println("Size of intersection: " + found);

		System.out.println("Client round 1: " + (t1e - t1s) + " ms");
		System.out.println("Server round 1: " + (t2e - t2s) + " ms");
		System.out.println("Client round 2: " + (t3e - t3s) + " ms");
		System.out.println("Total time: " + (t3e - t1s) + " ms");
	}

	public static void main(String[] args) {
		doComputation();
	}
}