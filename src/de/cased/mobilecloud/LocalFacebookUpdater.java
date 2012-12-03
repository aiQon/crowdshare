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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;

import de.cased.mobilecloud.facebook.SessionStore;
import de.cased.mobilecloud.fof.FofNonceRegistrationService;
import de.cased.mobilecloud.fof.IRemoteFofRegistrationService;

//ud.facebook.SessionStore;

public class LocalFacebookUpdater extends Thread {


	// private RuntimeConfiguration config;
	private Facebook facebookHandle;
	private Context context;
	private String appId;
	private String friendlistLocation;
	private String myInfoLocation;
	private String nonceLocation;
	private static String TAG = "LocalFacebookUpdater";
	IRemoteFofRegistrationService mIRemoteService;

	private AsyncFacebookRunner mAsyncRunner;

	// private static int friendInfo = 1;
	// private static int meInfo = 2;
	//
	// private int currentInfoStatus = 0;

	// private synchronized void registerInfo(int info) {
	// currentInfoStatus += info;
	// Log.d(TAG, "registerInfo raised to:" + currentInfoStatus);
	// }
	//
	// private boolean readyToPersist() {
	// return currentInfoStatus == 3 ? true : false;
	// }

	public LocalFacebookUpdater(String appId,
 Context context,
			String friendlistLocation, String myInfoLocation,
			String nonceLocation) {

		Log.d(TAG, "starting LocalFBUpdater with appId:" + appId);

		facebookHandle = new Facebook(appId);
		this.myInfoLocation = myInfoLocation;

		if (SessionStore.restore(facebookHandle, context)) {
			Log.d(TAG, "FB session restored successfully");
		} else {
			Log.d(TAG, "restored FB session is invalid");
			if (facebookHandle.extendAccessTokenIfNeeded(context, null)) {
				Log.d(TAG, "successfully refreshed facebook token");
			} else {
				Log.d(TAG, "failed refreshing facebook access token, giving up");
			}
		}

		this.friendlistLocation = friendlistLocation;
		this.context = context;
		this.appId = appId;
		this.nonceLocation = nonceLocation;
		mAsyncRunner = new AsyncFacebookRunner(facebookHandle);
		context.bindService(new Intent(context,
				FofNonceRegistrationService.class),
				mConnection, Context.BIND_AUTO_CREATE);
	}


	@Override
	public void run() {
		try {
			while (mIRemoteService == null) {
				synchronized (this) {
					wait();
				}
			}
			retrieveFriendsNonces();
		} catch (Exception somethingBroke) {
			Log.d(TAG, "somethingBroke");
			somethingBroke.printStackTrace();
		}
		deleteFriendsList();
		getAndPersistFriends();
	}


	private ServiceConnection mConnection = new ServiceConnection() {
		// Called when the connection with the service is established
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// Following the example above for an AIDL interface,
			// this gets an instance of the IRemoteInterface, which we can use
			// to call on the service
			mIRemoteService = IRemoteFofRegistrationService.Stub
					.asInterface(service);
			synchronized (LocalFacebookUpdater.this) {
				LocalFacebookUpdater.this.notifyAll();
			}
		}

		// Called when the connection with the service disconnects unexpectedly
		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.e(TAG, "Service has unexpectedly disconnected");
			mIRemoteService = null;
		}
	};


	private void addEntry(String message, long time, String actor, ArrayList<NonceEntries> entries){
		Log.d(TAG, "adding Entry to nonce list");
		for(int i = 0; i < entries.size(); i++){
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

	private void retrieveFriendsNonces() {
		// Log.d(TAG, "waiting for the registration service to be conected");
		// while (mIRemoteService == null) {
		// try {
		// Thread.sleep(100);
		// } catch (InterruptedException e) {
		// }
		// }
		//
		// Log.d(TAG, "registration service connected");

		try {
			mIRemoteService.update(appId);
			Log.d(TAG, "retrieved Friends nonces");
		} catch (RemoteException e) {
			Log.d(TAG, e.getMessage());
		}
//		Log.d(TAG, "reached retrieveFriendsNonce");
//		String fqlQuery = "SELECT message, created_time, actor_id FROM stream WHERE source_id IN(SELECT uid1 FROM friend WHERE uid2=me()) AND app_id = '"
//				+ appId + "' LIMIT 10000";
//
//		AsyncFacebookRunner mAsyncFacebookRunner = new AsyncFacebookRunner(facebookHandle);
//		Bundle params = new Bundle();
//		params.putString("method", "fql.query");
//		params.putString("query", fqlQuery);
//		Log.d(TAG, "directly querying facebook with:" + fqlQuery);
//		mAsyncFacebookRunner.request(null, params, new RequestListener(){
//
//			@Override
//			public void onComplete(String response, Object state) {
//				response = "{data:" + response + "}";
//				Log.d(TAG, "response to nonce request came in:" + response);
//
//				try {
//					JSONObject json = new JSONObject(response);
//					JSONArray data = json.getJSONArray("data");
//					ArrayList<NonceEntries> entries = new ArrayList<NonceEntries>();
//					if (data.length() > 0) {
//						for (int i = 0; i < data.length(); i++) {
//							String message = data.getJSONObject(i).getString(
//									"message");
//							long created_time = Long
//									.parseLong(data.getJSONObject(i).getString(
//											"created_time"));
//							String actor_id = data.getJSONObject(i).getString(
//									"actor_id");
//							addEntry(message, created_time, actor_id, entries);
//							Log.d(TAG, "added actor " + actor_id
//									+ " with message " + message);
//						}
//						deleteNonceList();
//						persistNonceInfo(entries);
//						// String postId =
//						// data.getJSONObject(0).getString("post_id");
//
//					} else {
//						Log.d(TAG, "got no responses");
//					}
//				} catch (Exception e) {
//					Log.d(TAG, "failed parsing json");
//					e.printStackTrace();
//				}
//			}
//
//			@Override
//			public void onIOException(IOException e, Object state) {
//
//			}
//
//			@Override
//			public void onFileNotFoundException(FileNotFoundException e,
//					Object state) {
//
//			}
//
//			@Override
//			public void onMalformedURLException(MalformedURLException e,
//					Object state) {
//
//			}
//
//			@Override
//			public void onFacebookError(FacebookError e, Object state) {
//				Log.d(TAG, "Facebook error: " + e.getMessage());
//			}
//
//		},1337);

	}

	// private void persistNonceInfo(ArrayList<NonceEntries> entries) {
	// try {
	// Log.d(TAG, "persisting nonces to " + nonceLocation);
	// BufferedOutputStream bos = new BufferedOutputStream(
	// context.openFileOutput(nonceLocation, Context.MODE_PRIVATE));
	// for (NonceEntries nonceEntries : entries) {
	// bos.write(nonceEntries.getMessage().getBytes());
	// bos.write('\n');
	// }
	// bos.flush();
	// bos.close();
	// } catch (FileNotFoundException e) {
	// Log.e(TAG, e.getMessage(), e);
	// } catch (IOException e) {
	// Log.e(TAG, e.getMessage(), e);
	// }
	// }

	private void persistMyInfo(FacebookEntry me) {
		try {
			BufferedOutputStream bos = new BufferedOutputStream(
					context.openFileOutput(myInfoLocation,
							Context.MODE_PRIVATE));
			bos.write(me.toString().getBytes());
			bos.write('\n');
			bos.flush();
			bos.close();
			Log.d(TAG, "persisted me to" + myInfoLocation);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
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
			bos.flush();
			bos.close();
			Log.d(TAG, "persisted friends to" + friendlistLocation);
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void deleteFriendsList() {
		context.deleteFile(friendlistLocation);
	}

	// private void deleteNonceList() {
	// context.deleteFile(nonceLocation);
	// }

	private void getAndPersistFriends() {
		final List<FacebookEntry> result = new ArrayList<FacebookEntry>();

		Log.d(TAG, "trying to request data from facebook with token: "
				+ facebookHandle.getAccessToken());
		Log.d(TAG,
				"which is valid to "
						+ new Date(facebookHandle.getAccessExpires()));


		mAsyncRunner.request("me/friends", createFriendRequestListener(result));
		mAsyncRunner.request("me", createMeRequestListener(result));
	}




	private synchronized void addFriend(List<FacebookEntry> result,
			FacebookEntry entry) {
		result.add(entry);
	}

	// private RequestListener createDontCareListener(){
	// return new RequestListener(){
	//
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
	// Object state) {Log.d(TAG,
	// "waiting for the registration service to be conected");
	// while (mIRemoteService == null) {
	// try {
	// Thread.sleep(100);
	// } catch (InterruptedException e) {
	// }
	// }
	//
	// Log.d
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
	// };
	// }

	private RequestListener createFriendRequestListener(
			final List<FacebookEntry> result) {
		return new RequestListener() {

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
				Log.d(TAG, "friendreply came in");
				try {
					JSONObject main = new JSONObject(response);
					JSONArray friendsData = main.getJSONArray("data");
					for (int i = 0; i < friendsData.length(); i++) {
						String name = friendsData.getJSONObject(i)
								.getString("name").toString();

						String id = friendsData.getJSONObject(i)
								.getString("id").toString();
						addFriend(result, new FacebookEntry(name, id));


					}

				} catch (JSONException e) {
					Log.e(TAG, e.getMessage(), e);
				}
				persistFriends(result);

			}
		};
	}

	private RequestListener createMeRequestListener(
			final List<FacebookEntry> result) {
		return new RequestListener() {

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
				Log.d(TAG, "me info came in");
				try {
					JSONObject main = new JSONObject(response);
					String name = main.getString("name").toString();
					String id = main.getString("id").toString();
					addFriend(result, new FacebookEntry(name, id));

					FacebookEntry myEntry = new FacebookEntry(name, id);
					persistMyInfo(myEntry);
				} catch (JSONException e) {
					Log.e(TAG, e.getMessage(), e);
				}

			}
		};
	}
}
