package de.cased.mobilecloud.fof;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import de.cased.mobilecloud.RuntimeConfiguration;

public class FofNonceService extends Service {

	private RuntimeConfiguration config;

	public FofNonceService() {
		config = RuntimeConfiguration.getInstance();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return (IBinder) new FofNonceEngine(this, config.getProperty("menonce"),
				config.getProperty("nonce_location"));
	}

}
