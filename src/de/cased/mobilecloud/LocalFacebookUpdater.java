package de.cased.mobilecloud;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;

import de.cased.mobilecloud.facebook.SessionStore;

public class LocalFacebookUpdater extends Thread {


	// private RuntimeConfiguration config;
	private Facebook facebookHandle;
	private Context context;
	private String friendlistLocation;
	private static String TAG = "LocalFacebookUpdater";

	private AsyncFacebookRunner mAsyncRunner;

	public LocalFacebookUpdater(String appId,
			Context context, String friendlistLocation) {

		Log.d(TAG, "starting LocalFBUpdater with appId:" + appId);

		// config = RuntimeConfiguration.getInstance();
		facebookHandle = new Facebook(appId);
		// facebookHandle.authorize(RuntimeConfiguration.getInstance()
		// .getHostActivity(),
		// new String[] { "read_friendlists" }, new DialogListener() {
		//
		// @Override
		// public void onComplete(Bundle values) {
		// Log.d(TAG, "DialogListener.onComplete()");
		// }
		//
		// @Override
		// public void onFacebookError(FacebookError e) {
		// Log.d(TAG, "DialogListener.onFacebookError()");
		// }
		//
		// @Override
		// public void onError(DialogError e) {
		// Log.d(TAG, "DialogListener.onError()");
		// }
		//
		// @Override
		// public void onCancel() {
		// Log.d(TAG, "DialogListener.onCancel()");
		// }
		//
		// });

		if (SessionStore.restore(facebookHandle, context)) {
			Log.d(TAG, "FB session restored successfully");
		} else {
			Log.d(TAG, "restored FB session is invalid");
			if (facebookHandle.extendAccessTokenIfNeeded(context, null)) {
				Log.d(TAG, "successfully refreshed facebook token");
			} else {
				Log.d(TAG, "failed refreshing, giving up");
			}
		}

		this.friendlistLocation = friendlistLocation;
		this.context = context;
		mAsyncRunner = new AsyncFacebookRunner(facebookHandle);
	}


	@Override
	public void run() {
		deleteFriendsList();
		getAndPersistFriends();

	}


	private void persistFriends(List<FacebookEntry> friends) {
		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					context.openFileOutput(friendlistLocation,
					Context.MODE_PRIVATE));
			for (FacebookEntry entry : friends) {
				bos.write(entry.toString().getBytes());
				bos.write('\n');
			}
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void deleteFriendsList() {
		context.deleteFile(friendlistLocation);
	}

	private void getAndPersistFriends() {
		final List<FacebookEntry> result = new ArrayList<FacebookEntry>();

		Log.d(TAG, "trying to request data from facebook with token: "
				+ facebookHandle.getAccessToken());
		Log.d(TAG,
				"which is valid to "
						+ new Date(facebookHandle.getAccessExpires()));


		mAsyncRunner.request("me/friends", new RequestListener() {

			@Override
			public void onMalformedURLException(MalformedURLException e,
					Object state) {
				Log.d(TAG, "onMalformedURLException");

			}

			@Override
			public void onIOException(IOException e, Object state) {
				Log.d(TAG, "onIOException");

			}

			@Override
			public void onFileNotFoundException(FileNotFoundException e,
					Object state) {
				Log.d(TAG, "onFileNotFoundException");

			}

			@Override
			public void onFacebookError(FacebookError e, Object state) {
				Log.d(TAG, "onFacebookError");

			}

			@Override
			public void onComplete(String response, Object state) {
				try {
					JSONObject main = new JSONObject(response);
					JSONArray friendsData = main.getJSONArray("data");
					for (int i = 0; i < friendsData.length(); i++) {
						String name = friendsData.getJSONObject(i)
								.getString("name").toString();

						String id = friendsData.getJSONObject(i)
								.getString("id").toString();
						result.add(new FacebookEntry(name, id));

						Log.d(TAG, "got friend:" + name + ":" + id);
					}
				} catch (JSONException e) {
					Log.e(TAG, e.getMessage(), e);
				}
				persistFriends(result);

			}
		});
	}
}
