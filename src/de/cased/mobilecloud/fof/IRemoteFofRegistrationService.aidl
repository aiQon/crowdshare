package de.cased.mobilecloud.fof;

interface IRemoteFofRegistrationService {

	/**
	 * Clears old nonces if available and possible, generates a new nonce, posts
	 * it on the user's wall and stores it locally.
	 *
	 * @param authToken
	 *            The Facebook token reqired to access the own stream.
	 * @param appId
	 *            The appId on which behalf to post.
	 * @return Whether the operation was successful.
	 */
	boolean register(String authToken, String appId);
	
	/**
	 * Gathers nonces from Facebook friends walls' and stores them locally.
	 *
	 * @param appId
	 *            The appId which should be looked for as the poster of the
	 *            nonce.
	 * @return Whether the operation was successsful.
	 */
	boolean update(String appId);

}