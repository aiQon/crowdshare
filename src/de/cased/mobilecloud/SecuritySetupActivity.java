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
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class SecuritySetupActivity extends PreferenceActivity {

	RuntimeConfiguration config;
	Handler handler;
	RegistrationWorker worker;
	Preference delete;
	Preference register;

	CheckBoxPreference enableMobileCloud;
	CheckBoxPreference enableSecurity;
	CheckBoxPreference enableTethering;

	public static String ENABLE_MOBILE_CLOUD = "mobilecloud_string";
	public static String ENABLE_SECURITY = "security_string";
	public static String ENABLE_TETHERING = "tethering_string";



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		config = RuntimeConfiguration.getInstance();

		addPreferencesFromResource(R.xml.sec_preferences);
		delete = findPreference("deletePreference");
		register = findPreference("registerPreference");
		enableMobileCloud = (CheckBoxPreference) findPreference("enableMobileCloud");
		enableSecurity = (CheckBoxPreference) findPreference("enableSecurity");
		enableTethering = (CheckBoxPreference) findPreference("enableTethering");
		determineRegistrationState();
		determineSettings();
		registerReactions();
		createHandler();

	}


	private void determineSettings() {
		enableMobileCloud.setChecked(config.getPreferences().getBoolean(
				ENABLE_MOBILE_CLOUD, false));

		enableSecurity.setChecked(config.getPreferences().getBoolean(
				ENABLE_SECURITY, false));

		enableTethering.setChecked(config.getPreferences().getBoolean(
				ENABLE_TETHERING, false));
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



		enableSecurity
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						editor.putBoolean(ENABLE_SECURITY, (Boolean) newValue);
						editor.commit();
						enableSecurity.setChecked((Boolean) newValue);
						return true;
					}
				});


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

		enableTethering
				.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

					@Override
					public boolean onPreferenceChange(Preference preference,
							Object newValue) {
						SharedPreferences.Editor editor = config
								.getPreferences().edit();
						editor.putBoolean(ENABLE_TETHERING,
								(Boolean) newValue);
						editor.commit();
						enableTethering.setChecked((Boolean) newValue);
						return true;
					}
				});


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

	}

	private void toggleRegistration() {
		try {
			ProgressDialog dialog = ProgressDialog.show(this,
					"Performing Registration",
					"Please wait...", true);
			worker = new RegistrationWorker(dialog,
					handler);
			worker.start();

			Toast.makeText(this, "Request generated successfully", 2000).show();

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

		if (worker != null) {
			worker.cancel();

			// Mark RegistrationWorker for deletion by GC or there will be a
			// memory leak
			worker = null;
		}
	}

}
