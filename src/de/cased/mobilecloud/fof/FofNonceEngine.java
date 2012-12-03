package de.cased.mobilecloud.fof;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.IBinder;
import de.cased.mobilecloud.Utilities;
import de.cased.mobilecloud.setintersection.PrivateSetIntersectionCardinality;

/**
 * This class represents exactly one run of the Private Set Intersection
 * Cardinality Algorithm of De Christofaro. To perform an additional run, get a
 * new instance. One instance can only be used as a server or as a client but
 * not as both. Meaning: If used as a server (provider) then only use the method
 * serverInitialStep. If used as a client then only use the method clientStep.
 *
 * @author stas
 *
 */
public class FofNonceEngine implements IRemoteFofNonceService {

	private static String TAG = "FofEngine";

	private Context context;
	private String meLocation;
	private String nonceLocation;
	private String me;
	private List<String> friends;
	private PrivateSetIntersectionCardinality intersection;

	private BigInteger Rc_inv;
	private byte[][] ts;
	private BigInteger Rs;


	public FofNonceEngine(Context context, String meLocation, String nonceLocation) {
		this.context = context;
		this.meLocation = meLocation;
		this.nonceLocation = nonceLocation;
	}

	/**
	 * Initializes the Friend-of-Friend Engine. Does not need to be called for
	 * registration or updates.
	 *
	 * @return Whether successful.
	 */
	@Override
	public boolean initEngine() {
		me = loadNonceMe(meLocation);
		friends = loadLocalNonces(nonceLocation);
		intersection = new PrivateSetIntersectionCardinality();
		return me != null && !me.equals("") && friends != null;
	}

	/**
	 * Performs the server step of De Christofaro's Private Set Intersection
	 * Algorithm.
	 *
	 * @return Encrypted information about the host and his friends. Not
	 *         possible to decipher at receiver.
	 */
	@Override
	public ServerInitialStepContainer serverInitialStep() {
		intersection.setNumberOfServerFriends(friends.size());

		BigInteger[] a = new BigInteger[friends.size()];
		byte[][] c = new byte[friends.size()][];
		byte[] C = new BigInteger(me, 16).toByteArray();
		BigInteger[] A = new BigInteger[1];

		for (int i = 0; i < friends.size(); i++) {
			BigInteger friendInt = new BigInteger(friends.get(i), 16);
			// Log.d(TAG, "added friend " + friends.get(i));
			c[i] = friendInt.toByteArray();
		}
		BigInteger Rc = intersection.server_round_1(c, a, C, A);

		Rc_inv = intersection.server_round_2(Rc);

		ServerInitialStepContainer container = new ServerInitialStepContainer(
				A[0], a);

		return container;
	}

	/**
	 * Performs the client step of De Christofaro's Private Set Intersection
	 * Algorithm.
	 *
	 * @param serverParams
	 *            The received server parameters.
	 * @return The edited server parameters and the own friends information
	 *         encrypted to not be read in clear text by any one else including
	 *         the server.
	 */
	@Override
	public ClientStepContainer clientStep(
			ServerInitialStepContainer serverParams) {
		loadLocalNonces(nonceLocation);
		if (hasFriends()) {
			initClientVariables(serverParams);
			computeRs();
			ClientStepContainer container = calculateClientResponse(serverParams);
			return container;
		} else {
			return null;
		}
	}


	/**
	 * Performs the last step in calculating the Private Set Intersection
	 * Algorithm of De Christofaro. Despite returning the cardinality only this
	 * method also returns an indicator if a direct relation is available.
	 *
	 * @param clientParams
	 *            The parameters received from the client.
	 * @return A container object with information about the cardinality of the
	 *         set intersection and an indicator of direct relation.
	 */
	@Override
	public ServerFinalStepContainer finalServerStep(
			ClientStepContainer clientParams) {
		intersection.setNumberOfClientFriends(clientParams.getFriends().length);
		Boolean[] FOUND = new Boolean[1];
		int commonFriends = intersection.server_round_3(
				clientParams.getFriends(),
				clientParams.getTs(), Rc_inv, clientParams.getMe(),
				FOUND);
		ServerFinalStepContainer container = new ServerFinalStepContainer(
				FOUND[0], commonFriends);
		return container;
	}

	private ClientStepContainer calculateClientResponse(
			ServerInitialStepContainer serverParams) {
		BigInteger[] a_tick = new BigInteger[serverParams.getFriends().length];
		BigInteger[] A_TICK = new BigInteger[1];
		intersection.client_round_2(serverParams.getFriends(), Rs, a_tick,
				serverParams.getMe(), A_TICK);
		ClientStepContainer container = new ClientStepContainer(ts,
				A_TICK[0], a_tick);
		return container;
	}

	private void computeRs() {
		byte[][] s = new byte[friends.size()][];

		for (int i = 0; i < friends.size(); i++) {
			BigInteger friendInt = new BigInteger(friends.get(i), 16);
			s[i] = friendInt.toByteArray();
		}
		Rs = intersection.client_round_1(s, ts);
	}

	private void initClientVariables(ServerInitialStepContainer params) {
		intersection = new PrivateSetIntersectionCardinality();
		intersection.setNumberOfClientFriends(friends.size());
		ts = new byte[friends.size()][];
		intersection.setNumberOfServerFriends(params.getFriends().length);
	}

	private boolean hasFriends() {
		return friends.size() > 0;
	}

	private String loadNonceMe(String location) {
		List<String> myInfo = Utilities.readFromFile(location, context);
		return myInfo.get(0);
	}

	private List<String> loadLocalNonces(String localfriends) {

		List<String> friends = new ArrayList<String>();
		List<String> friendList = Utilities.readFromFile(localfriends, context);
		for (String r1 : friendList) {
			if (r1 != null && !r1.equals("")) {
				friends.add(r1);
			}
		}
		return friends;
	}

	@Override
	public IBinder asBinder() {
		return (IBinder) this;
	}
}
