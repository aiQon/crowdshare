package org.servalproject;

import android.app.Activity;
import android.os.Bundle;

public class WifiJammedActivity extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

		setContentView(R.layout.wifijammedlayout);
		ServalBatPhoneApplication.terminate_setup = true;
		ServalBatPhoneApplication.terminate_main = true;
		ServalBatPhoneApplication.wifiSetup = false;
		ServalBatPhoneApplication.dontCompleteWifiSetup = true;
	}

}
