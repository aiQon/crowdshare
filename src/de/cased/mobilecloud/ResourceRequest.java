package de.cased.mobilecloud;

import java.io.IOException;
import java.io.OutputStream;

import ext.org.bouncycastle.asn1.ASN1Encoding;
import ext.org.bouncycastle.asn1.ASN1Primitive;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import ext.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import ext.org.bouncycastle.operator.ContentVerifier;
import ext.org.bouncycastle.operator.ContentVerifierProvider;
import ext.org.bouncycastle.pkcs.PKCSException;
import ext.org.bouncycastle.pkcs.PKCSIOException;

public class ResourceRequest {

	private ResourceRequestASN1 rrAsn1;

	private static ResourceRequestASN1 parseBytes(byte[] encoding)
			throws IOException {
		try {
			return ResourceRequestASN1.getInstance(ASN1Primitive
					.fromByteArray(encoding));
		} catch (ClassCastException e) {
			e.printStackTrace();
			throw new PKCSIOException("malformed data: " + e.getMessage(), e);
		} catch (IllegalArgumentException e) {
			throw new PKCSIOException("malformed data: " + e.getMessage(), e);
		}
	}

	/**
	 * Create a ResourceRequest from an underlying ASN.1 structure.
	 *
	 * @param ResourceRequest
	 *            the underlying ASN.1 structure representing a request.
	 */
	public ResourceRequest(ResourceRequestASN1 rrAsn1) {
		this.rrAsn1 = rrAsn1;
	}

	/**
	 * Create a ResourceRequest from the passed in bytes.
	 *
	 * @param encoded
	 *            BER/DER encoding of the ResourceRequest structure.
	 * @throws IOException
	 *             in the event of corrupted data, or an incorrect structure.
	 */
	public ResourceRequest(byte[] encoded) throws IOException {
		this(parseBytes(encoded));
	}

	/**
	 * Return the underlying ASN.1 structure for this request.
	 *
	 * @return a CertificateRequest object.
	 */
	public ResourceRequestASN1 toASN1Structure() {
		return rrAsn1;
	}

	/**
	 * Return the subject on this request.
	 *
	 * @return the X500Name representing the request's subject.
	 */
	public X500Name getSubject() {
		return X500Name.getInstance(rrAsn1.getResourceRequestInfo()
				.getSubject());
	}

	/**
	 * Return the port on this request.
	 *
	 * @return the port which is requested
	 */
	public int getPort() {
		return rrAsn1.getResourceRequestInfo().getDestinationPort().getValue()
				.intValue();
	}

	/**
	 * Return the transport layer on this request.
	 *
	 * @return the port which is requested
	 */
	public int getTransportLayer() {
		return rrAsn1.getResourceRequestInfo().getTransportLayer().getValue()
				.intValue();
	}

	/**
	 * Return the ip address on this request as a 32bit int.
	 *
	 * @return the ip address in 32bit representation
	 */
	public int getIP() {
		return rrAsn1.getResourceRequestInfo().getIpAddress().getValue()
				.intValue();
	}

	/**
	 * Return the details of the signature algorithm used to create this
	 * request.
	 *
	 * @return the AlgorithmIdentifier describing the signature algorithm used
	 *         to create this request.
	 */
	public AlgorithmIdentifier getSignatureAlgorithm() {
		return rrAsn1.getSignatureAlgorithm();
	}

	/**
	 * Return the bytes making up the signature associated with this request.
	 *
	 * @return the request signature bytes.
	 */
	public byte[] getSignature() {
		return rrAsn1.getSignature().getBytes();
	}

	/**
	 * Return the SubjectPublicKeyInfo describing the public key this request is
	 * carrying.
	 *
	 * @return the public key ASN.1 structure contained in the request.
	 */
	public SubjectPublicKeyInfo getSubjectPublicKeyInfo() {
		return rrAsn1.getResourceRequestInfo().getSubjectPublicKeyInfo();
	}

	public byte[] getEncoded() throws IOException {
		return rrAsn1.getEncoded();
	}

	public byte[] getEncoded(String type) throws IOException {
		return rrAsn1.getEncoded(type);
	}

	/**
	 * Validate the signature on the ResourceRequestAsn1 request in this holder.
	 *
	 * @param verifierProvider
	 *            a ContentVerifierProvider that can generate a verifier for the
	 *            signature.
	 * @return true if the signature is valid, false otherwise.
	 * @throws PKCSException
	 *             if the signature cannot be processed or is inappropriate.
	 */
	public boolean isSignatureValid(ContentVerifierProvider verifierProvider)
			throws PKCSException {
		ResourceRequestInfo requestInfo = rrAsn1.getResourceRequestInfo();

		ContentVerifier verifier;

		try {
			verifier = verifierProvider.get(rrAsn1
					.getSignatureAlgorithm());

			OutputStream sOut = verifier.getOutputStream();

			sOut.write(requestInfo.getEncoded(ASN1Encoding.DER));

			sOut.close();
		} catch (Exception e) {
			throw new PKCSException("unable to process signature: "
					+ e.getMessage(), e);
		}

		return verifier.verify(rrAsn1.getSignature().getBytes());
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof ResourceRequest)) {
			return false;
		}

		ResourceRequest other = (ResourceRequest) o;

		return this.toASN1Structure().equals(other.toASN1Structure());
	}

	@Override
	public int hashCode() {
		return this.toASN1Structure().hashCode();
	}

}
