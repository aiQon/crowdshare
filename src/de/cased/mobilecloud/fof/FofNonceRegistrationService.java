package de.cased.mobilecloud.fof;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import de.cased.mobilecloud.RuntimeConfiguration;

public class FofNonceRegistrationService extends Service {

	private RuntimeConfiguration config;

	public FofNonceRegistrationService() {
		config = RuntimeConfiguration.getInstance();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new FofNonceRegistrationEngine(this,
				config.getProperty("menonce"),
				config.getProperty("nonce_location"));
	}

}
