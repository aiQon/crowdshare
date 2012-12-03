package de.cased.mobilecloud.fof;

import java.math.BigInteger;

import android.os.Parcel;
import android.os.Parcelable;

public class ClientStepContainer implements Parcelable {

	byte[][] ts;
	BigInteger me; //A_TICK
	BigInteger[] friends; // a_tick

	public ClientStepContainer(byte[][] ts, BigInteger me, BigInteger[] friends) {
		this.ts = ts;
		this.me = me;
		this.friends = friends;
	}

	public static final Parcelable.Creator<ClientStepContainer> CREATOR = new Parcelable.Creator<ClientStepContainer>() {
		@Override
		public ClientStepContainer createFromParcel(Parcel source) {
			return new ClientStepContainer(source);
		}

		@Override
		public ClientStepContainer[] newArray(int size) {
			return new ClientStepContainer[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	public ClientStepContainer(Parcel source) {
		// read TS
		int tsElements = source.readInt();
		ts = new byte[tsElements][];
		for (int i = 0; i < tsElements; i++) {
			source.readByteArray(ts[i]);
		}

		// read me
		byte[] meAsByteArray = null;
		source.readByteArray(meAsByteArray);
		me = new BigInteger(meAsByteArray);

		// read friends
		int friendSize = source.readInt();
		friends = new BigInteger[friendSize];
		byte[] friend = null;
		for (int i = 0; i < friendSize; i++) {
			source.readByteArray(friend);
			friends[i] = new BigInteger(friend);
		}
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// write TS
		int tsElements = ts.length;
		dest.writeInt(tsElements);
		for (int i = 0; i < tsElements; i++) {
			dest.writeByteArray(ts[i]);
		}

		// write me
		dest.writeByteArray(me.toByteArray());

		// write friends
		int friendsElements = friends.length;
		dest.writeInt(friendsElements);
		for (BigInteger friend : friends) {
			dest.writeByteArray(friend.toByteArray());
		}

	}

	public byte[][] getTs() {
		return ts;
	}

	public BigInteger getMe() {
		return me;
	}

	public BigInteger[] getFriends() {
		return friends;
	}

}
