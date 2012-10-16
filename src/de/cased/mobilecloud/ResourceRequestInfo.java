package de.cased.mobilecloud;

import ext.org.bouncycastle.asn1.ASN1EncodableVector;
import ext.org.bouncycastle.asn1.ASN1Integer;
import ext.org.bouncycastle.asn1.ASN1Object;
import ext.org.bouncycastle.asn1.ASN1Primitive;
import ext.org.bouncycastle.asn1.ASN1Sequence;
import ext.org.bouncycastle.asn1.ASN1UTCTime;
import ext.org.bouncycastle.asn1.DERSequence;
import ext.org.bouncycastle.asn1.x500.X500Name;
import ext.org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;

public class ResourceRequestInfo extends ASN1Object {

	private ASN1Integer serialNumber;
	private SubjectPublicKeyInfo publicKeyInfo;
	private X500Name subject;
	private ASN1UTCTime time;
	private ASN1Integer ipAddress;
	private ASN1Integer destinationPort;
	private ASN1Integer transportLayer;


	public ResourceRequestInfo(ASN1Integer serialNumber,
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

		verifyEntries();
	}

	public ResourceRequestInfo(ASN1Sequence instance) {
		this.serialNumber = (ASN1Integer) instance.getObjectAt(0);
		this.publicKeyInfo = SubjectPublicKeyInfo.getInstance(instance
				.getObjectAt(1));
		this.subject = X500Name.getInstance(instance.getObjectAt(2));
		this.time = (ASN1UTCTime) instance.getObjectAt(3);
		this.ipAddress = (ASN1Integer) instance.getObjectAt(4);
		this.destinationPort = (ASN1Integer) instance.getObjectAt(5);
		this.transportLayer = (ASN1Integer) instance.getObjectAt(6);

		verifyEntries();
	}

	private void verifyEntries() {
		if ((serialNumber == null) || (publicKeyInfo == null)
				|| (subject == null) || (time == null) || (ipAddress == null)
				|| (destinationPort == null)
				|| (transportLayer == null)) {
			throw new IllegalArgumentException(
					"Not all fields set in ResourceRequestInfo generator.");
		}
	}

	public static ResourceRequestInfo getInstance(Object obj) {
		if (obj instanceof ResourceRequestInfo) {
			return (ResourceRequestInfo) obj;
		} else if (obj != null) {
			return new ResourceRequestInfo(ASN1Sequence.getInstance(obj));
		}

		return null;
	}

	@Override
	public ASN1Primitive toASN1Primitive() {
		ASN1EncodableVector v = new ASN1EncodableVector();

		v.add(serialNumber);
		v.add(publicKeyInfo);
		v.add(subject);
		v.add(time);
		v.add(ipAddress);
		v.add(destinationPort);
		v.add(transportLayer);

		return new DERSequence(v);
	}

	public ASN1Integer getSerialNumber() {
		return serialNumber;
	}

	public SubjectPublicKeyInfo getSubjectPublicKeyInfo() {
		return publicKeyInfo;
	}

	public X500Name getSubject() {
		return subject;
	}

	public ASN1UTCTime getTime() {
		return time;
	}

	public ASN1Integer getIpAddress() {
		return ipAddress;
	}

	public ASN1Integer getDestinationPort() {
		return destinationPort;
	}

	public ASN1Integer getTransportLayer() {
		return transportLayer;
	}

}
