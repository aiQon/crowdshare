package de.cased.mobilecloud.fof;

import android.os.Parcel;
import android.os.Parcelable;

public class ServerFinalStepContainer implements Parcelable {


	private boolean directFriend;
	private int commonFriends;

	public static final Parcelable.Creator<ServerFinalStepContainer> CREATOR = new Parcelable.Creator<ServerFinalStepContainer>() {
		@Override
		public ServerFinalStepContainer createFromParcel(Parcel source) {
			return new ServerFinalStepContainer(source);
		}

		@Override
		public ServerFinalStepContainer[] newArray(int size) {
			return new ServerFinalStepContainer[size];
		}
	};

	public ServerFinalStepContainer(boolean directFriend, int commonFriends) {
		this.directFriend = directFriend;
		this.commonFriends = commonFriends;
	}

	public boolean isDirectFriend() {
		return directFriend;
	}

	public int getCommonFriends() {
		return commonFriends;
	}

	@SuppressWarnings("null")
	private ServerFinalStepContainer(Parcel source) {
		boolean[] tempHolder = null;
		source.readBooleanArray(tempHolder);
		directFriend = tempHolder[0];
		commonFriends = source.readInt();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeBooleanArray(new boolean[] { directFriend });
		dest.writeInt(commonFriends);
	}

}
