package de.cased.mobilecloud;

import java.io.File;
import java.util.HashMap;

import org.servalproject.R;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;
import de.cased.mobilecloud.common.FriendUpdateStateContext;
import de.cased.mobilecloud.common.RegistrationStateContext;

public class SecuritySetupActivity extends PreferenceActivity {

	RuntimeConfiguration config;
	private static String TAG = "SecuritySetupActivity";
	Handler handler;
	// RegistrationWorker registrationWorker;
	Preference delete;
	Preference register;
	Preference fbUpdate;

	CheckBoxPreference enableMobileCloud;
	CheckBoxPreference enableSecurity;
	CheckBoxPreference enableLiability;
	CheckBoxPreference enableDirectFriendsTethering;
	EditTextPreference enableFoFTethering;
	// CheckBoxPreference enableTethering;
	// CheckBoxPreference enableTetheringFriends;

	ListPreference tehterPreference;

	public static String ENABLE_MOBILE_CLOUD = "mobilecloud_string";
	// public static String ENABLE_SECURITY = "security_string";
	public static String ENABLE_LIABILITY = "enableLiability";
	public static String ENABLE_TETHERING = "tethering_string";
	public static String ENABLE_TETHERING_FRIENDS = "tethering_string_friends";
	public static String ENABLE_TETHERING_FRIENDS_SET_INTERSECTION = "set intersection";

	public static String ENABLE_DIRECT_FRIENDS_TETHERING = "enableDirectFriendsTethering";
	public static String ENABLE_FOF_TETHERING = "enableFoFTethering";



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		config = RuntimeConfiguration.getInstance();

		addPreferencesFromResource(R.xml.sec_preferences);
		findViews();
		determineRegistrationState();
		determineSettings();
		registerReactions();
		createHandler();

	}


	private void findViews() {
		delete = findPreference("deletePreference");
		register = findPreference("registerPreference");
		fbUpdate = findPreference("fbServerUpdatePreference");
		enableMobileCloud = (CheckBoxPreference) findPreference("enableMobileCloud");
		enableLiability = (CheckBoxPreference) findPreference("enableLiability");
		enableDirectFriendsTethering = (CheckBoxPreference) findPreference("enableDirectFriendsTethering");
		enableFoFTethering = (EditTextPreference) findPreference("enableFoFTethering2");
		// enableSecurity = (CheckBoxPreference)
		// findPreference("enableSecurity");
		// enableTethering = (CheckBoxPreference)
		// findPreference("enableTethering");
		// enableTetheringFriends = (CheckBoxPreference)
		// findPreference("enableTetheringFriends");
		tehterPreference = (ListPreference) findPreference("tetherPref");
	}


	private void determineSettings() {
		enableMobileCloud.setChecked(config.getPreferences().getBoolean(
				ENABLE_MOBILE_CLOUD, false));

		int friendsNeeded = config.getPreferences().getInt(
				ENABLE_FOF_TETHERING, 0);
		enableFoFTethering.setText("" + friendsNeeded);


		enableDirectFriendsTethering.setChecked(config.getPreferences()
				.getBoolean(ENABLE_DIRECT_FRIENDS_TETHERING, false));

		// enableSecurity.setChecked(config.getPreferences().getBoolean(
		// ENABLE_SECURITY, false));

		enableLiability.setChecked(config.getPreferences().getBoolean(
				ENABLE_LIABILITY, false));

		if (config.getPreferences().getBoolean(ENABLE_TETHERING, false)) {
			tehterPreference.setValue("anonymous");
		} else if (config.getPreferences().getBoolean(ENABLE_TETHERING_FRIENDS,
				false)) {
			tehterPreference.setValue("server-side");
		} else if (config.getPreferences().getBoolean(
				ENABLE_TETHERING_FRIENDS_SET_INTERSECTION, false)) {
			tehterPreference.setValue("private-set-intersection");
		} else {
			tehterPreference.setValue("off");
		}

		if (config.getPreferences().getBoolean(
				ENABLE_TETHERING_FRIENDS_SET_INTERSECTION, false)
				|| config.getPreferences().getBoolean(ENABLE_TETHERING_FRIENDS,
						false)) {
			enableDirectFriendsTethering.setEnabled(true);
			enableFoFTethering.setEnabled(true);
		} else {
			enableDirectFriendsTethering.setEnabled(false);
			enableFoFTethering.setEnabled(false);
		}

		// enableTethering.setChecked(config.getPreferences().getBoolean(
		// ENABLE_TETHERING, false));
	}

	private void determineRegistrationState() {
		if (Utilities.isTokenAvailable(config)) {
			delete.setEnabled(true);
			register.setEnabled(false);
			config.setReadyToCloud(true);
		} else {
			delete.setEnabled(false);
			register.setEnabled(true);
			config.setReadyToCloud(false);
		}

	}

	private void createHandler() {
		// Handle response from the worker thread
		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				HashMap data = (HashMap) msg.obj;
				Boolean status = (Boolean) data.get("status");

				if (status == true) {// if successful
					Toast.makeText(getApplicationContext(),
							"Work was successful",
							2000).show();
					if (Utilities.isTokenAvailable(config)) {
						config.setReadyToCloud(true);
						register.setEnabled(false);
						delete.setEnabled(true);
					} else {
						deleteCredentials();
					}

				} else {
					if (data.get("message") != null) {
						Toast.makeText(getApplicationContext(),
								"Work failed!" + data.get("message"),
								3000).show();
					}

				}
			}
		};

	}

	private void registerReactions() {



		// enableSecurity
		// .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
		//
		// @Override
		// public boolean onPreferenceChange(Preference preference,
		// Object newValue) {
		// SharedPreferences.Editor editor = config
		// .getPreferences().edit();
		// editor.putBoolean(ENABLE_SECURITY, (Boolean) newValue);
		// editor.commit();
		// enableSecurity.setChecked((Boolean) newValue);
		// return true;
		// }
		// });


		enableMobileCloud
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						editor.putBoolean(ENABLE_MOBILE_CLOUD,
										(Boolean) newValue);
						editor.commit();
						enableMobileCloud.setChecked((Boolean) newValue);
						return true;
					}
				});

		enableLiability
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						editor.putBoolean(ENABLE_LIABILITY, (Boolean) newValue);
						editor.commit();
						enableLiability.setChecked((Boolean) newValue);
						return true;
					}
				});


		enableDirectFriendsTethering
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						editor.putBoolean(ENABLE_DIRECT_FRIENDS_TETHERING,
								(Boolean) newValue);
						editor.commit();
						enableDirectFriendsTethering.setChecked((Boolean) newValue);
						return true;
					}
				});

		enableFoFTethering
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						int extracted = 0;
						try {
							extracted = Integer.parseInt((String) newValue);
							editor.putInt(ENABLE_FOF_TETHERING, extracted);
						} catch (Exception e) {
							editor.putInt(ENABLE_FOF_TETHERING, 0);
						}

						editor.commit();
						enableFoFTethering.setText("" + extracted);
						return true;
					}
				});

		// enableTethering
		// .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
		//
		// @Override
		// public boolean onPreferenceChange(Preference preference,
		// Object newValue) {
		// boolean value = (Boolean) newValue;
		// enableTetheringAll(value);
		// if (value) {
		// enableTetheringFriends(false);
		// // enableTetheringFriends.setEnabled(false);
		// // } else {
		// // enableTetheringFriends.setEnabled(true);
		// }
		// return true;
		// }
		//
		// });

		// enableTetheringFriends
		// .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
		//
		// @Override
		// public boolean onPreferenceChange(Preference preference,
		// Object newValue) {
		//
		// boolean value = (Boolean) newValue;
		//
		// enableTetheringFriends(value);
		//
		// if (value) {
		// enableTetheringAll(false);
		// // enableTethering.setEnabled(false);
		// // } else {
		// // enableTethering.setEnabled(true);
		// }
		//
		// return true;
		// }
		//
		//
		// });


		register.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				toggleRegistration();
				return true;
			}
		});


		delete.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (deleteCredentials()) {
					Preference register = findPreference("registerPreference");
					Preference delete = findPreference("deletePreference");
					Toast.makeText(getApplicationContext(), "Token deleted",
							Toast.LENGTH_LONG)
							.show();
					register.setEnabled(true);
					delete.setEnabled(false);
					config.setToken(null);
				} else {
					Toast.makeText(getApplicationContext(),
							"Token could not be deleted",
							Toast.LENGTH_LONG).show();
				}
				return true;
			}
		});

		fbUpdate.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				updateFriendList();
				return true;
			}
		});

		tehterPreference
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						Log.d(TAG, "current value is:" + newValue.toString());
						deactivateAllTether();
						String keyFromList = getKeyFromList((String) newValue);
						manageSubmenue(keyFromList);
						setEditorBool(keyFromList, true);
						return true;
					}
				});


	}

	protected void manageSubmenue(String keyFromList) {
		if (keyFromList != null
				&& (keyFromList.equals(ENABLE_TETHERING_FRIENDS)
				|| keyFromList
						.equals(ENABLE_TETHERING_FRIENDS_SET_INTERSECTION))) {
			enableDirectFriendsTethering.setEnabled(true);
			enableFoFTethering.setEnabled(true);
		} else {
			enableDirectFriendsTethering.setEnabled(false);
			enableFoFTethering.setEnabled(false);
		}
	}

	protected String getKeyFromList(String newValue) {
		if(newValue.equals("anonymous")){
			return ENABLE_TETHERING.toString();
		} else if (newValue.equals("server-side")) {
			return ENABLE_TETHERING_FRIENDS.toString();
		} else if (newValue.equals("private-set-intersection")) {
			return ENABLE_TETHERING_FRIENDS_SET_INTERSECTION.toString();
		} else {
			return null;
		}
	}

	// private void enableTetheringAll(boolean newValue) {
	// SharedPreferences.Editor editor = config.getPreferences().edit();
	// editor.putBoolean(ENABLE_TETHERING, newValue);
	// editor.commit();
	//
	// enableTethering.setChecked(newValue);
	// }

	// private void enableTetheringFriends(boolean value) {
	// SharedPreferences.Editor editor = config.getPreferences().edit();
	// editor.putBoolean(ENABLE_TETHERING_FRIENDS, value);
	// editor.commit();
	//
	// enableTetheringFriends.setChecked(value);
	// }

	private void setEditorBool(String key, boolean value) {
		if (key != null) {
			SharedPreferences.Editor editor = config.getPreferences().edit();
			editor.putBoolean(key, value);
			editor.commit();
		}
	}

	private void deactivateAllTether() {
		SharedPreferences.Editor editor = config.getPreferences().edit();
		editor.putBoolean(ENABLE_TETHERING, false);
		editor.putBoolean(ENABLE_TETHERING_FRIENDS, false);
		editor.putBoolean(ENABLE_TETHERING_FRIENDS_SET_INTERSECTION, false);
		editor.commit();
	}

	protected void updateFriendList() {
		try {
			ProgressDialog dialog = ProgressDialog.show(this,
					"Performing Update", "Please wait...", true);
			UpdateWorker update = new UpdateWorker(dialog, handler,
					new FriendUpdateStateContext());
			update.start();

			LocalFacebookUpdater localUpdater = new LocalFacebookUpdater(
					config.getProperty("appid"), this,
					config.getProperty("localfriends"),
					config.getProperty("myInfo"));
			localUpdater.start();

		} catch (Exception e) {
			Toast.makeText(this, e.getMessage(), 2000).show();
			e.printStackTrace();
		}
	}

	private void toggleRegistration() {
		try {
			ProgressDialog dialog = ProgressDialog.show(this,
					"Performing Registration",
					"Please wait...", true);
			RegistrationWorker registrationWorker = new RegistrationWorker(
					dialog, handler,
					new RegistrationStateContext());
			registrationWorker.start();

		} catch (Exception e) {
			Toast.makeText(this, e.getMessage(), 2000).show();
			e.printStackTrace();
		}

	}

	private boolean deleteCredentials() {
		File token = new File(config.getCertificateDir(),
				config.getProperties().getProperty("token_loc"));

		File privateKey = new File(config.getCertificateDir(),
				config.getProperties().getProperty("private_key_loc"));

		File dhParams = new File(config.getCertificateDir(),
				config.getProperties().getProperty("dh_loc"));

		boolean result = token.delete() && privateKey.delete()
				&& dhParams.delete();

		config.setReadyToCloud(false);

		return result;
	}

	@Override
	public void onPause() {
		super.onPause();

		// if (registrationWorker != null) {
		// registrationWorker.cancel();
		//
		// // Mark RegistrationWorker for deletion by GC or there will be a
		// // memory leak
		// registrationWorker = null;
		// }
	}

}
