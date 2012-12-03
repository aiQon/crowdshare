package de.cased.mobilecloud.fof;

import java.math.BigInteger;

import android.os.Parcel;
import android.os.Parcelable;

public class ServerInitialStepContainer implements Parcelable {

	private BigInteger me;
	private BigInteger friends[];


	public ServerInitialStepContainer(BigInteger me, BigInteger[] friends) {
		this.me = me;
		this.friends = friends;
	}

	public static final Parcelable.Creator<ServerInitialStepContainer> CREATOR= new
			Parcelable.Creator<ServerInitialStepContainer>() {

		@Override
		public ServerInitialStepContainer createFromParcel(Parcel source) {
			return new ServerInitialStepContainer(source);
		}

		@Override
		public ServerInitialStepContainer[] newArray(int size) {
			return new ServerInitialStepContainer[size];
		}

	};

	private ServerInitialStepContainer(Parcel source) {
		byte[] meByte = null;
		source.readByteArray(meByte);
		me = new BigInteger(meByte);

		int friendsSize = source.readInt();
		friends = new BigInteger[friendsSize];
		byte[] friend = null;

		for (int i = 0; i < friendsSize; i++) {
			source.readByteArray(friend);
			friends[i] = new BigInteger(friend);
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeByteArray(me.toByteArray());
		dest.writeInt(friends.length);
		for (BigInteger friend : friends) {
			dest.writeByteArray(friend.toByteArray());
		}
	}

	public BigInteger getMe() {
		return me;
	}

	public BigInteger[] getFriends() {
		return friends;
	}
}
