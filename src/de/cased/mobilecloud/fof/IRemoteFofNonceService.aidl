// IRemoteFofService.aidl
package de.cased.mobilecloud.fof;

// Declare any non-default types here with import statements

import de.cased.mobilecloud.fof.ServerInitialStepContainer;
import de.cased.mobilecloud.fof.ClientStepContainer;
import de.cased.mobilecloud.fof.ServerFinalStepContainer;

/** Example service interface */
interface IRemoteFofNonceService {
	
	/**
	 * Initializes the Friend-of-Friend Engine. Does not need to be called for
	 * registration or updates.
	 *
	 * @return Whether successful.
	 */
	boolean initEngine(boolean nonces);
	
	/**
	 * Performs the server step of De Christopharo's Private Set Intersection
	 * Algorithm.
	 *
	 * @return Encrypted information about the host and his friends. Not
	 *         possible to decipher at receiver.
	 */
	ServerInitialStepContainer serverInitialStep();
	
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
	ClientStepContainer clientStep(in ServerInitialStepContainer serverParams);
	
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
	ServerFinalStepContainer finalServerStep(in ClientStepContainer clientParams);

}