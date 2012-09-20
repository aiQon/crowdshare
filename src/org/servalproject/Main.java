/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Properties;

import org.servalproject.PreparationWizard.Action;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.account.AccountService;
import org.servalproject.servald.Identities;
import org.servalproject.servald.PeerListService;
import org.servalproject.system.WifiMode;
import org.servalproject.wizard.Wizard;

import android.R.drawable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import de.cased.mobilecloud.ConnectionStateListener;
import de.cased.mobilecloud.PeerClientCommunicator;
import de.cased.mobilecloud.PeerClientCommunicator.LocalBinder;
import de.cased.mobilecloud.RuntimeConfiguration;
import de.cased.mobilecloud.SecuritySetupActivity;
import de.cased.mobilecloud.Status;
import de.cased.mobilecloud.Utilities;
import ext.org.bouncycastle.openssl.PEMReader;

/**
 *
 * Main activity which presents the Serval launcher style screen. On the first
 * time Serval is installed, this activity ensures that a warning dialog is
 * presented and the user is taken through the setup wizard. Once setup has been
 * confirmed the user is taken to the main screen.
 *
 */
public class Main extends Activity implements ConnectionStateListener {
	public ServalBatPhoneApplication app;
	private static final String PREF_WARNING_OK = "warningok";

	public static String TAG = "Main";

	ImageView btnPower;
//	Button btnreset;
	ImageView btncall;
	ImageView helpLabel;
	ImageView settingsLabel;
	ImageView btnShare;
	ImageView btnShareServal;
	BroadcastReceiver mReceiver;
	boolean mContinue;
	// private TextView buttonToggle;
	// private ImageView buttonToggleImg;
	// private Drawable powerOnDrawable;
	// private Drawable powerOffDrawable;
	// private boolean changingState;

	private ImageView toggleView;
	private ImageView callView;
	private ImageView smsView;
	private ImageView tetherView;

	private TextView tetherTextView;

	private Button peerCount;

	private TextView startText;

	private RuntimeConfiguration config;

	private PeerClientCommunicator clientCommunicator;
	private boolean isCommunicatorBound = false;

	private Control controlService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.app = (ServalBatPhoneApplication) this.getApplication();
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.activity_main_portrait);

		initSecureAddon();


		callView = (ImageView) this.findViewById(R.id.callnow);
		callView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Main.this.startActivity(new Intent(Intent.ACTION_DIAL));
			}
		});

		startText = (TextView) findViewById(R.id.textView3);

		toggleView = (ImageView) this.findViewById(R.id.btntoggle);
		toggleView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Log.d(TAG, "reached toggleView click listener");
				State state = app.getState();

				Intent serviceIntent = new Intent(Main.this, Control.class);
				switch (state) {
				case On:
					Log.d(TAG, "going to stop the service");
					if (controlService != null) {
						Log.d(TAG,
								"bound to service, going to close by direct method call");
						controlService.stop();
					}

					if (isCommunicatorBound) {
						if (clientCommunicator.getCurrentStatus() != Status.Offline) {
							abortConnection();
						}
					}

					stopService(serviceIntent);
					break;
				case Off:
					Log.d(TAG, "going to start the service");
					startService(serviceIntent);
					break;
				}

				// if Client mode ask the user if we should turn it off.
				if (state == State.On
						&& app.wifiRadio.getCurrentMode() == WifiMode.Client) {
					AlertDialog.Builder alert = new AlertDialog.Builder(
							Main.this);
					alert.setTitle("Stop Wifi");
					alert.setMessage("Would you like to turn wifi off completely to save power?");
					alert.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int whichButton) {
									new Thread() {
										@Override
										public void run() {
											try {
												app.wifiRadio
														.setWiFiMode(WifiMode.Off);
											} catch (Exception e) {
												Log.e("BatPhone", e.toString(),
														e);
												app.displayToastMessage(e
														.toString());
											}
										}
									}.start();
								}
							});
					alert.setNegativeButton("No", null);
					alert.show();
				}
			}
		});

		smsView = (ImageView) findViewById(R.id.imageView4);
		tetherView = (ImageView) findViewById(R.id.imageView5);
		tetherView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				performButtonAction();
			}
		});

		peerCount = (Button) findViewById(R.id.button1);
		peerCount.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				startActivity(new Intent(Main.this, PeerList.class));
			}
		});

		tetherTextView = (TextView) findViewById(R.id.textView5);


		Intent intent = new Intent(this, PeerClientCommunicator.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		// Intent controlIntent = new Intent(this, Control.class);
		// bindService(controlIntent, cConnection, Context.BIND_AUTO_CREATE);

		adaptToStatus();

		findPeerCount();


	} // onCreate

	private void findPeerCount() {
		peerCount.setText("" + PeerListService.peerCount(this));

		// new Thread(new Runnable() {
		// @Override
		// public void run() {
		// while (controlService == null)
		// ;
		// runOnUiThread(new Runnable() {
		// @Override
		// public void run() {
		// peerCount.setText("" + controlService.getPeerCount());
		// }
		// });
		// }
		// }).start();
	}

	private void adaptToStatus() {
		if (isCommunicatorBound) {
			Runnable switchUI = new Runnable() {

				@Override
				public void run() {
					tetherTextView.setText(decipherStatus(clientCommunicator
							.getCurrentStatus()));
					switch (clientCommunicator.getCurrentStatus()) {
					case Offline:
						// TODO check if the state is up
						if (app.getState() != State.On) {
							tetherView
									.setImageResource(R.drawable.wifi_tether_stopped);
						} else {
							tetherView.setImageResource(R.drawable.wifi_tether);
						}
						break;
					case EstablishedManagementConnection:
					case SearchingManagementConnections:
					case Connecting:
						tetherView
								.setImageResource(R.drawable.wifi_searching);
						break;
					case Connected:
						tetherView
								.setImageResource(R.drawable.wifi_tether_connected);
						break;
					case Error:
						tetherView
								.setImageResource(R.drawable.wifi_tether_stopped);
						break;
					default:
						break;
					}

				}
			};
			runOnUiThread(switchUI);
		} else {
			tetherTextView.setText("Tethering");
			tetherView.setImageResource(R.drawable.wifi_tether_stopped);
		}
	}

	private String decipherStatus(Status currentStatus) {
		switch (currentStatus) {
		case Offline:
			return "Tethering";
		case SearchingManagementConnections:
			return "Searching...";
		case EstablishedManagementConnection:
			return "Management est.";
		case Connecting:
			return "Connecting...";
		case Connected:
			return "Connected";
		case Error:
			return "Error";
		default:
			return "Unknown";
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className,
				IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			clientCommunicator = binder.getService();
			isCommunicatorBound = true;

			clientCommunicator
					.addConnectionStateListener(Main.this);

			adaptToStatus();
			notifyAboutManagementChange();

		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			clientCommunicator
					.removeConnectionStateListener(Main.this);
			isCommunicatorBound = false;
			clientCommunicator = null;
		}
	};

	// private ServiceConnection cConnection = new ServiceConnection() {
	//
	// @Override
	// public void onServiceConnected(ComponentName className,
	// IBinder service) {
	// Log.d(TAG, "Control service connected");
	// LocalControlBinder binder = (LocalControlBinder) service;
	// controlService = binder.getService();
	// }
	//
	// @Override
	// public void onServiceDisconnected(ComponentName arg0) {
	// controlService = null;
	// }
	// };


	private void initSecureAddon() {
		instantiateHelperObjects();
		loadConfiguration();
		createDirectories();
		handleToken();
		installCertificates();

	}

	private void instantiateHelperObjects() {
		config = RuntimeConfiguration.getInstance();
		config.setAssetManager(getAssets());
		config.setContentResolver(getContentResolver());
		config.setHostActivity(this);
		config.setPreferences(PreferenceManager
				.getDefaultSharedPreferences(this));
	}

	private void loadConfiguration() {
		InputStream is = null;
		try {
			is = config.getAssetManager().open("properties");
			Properties props = new Properties();
			props.load(is);
			config.setProperties(props);
		} catch (Exception e) {
			Log.d(TAG, "Could not open properties, dying");
			e.printStackTrace();
			System.exit(-1);
		} finally {
			Utilities.closeInputStream(is);
		}
	}

	private void createDirectories() {
		config.setCertificateDir(getDir("certificates",
				Context.MODE_WORLD_READABLE));
		config.setConfigurationDir(getDir("configs",
				Context.MODE_WORLD_READABLE));

	}

	private void loadToken() {

		File tokenFile = new File(
				config.getCertificateDir(), config.getProperties()
						.getProperty("token_loc"));

		if (tokenFile.exists()) {
			try {
				PEMReader tokenReader = new PEMReader(new FileReader(tokenFile));
				Object rawToken = tokenReader.readObject();
				tokenReader.close();
				if (rawToken instanceof Certificate) {
					X509Certificate extractedToken = (X509Certificate) rawToken;
					Log.d(TAG,
							"extracted token dn:"
									+ extractedToken.getSubjectDN());
					config.setToken(extractedToken);
				}
			} catch (Exception e) {
				Log.d(TAG, "Failed loading token");
			}
		} else {
			Toast.makeText(this, "No private credentials found",
					Toast.LENGTH_LONG)
					.show();
		}
	}

	private void loadPrivateKey() {

		File privKeyFile = new File(
				config.getCertificateDir(), config.getProperties()
						.getProperty("private_key_loc"));
		if (privKeyFile.exists()) {
			try {
				PEMReader keyReader = new PEMReader(new FileReader(privKeyFile));
				Object rawKey = keyReader.readObject();
				keyReader.close();
				if (rawKey instanceof KeyPair) {
					KeyPair tempPair = (KeyPair) rawKey;
					config.setPrivKey(tempPair.getPrivate());
					Log.d(TAG, "extracted private key");
				}
			} catch (Exception e) {
				Log.d(TAG, "Failed loading private key");
			}
		} else {
			Toast.makeText(this, "No public credentials found",
					Toast.LENGTH_LONG)
					.show();
		}
	}

	private void loadCredentials() {
		loadToken();
		loadPrivateKey();
	}

	private boolean isTokenAvailable() {
		File file = new File(config.getCertificateDir(), config.getProperties()
				.getProperty("token_loc"));
		if (file.exists())
			return true;
		else
			return false;
	}

	private void handleToken() {
		if (isTokenAvailable()) {
			loadCredentials();
			Toast.makeText(this, "Authentication Token retrieved", 3000).show();
		}
	}

	private void installCert(String from, String to) {

		File file = new File(config.getCertificateDir(), config.getProperties()
				.getProperty(to));

		if (!file.exists()) {
			try {

				DataInputStream reader = new DataInputStream(getAssets().open(
						config.getProperties().getProperty(from)));
				byte[] buffer = new byte[4096];
				reader.read(buffer, 0, 4096);
				Utilities.closeInputStream(reader);
				FileOutputStream fos = new FileOutputStream(file);
				fos.write(buffer);
				Utilities.closeOutputStream(fos);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void installCertificates() {
		installCert("ca_cert_loc", "ca_cert_dest_loc");
		installCert("server_cert_loc", "server_cert_dest_loc");
	}

	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			Log.d(TAG, "received state change event");

			int stateOrd = intent.getIntExtra(
					ServalBatPhoneApplication.EXTRA_STATE, 0);
			State state = State.values()[stateOrd];
			stateChanged(state);
		}
	};

	private void performButtonAction() {
		Log.d(TAG, "performing Button Action");
		if (isCommunicatorBound) {
			switch (clientCommunicator.getCurrentStatus()) {
			case Offline:
				Log.d(TAG, "request tethering");
				requestTethering();
				break;
			case SearchingManagementConnections:
				Log.d(TAG, "abort management connection search");
				abortConnection();
				break;
			case Connected:
				Log.d(TAG, "cancel connection");
				abortConnection();
				break;
			default:
				Log.d(TAG, "unknown Button Action requested");
				break;
			}
		} else {
			Toast.makeText(this, "PeerCommunicator is not started",
					Toast.LENGTH_LONG).show();
		}
	}

	private void abortConnection() {
		if (isCommunicatorBound) {
			Log.d(TAG, "going to abort connection search");
			clientCommunicator.stopService();
		}
	}

	private void requestTethering() {

		if (!Utilities.is3GAvailable(config)) {
			if (config.getToken() != null
					&& config.getPrivKey() != null) {
				Log.d(TAG, "sent request Tethering request");
				Intent peerClientComm = new Intent(config.getHostActivity(),
						PeerClientCommunicator.class);
				// peerClientComm.putExtra(PeerClientCommunicator.EXTRA_MESSENGER,
				// new Messenger(handler));
				startService(peerClientComm);
			} else {
				Toast.makeText(
						this,
						"We are missing credential files to start properly",
						Toast.LENGTH_LONG)
						.show();
			}
		} else {
			Toast.makeText(
					this,
					"You have 3G connectivity, disable it to request tethering",
					Toast.LENGTH_LONG)
					.show();
		}
	}

	boolean registered = false;

	private void stateChanged(State state) {
		// TODO update display of On/Off button
		switch (state) {
		case Installing:
		case Upgrading:
		case Starting:
			toggleView.setImageResource(R.drawable.play_busy);
			startText.setText("Starting...");
			break;
		case Stopping:
			toggleView.setImageResource(R.drawable.stop_busy);
			startText.setText("Stopping...");

			callView.setEnabled(false);
			callView.setImageResource(R.drawable.call_stopped);

			smsView.setEnabled(false);
			smsView.setImageResource(R.drawable.mail_stopped);
			tetherView.setEnabled(false);
			tetherView.setImageResource(R.drawable.wifi_tether_stopped);

			break;
		case Broken:
			toggleView.setImageResource(R.drawable.play_busy);
			startText.setText("Working...");
			break;
		case On:
			// toggleButton.setEnabled(true);
			toggleView.setImageResource(R.drawable.stop);
			callView.setEnabled(true);
			callView.setImageResource(R.drawable.call);

			smsView.setEnabled(true);
			smsView.setImageResource(R.drawable.mail);

			tetherView.setEnabled(true);
			tetherView.setImageResource(R.drawable.wifi_tether);

			startText.setText("Stop");
			break;
		case Off:
			// toggleButton.setEnabled(true);
			callView.setEnabled(false);
			callView.setImageResource(R.drawable.call_stopped);
			toggleView.setImageResource(R.drawable.play);
			smsView.setEnabled(false);
			smsView.setImageResource(R.drawable.mail_stopped);
			tetherView.setEnabled(false);
			tetherView.setImageResource(R.drawable.wifi_tether_stopped);
			peerCount.setText("" + 0);

			startText.setText("Start");
			break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		checkAppSetup();
	}

	/**
	 * Run initialisation procedures to setup everything after install. Called
	 * from onResume() and after agreeing Warning dialog
	 */
	private void checkAppSetup() {
		if (ServalBatPhoneApplication.terminate_main) {
			ServalBatPhoneApplication.terminate_main = false;
			finish();
			return;
		}

		// Don't continue unless they've seen the warning
		// if (!app.settings.getBoolean(PREF_WARNING_OK, false)) {
		// showDialog(R.layout.warning_dialog);
		// return;
		// }

		if (PreparationWizard.preparationRequired()
				|| !ServalBatPhoneApplication.wifiSetup) {
			// Start by showing the preparation wizard
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			return;
		}

		if (Identities.getCurrentDid() == null
				|| Identities.getCurrentDid().equals("")
				|| AccountService.getAccount(this) == null) {
			// this.startActivity(new Intent(this, AccountAuthActivity.class));
			// use the wizard rather than the test AccountAuthActivity

			Log.v("MAIN", "currentDid(): " + Identities.getCurrentDid());
			Log.v("MAIN", "getAccount(): " + AccountService.getAccount(this));

			this.startActivity(new Intent(this, Wizard.class));
			return;
		}

		// Put in-call display on top if it is not currently so
		// if (Receiver.call_state != UserAgent.UA_STATE_IDLE) {
		// Receiver.moveTop();
		// return;
		// }

		if (!registered) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(ServalBatPhoneApplication.ACTION_STATE);
			this.registerReceiver(receiver, filter);
			registered = true;
		}
		stateChanged(app.getState());

		TextView pn = (TextView) this.findViewById(R.id.mainphonenumber);
		String id = "";
		if (Identities.getCurrentDid() != null)
			id=Identities.getCurrentDid();
		if (Identities.getCurrentName() != null)
			if (Identities.getCurrentName().length() > 0)
				id = id + "/" + Identities.getCurrentName();
		pn.setText(id);

		if (app.showNoAdhocDialog) {
			// We can't figure out how to control adhoc
			// mode on this phone,
			// so warn the user.
			// TODO, this is really the wrong place for this!!!
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder
					.setMessage("I could not figure out how to get ad-hoc WiFi working on your phone.  Some mesh services will be degraded.  Obtaining root access may help if you have not already done so.");
			builder.setTitle("No Ad-hoc WiFi :(");
			builder.setPositiveButton("ok", null);
			builder.show();
			app.showNoAdhocDialog = false;
		}

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (registered) {
			this.unregisterReceiver(receiver);
			registered = false;
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		LayoutInflater li = LayoutInflater.from(this);
		View view = li.inflate(R.layout.warning_dialog, null);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(view);
		builder.setPositiveButton(R.string.agree,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int b) {
						dialog.dismiss();
						app.preferenceEditor.putBoolean(PREF_WARNING_OK, true);
						app.preferenceEditor.commit();
						checkAppSetup();
					}
				});
		builder.setNegativeButton(R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int b) {
						dialog.dismiss();
						finish();
					}
				});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				dialog.dismiss();
				finish();
			}
		});
		return builder.create();
	}

	// /**
	// * MENU SETTINGS
	// */
	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// MenuInflater inflater = getMenuInflater();
	// inflater.inflate(R.menu.main_menu, menu);
	// return true;
	// }

	private static final int MENU_SETUP = 0;
	private static final int MENU_PEERS = 1;
	// private static final int MENU_LOG = 2;
	private static final int MENU_REDETECT = 3;
	// private static final int MENU_RHIZOME = 4;
	private static final int MENU_ABOUT = 5;
	private static final int MENU_SECURITY = 6;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean supRetVal = super.onCreateOptionsMenu(menu);
		SubMenu m;

		m = menu.addSubMenu(0, MENU_SETUP, 0, getString(R.string.setuptext));
		m.setIcon(drawable.ic_menu_preferences);

		m = menu.addSubMenu(0, MENU_PEERS, 0, "Peers");
		m.setIcon(drawable.ic_dialog_info);

		// m = menu.addSubMenu(0, MENU_LOG, 0, getString(R.string.logtext));
		// m.setIcon(drawable.ic_menu_agenda);

		m = menu.addSubMenu(0, MENU_REDETECT, 0,
				getString(R.string.redetecttext));
		m.setIcon(R.drawable.ic_menu_refresh);

		// m = menu.addSubMenu(0, MENU_RHIZOME, 0,
		// getString(R.string.rhizometext));
		// m.setIcon(drawable.ic_menu_agenda);

//		m = menu.addSubMenu(0, MENU_ABOUT, 0, getString(R.string.abouttext));
//		m.setIcon(drawable.ic_menu_info_details);

		m = menu.addSubMenu(0, MENU_SECURITY, 0,
				getString(R.string.secmenutext));
		m.setIcon(R.drawable.ic_menu_new_keys);

		return supRetVal;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		boolean supRetVal = super.onOptionsItemSelected(menuItem);
		switch (menuItem.getItemId()) {
		case MENU_SETUP:
			startActivity(new Intent(this, SetupActivity.class));
			break;
		case MENU_SECURITY:
			startActivity(new Intent(this, SecuritySetupActivity.class));
			break;
		case MENU_PEERS:
			startActivity(new Intent(this, PeerList.class));
			break;
		// case MENU_LOG:
		// startActivity(new Intent(this, LogActivity.class));
		// break;
		case MENU_REDETECT:
			// Clear out old attempt_ files
			File varDir = new File("/data/data/org.servalproject/var/");
			if (varDir.isDirectory())
				for (File f : varDir.listFiles()) {
					if (!f.getName().startsWith("attempt_"))
						continue;
					f.delete();
				}
			// Re-run wizard
			PreparationWizard.currentAction = Action.NotStarted;
			Intent prepintent = new Intent(this, PreparationWizard.class);
			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(prepintent);
			break;
		// case MENU_RHIZOME:
		// // Check if there's a SD card, because no SD card will lead Rhizome
		// // to crash - code from Android doc
		// String state = Environment.getExternalStorageState();
		//
		// if (Environment.MEDIA_MOUNTED.equals(state)) {
		// startActivity(new Intent(this, RhizomeRetriever.class));
		// } else {
		// app.displayToastMessage(getString(R.string.rhizomesdcard));
		// }
		// break;
//		case MENU_ABOUT:
//			this.openAboutDialog();
//			break;
		}
		return supRetVal;
	}

//	@Override
//	public boolean onOptionsItemSelected(MenuItem menuItem) {
//
//		switch (menuItem.getItemId()) {
//		case R.id.main_menu_show_log:
//			startActivity(new Intent(this, LogActivity.class));
//			return true;
//		case R.id.main_menu_set_number:
//			startActivity(new Intent(Main.this, Wizard.class));
//			new Thread() {
//				@Override
//				public void run() {
//					try {
//						app.resetNumber();
//					} catch (Exception e) {
//						Log.e("BatPhone", e.toString(), e);
//						app.displayToastMessage(e.toString());
//					}
//				}
//			}.start();
//			return true;
//		case R.id.main_menu_redetect_wifi:
//			// Clear out old attempt_ files
//			File varDir = new File("/data/data/org.servalproject/var/");
//			if (varDir.isDirectory())
//				for (File f : varDir.listFiles()) {
//					if (!f.getName().startsWith("attempt_"))
//						continue;
//					f.delete();
//				}
//			// Re-run wizard
//			PreparationWizard.currentAction = Action.NotStarted;
//			Intent prepintent = new Intent(this, PreparationWizard.class);
//			prepintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//			startActivity(prepintent);
//			return true;
//		default:
//			return super.onOptionsItemSelected(menuItem);
//		}
//	}

	@Override
	public void notifyAboutConnectionChange() {
		adaptToStatus();

	}

	@Override
	public void notifyAboutManagementChange() {
		// TODO Auto-generated method stub

	}
}
