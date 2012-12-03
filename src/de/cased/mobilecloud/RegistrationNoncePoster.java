package de.cased.mobilecloud;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.facebook.android.Facebook;

import de.cased.mobilecloud.facebook.SessionStore;

public class RegistrationNoncePoster {

	private Facebook facebookHandle;
	private static String TAG = "RegistrationNoncePoster";

	private static String crowdAppID = "360870950666152";

	public RegistrationNoncePoster(String appId, Context context)
			throws FacebookAuthenticationException {
		facebookHandle = new Facebook(appId);
		if (SessionStore.restore(facebookHandle, context)) {
			Log.d(TAG, "FB session restored successfully");
		} else {
			Log.d(TAG, "restored FB session is invalid");
			if (facebookHandle.extendAccessTokenIfNeeded(context, null)) {
				Log.d(TAG, "successfully refreshed facebook token");
			} else {
				Log.d(TAG, "failed refreshing facebook access token, giving up");
				throw new FacebookAuthenticationException();
			}
		}
	}

	public String postNonce() {
		try {
			String previousNonce = findCrowdAppNonceOnWall();
			if(previousNonce != null){
				deleteWallPost(previousNonce);
				Log.d(TAG, "previousNonce is " + previousNonce);
			} else {
				Log.d(TAG, "there is no previousNonce");
			}
			return publishRandomToken();

		} catch (MalformedURLException e) {
			Log.d(TAG, e.getMessage());
		} catch (IOException e) {
			Log.d(TAG, e.getMessage());
		} catch (JSONException e) {
			Log.d(TAG, e.getMessage());
		}
		return null;
	}


	private String findCrowdAppNonceOnWall() throws MalformedURLException,
			IOException, JSONException {
		String fqlQuery = "SELECT post_id,message, app_id FROM stream WHERE source_id = me() AND app_id = '"
				+ crowdAppID + "' LIMIT 1";

		Bundle params = new Bundle();
		params.putString("method", "fql.query");
		params.putString("query", fqlQuery);
		String response = facebookHandle.request(params);
		response = "{\"data\":" + response + "}";

		JSONObject json = new JSONObject(response);
		JSONArray data = json.getJSONArray("data");
		if (data.length() == 1) {
			String postId = data.getJSONObject(0).getString("post_id");
			Log.d(TAG, "got post-id:" + postId);
			return postId;
		} else {
			Log.d(TAG, "got no post-id");
			return null;
		}

	}

	private void deleteWallPost(String id) {
		try {
			Bundle param = new Bundle();
			param.putString("method", "delete");
			facebookHandle.request(id, param, "POST");
			Log.d(TAG, "send command to delete wall post");
		} catch (Exception ex) {
			Log.d(TAG, "Error while deleting wall post");
			ex.printStackTrace();
		}
	}

	private String publishRandomToken() throws FileNotFoundException,
			MalformedURLException, IOException {

		/*
		 *
		 * fql?q=SELECT message, app_id FROM stream WHERE source_id IN(SELECT
		 * uid1 FROM friend WHERE uid2=me()) AND app_id = '360870950666152'
		 * LIMIT 1000
		 */

		String nonce = NonceGenerator.newNonce();
		Log.d(TAG, "got nonce:" + nonce);
		Bundle params = new Bundle();
		params.putString("message", nonce);

		facebookHandle.request("me/feed", params, "POST");
		Log.d(TAG, "sent nonce to FB");
		return nonce;
	}

	// class DontCareListener implements RequestListener {
	// @Override
	// public void onComplete(String response, Object state) {
	// }
	//
	// @Override
	// public void onIOException(IOException e, Object state) {
	// }
	//
	// @Override
	// public void onFileNotFoundException(FileNotFoundException e,
	// Object state) {
	// }
	//
	// @Override
	// public void onMalformedURLException(MalformedURLException e,
	// Object state) {
	// }
	//
	// @Override
	// public void onFacebookError(FacebookError e, Object state) {
	// }
	// }
}
