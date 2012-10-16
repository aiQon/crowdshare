package de.cased.mobilecloud;

import ext.org.bouncycastle.asn1.ASN1EncodableVector;
import ext.org.bouncycastle.asn1.ASN1Object;
import ext.org.bouncycastle.asn1.ASN1Primitive;
import ext.org.bouncycastle.asn1.ASN1Sequence;
import ext.org.bouncycastle.asn1.DERBitString;
import ext.org.bouncycastle.asn1.DERSequence;
import ext.org.bouncycastle.asn1.x509.AlgorithmIdentifier;

public class ResourceRequestASN1 extends ASN1Object {

	protected ResourceRequestInfo reqInfo = null;
	protected AlgorithmIdentifier sigAlgId = null;
	protected DERBitString sigBits = null;

	public static ResourceRequestASN1 getInstance(Object o) {
		if (o instanceof ResourceRequestASN1) {
			return (ResourceRequestASN1) o;
		}

		if (o != null) {
			return new ResourceRequestASN1(ASN1Sequence.getInstance(o));
		}

		return null;
	}

	protected ResourceRequestASN1() {
	}

	public ResourceRequestASN1(ResourceRequestInfo requestInfo,
			AlgorithmIdentifier algorithm, DERBitString signature) {
		this.reqInfo = requestInfo;
		this.sigAlgId = algorithm;
		this.sigBits = signature;
	}

	public ResourceRequestASN1(ASN1Sequence seq) {
		reqInfo = ResourceRequestInfo.getInstance(seq.getObjectAt(0));
		sigAlgId = AlgorithmIdentifier.getInstance(seq.getObjectAt(1));
		sigBits = (DERBitString) seq.getObjectAt(2);
	}

	public ResourceRequestInfo getResourceRequestInfo() {
		return reqInfo;
	}

	public AlgorithmIdentifier getSignatureAlgorithm() {
		return sigAlgId;
	}

	public DERBitString getSignature() {
		return sigBits;
	}

	@Override
	public ASN1Primitive toASN1Primitive() {
		ASN1EncodableVector v = new ASN1EncodableVector();

		v.add(reqInfo);
		v.add(sigAlgId);
		v.add(sigBits);

		return new DERSequence(v);
	}


}
