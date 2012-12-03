package de.cased.mobilecloud.fof;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;

import de.cased.mobilecloud.FacebookAuthenticationException;
import de.cased.mobilecloud.NonceEntries;
import de.cased.mobilecloud.RegistrationNoncePoster;
import de.cased.mobilecloud.facebook.SessionStore;

public class FofNonceRegistrationEngine extends
		IRemoteFofRegistrationService.Stub {

	private Context context;
	private String persistOwnLocation;
	private String persistedOtherNonceLocation;

	private static String TAG = "FofNonceRegistrationEngine";

	public FofNonceRegistrationEngine(Context context,
			String persistOwnLocation, String persistedOtherNonceLocation) {
		this.context = context;
		this.persistOwnLocation = persistOwnLocation;
		this.persistedOtherNonceLocation = persistedOtherNonceLocation;
	}

	@Override
	public IBinder asBinder() {
		// TODO Auto-generated method stub
		return this;
	}

	/**
	 * Clears old nonces if available and possible, generates a new nonce, posts
	 * it on the user's wall and stores it locally.
	 *
	 * @param appId
	 *            The appId on which behalf to post.
	 * @param persistLocation
	 *            The location where the fresh nonce has to be stored.
	 * @return
	 */
	@Override
	public boolean register(String appId)
			throws RemoteException {
		try {
			RegistrationNoncePoster poster = new RegistrationNoncePoster(appId,
					context);
			String nonce = poster.postNonce();
			persistNonce(nonce, persistOwnLocation, context);
		} catch (FacebookAuthenticationException fbException) {
			return false;
		}
		return true;
	}

	/**
	 * Gathers nonces from Facebook friends walls' and stores them locally.
	 *
	 * @param appId
	 *            The appId which should be looked for as the poster of the
	 *            nonce.
	 * @param nonceLocation
	 *            The location where the retrieved nonces are to be stored.
	 * @return
	 */
	@Override
	public boolean update(String appId) throws RemoteException {
		Log.d(TAG, "reached retrieveFriendsNonce");
		String fqlQuery = "SELECT message, created_time, actor_id FROM stream WHERE source_id IN(SELECT uid1 FROM friend WHERE uid2=me()) AND app_id = '"
				+ appId + "' LIMIT 10000";

		Facebook facebookHandle;
		try {
			facebookHandle = createValidFacebookContext(appId);
		} catch (FacebookAuthenticationException e1) {
			Log.d(TAG, e1.getMessage());
			return false;
		}
		AsyncFacebookRunner mAsyncFacebookRunner = new AsyncFacebookRunner(
				facebookHandle);
		Bundle params = new Bundle();
		params.putString("method", "fql.query");
		params.putString("query", fqlQuery);
		Log.d(TAG, "directly querying facebook with:" + fqlQuery);
		mAsyncFacebookRunner.request(null, params, new UpdateCallback(
				persistedOtherNonceLocation), 1337);

		return true;
	}

	private Facebook createValidFacebookContext(String appId)
			throws FacebookAuthenticationException {
		Facebook facebookHandle = new Facebook(appId);
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
		return facebookHandle;
	}

	private void persistNonceInfo(ArrayList<NonceEntries> entries,
			String nonceLocation) {
		try {
			Log.d(TAG, "persisting nonces to " + nonceLocation);
			BufferedOutputStream bos = new BufferedOutputStream(
					context.openFileOutput(nonceLocation, Context.MODE_PRIVATE));
			for (NonceEntries nonceEntries : entries) {
				bos.write(nonceEntries.getMessage().getBytes());
				bos.write('\n');
			}
			bos.flush();
			bos.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void deleteNonceList(String nonceLocation) {
		context.deleteFile(nonceLocation);
	}

	private void addEntry(String message, long time, String actor,
			ArrayList<NonceEntries> entries) {
		Log.d(TAG, "adding Entry to nonce list");
		for (int i = 0; i < entries.size(); i++) {
			Log.d(TAG, "check if actor " + entries.get(i).getActor_id()
					+ " with created time " + entries.get(i).getCreated_time()
					+ " is older than " + time);
			if (entries.get(i).getActor_id().equals(actor)) {
				if (entries.get(i).getCreated_time() < time) {
					Log.d(TAG, "yep existing entry is older, removing it again");
					entries.remove(i);
					addEntryWithoutCheck(message, time, actor, entries);
					return;
				} else {
					Log.d(TAG, "nope, its younger, we return here");
					return;
				}
			}
		}
		addEntryWithoutCheck(message, time, actor, entries);
	}

	private void addEntryWithoutCheck(String message, long time, String actor,
			ArrayList<NonceEntries> entries) {
		Log.d(TAG, "adding without check actor " + actor + " message "
				+ message + " with creat time " + time);
		NonceEntries entry = new NonceEntries();
		entry.setActor_id(actor);
		entry.setCreated_time(time);
		entry.setMessage(message);
		entries.add(entry);
	}

	private void persistNonce(String nonce, String persistLocation, Context con) {
		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					con.openFileOutput(persistLocation, Context.MODE_PRIVATE));
			bos.write(nonce.getBytes());
			bos.write('\n');
			bos.flush();
			bos.close();
			Log.d(TAG, "persisted me to " + persistLocation);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private final class UpdateCallback implements RequestListener {
		private final String nonceLocation;

		private UpdateCallback(String nonceLocation) {
			this.nonceLocation = nonceLocation;
		}

		@Override
		public void onComplete(String response, Object state) {
			response = "{data:" + response + "}";
			Log.d(TAG, "response to nonce request came in:" + response);

			try {
				JSONObject json = new JSONObject(response);
				JSONArray data = json.getJSONArray("data");
				ArrayList<NonceEntries> entries = new ArrayList<NonceEntries>();
				if (data.length() > 0) {
					for (int i = 0; i < data.length(); i++) {
						String message = data.getJSONObject(i).getString(
								"message");
						long created_time = Long.parseLong(data
								.getJSONObject(i).getString("created_time"));
						String actor_id = data.getJSONObject(i).getString(
								"actor_id");
						addEntry(message, created_time, actor_id, entries);
						Log.d(TAG, "added actor " + actor_id + " with message "
								+ message);
					}
					deleteNonceList(nonceLocation);
					persistNonceInfo(entries, nonceLocation);
				} else {
					Log.d(TAG, "got no responses");
				}
			} catch (Exception e) {
				Log.d(TAG, "failed parsing json");
				e.printStackTrace();
			}
		}

		@Override
		public void onIOException(IOException e, Object state) {

		}

		@Override
		public void onFileNotFoundException(FileNotFoundException e,
				Object state) {

		}

		@Override
		public void onMalformedURLException(MalformedURLException e,
				Object state) {

		}

		@Override
		public void onFacebookError(FacebookError e, Object state) {
			Log.d(TAG, "Facebook error: " + e.getMessage());
		}
	}

}
