package de.cased.mobilecloud;

import java.io.IOException;
import java.io.OutputStream;
import java.security.PublicKey;

import ext.org.bouncycastle.asn1.ASN1Encoding;
import ext.org.bouncycastle.asn1.ASN1Integer;
import ext.org.bouncycastle.asn1.ASN1OctetString;
import ext.org.bouncycastle.asn1.ASN1UTCTime;
import ext.org.bouncycastle.asn1.DERBitString;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import ext.org.bouncycastle.operator.ContentSigner;

/**
 * A class for creating Ressource requests.
 *
 * <pre>
 *  ResourceRequest ::= SEQUENCE {
 * 	tbsRequest TBSRequest ,
 * 	signatureValue BIT STRING
 * }
 *
 * TBSRequest ::= SEQUENCE {
 * 	serialNumber INTEGER ,
 * 	holderPublicKeyID SubjectPublicKeyInfo{{ PKInfoAlgorithms }},
 *  subject X500Name,
 * 	requestTime Time,
 * 	destinationIP OCTET STRING,
 * 	destinationURL OCTET STRING,
 * 	destinationPort INTEGER,
 * 	transportLayer OCTET STRING
 * }
 * </pre>
 */

public class ResourceRequestBuilder {
	private ASN1Integer serialNumber;
	private SubjectPublicKeyInfo publicKeyInfo;
	private X500Name subject;
	private ASN1UTCTime time;
	private ASN1Integer ipAddress;
	private ASN1Integer destinationPort;
	private ASN1Integer transportLayer;

	// private ResourceRequestInfo info

	public ResourceRequestBuilder(ASN1Integer serialNumber,
			SubjectPublicKeyInfo publicKeyInfo, X500Name subject,
			ASN1UTCTime time, ASN1Integer ipAddress,
			ASN1Integer destinationPort,
			ASN1Integer transportLayer) {

		this.serialNumber = serialNumber;
		this.publicKeyInfo = publicKeyInfo;
		this.subject = subject;
		this.time = time;
		this.ipAddress = ipAddress;
		this.destinationPort = destinationPort;
		this.transportLayer = transportLayer;

	}

	public ResourceRequestBuilder(ASN1Integer asn1Integer, PublicKey publicKey,
			X500Name x500Name, ASN1UTCTime asn1utcTime) {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Generate a resource request based on the past in signer.
	 *
	 * @param signer
	 *            the content signer to be used to generate the signature
	 *            validating the certificate.
	 * @return a holder containing the resulting resource request.
	 */
	public ResourceRequest build(ContentSigner signer) {
		ResourceRequestInfo info = new ResourceRequestInfo(serialNumber,
				publicKeyInfo, subject,
 time, ipAddress, destinationPort,
				transportLayer);
		try {
			OutputStream sOut = signer.getOutputStream();

			sOut.write(info.getEncoded(ASN1Encoding.DER));

			sOut.close();

			return new ResourceRequest(new ResourceRequestASN1(
					info, signer.getAlgorithmIdentifier(), new DERBitString(
							signer.getSignature())));
		} catch (IOException e) {
			throw new IllegalStateException(
					"cannot produce resource request signature");
		}
	}
}
