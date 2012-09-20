package de.cased.mobilecloud;

import org.servalproject.R;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class EnterPhoneNumber extends Activity {


	private Spinner countryName;
	private EditText countryCode;
	private EditText phoneNumber;
	private Button done;
	private RuntimeConfiguration config;

	private ArrayAdapter<CharSequence> countryCodes;
	private ArrayAdapter<CharSequence> isoCodes;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_phone_number);
		config = RuntimeConfiguration.getInstance();
        findViews();
        fillSpinner();
        loadCountryCodes();
		loadIsoCodes();
        reactOnSpinnerChange();
		setDefaultCountryValue();
		reactOnButtonPress();
    }

	private void setDefaultCountryValue() {
		String countryCode = checkOnCountryCode();
		if (countryCode != null && !countryCode.equals("")) {
			int pos = findPositionInIsoCodes(countryCode);
			if (pos != -1) {
				countryName.setSelection(pos);
			}
		}
	}

	private int findPositionInIsoCodes(String countryCode2) {
		for (int i = 0; i < isoCodes.getCount(); i++) {
			String extract = isoCodes.getItem(i).toString();
			if (extract.equals(countryCode2)) {
				return i;
			}
		}
		return -1;
	}

	private void reactOnButtonPress() {
    	done.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				String cleanNumber = cleanNumber();
				if(cleanNumber != null){
					performNumberAction(countryCode.getText().toString(), cleanNumber);
				}
			}
		});
	}

    protected void performNumberAction(String countrCode, String number) {

		// TODO safe country code here too somewhere, is necessary for
		// registering at CA
		final String fullNumber = number;
		done.setEnabled(false);

		// new AsyncTask<Void, Void, Void>() {
		// @Override
		// protected Void doInBackground(Void... params) {
		// try {
		// config.getApp().setPrimaryNumber(fullNumber,
		// false);
		//
		// Intent serviceIntent = new Intent(
		// EnterPhoneNumber.this, Control.class);
		// startService(serviceIntent);
		//
		// Intent intent = new Intent(EnterPhoneNumber.this,
		// Main.class);
		// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		// EnterPhoneNumber.this.startActivity(intent);
		//
		// } catch (IllegalArgumentException e) {
		// config.getApp().displayToastMessage(e.getMessage());
		// } catch (Exception e) {
		// Log.e("BatPhone", e.toString(), e);
		// config.getApp().displayToastMessage(e.toString());
		// }
		// return null;
		// }
		//
		// @Override
		// protected void onPostExecute(Void result) {
		// done.setEnabled(true);
		// }
		// }.execute((Void[]) null);

	}

	/*
     * checks the number to be made of only digits and start with a "0"
     */
	protected String cleanNumber() {
		String workingCopy = phoneNumber.getText().toString();
		if(workingCopy.length() < 3){
			showToast("Enter your real phone number, please");
		}else{
			if(workingCopy.charAt(0) != '0'){
				workingCopy = "0" + workingCopy;
			}
			for(int i = 0; i < workingCopy.length(); i++){
				if(workingCopy.charAt(i) < 48 || workingCopy.charAt(i) > 57){
					showToast("Enter only digits, please");
					return null;
				}
			}

		}
		return workingCopy;
	}

	private void showToast(String string) {
		Toast.makeText(this, string, Toast.LENGTH_LONG).show();
	}

	private void loadCountryCodes() {

    	countryCodes = ArrayAdapter.createFromResource(this,
    	        R.array.country_codes_array, android.R.layout.simple_spinner_item);
	}

	private void loadIsoCodes() {
		isoCodes = ArrayAdapter.createFromResource(this,
				R.array.iso_codes_array,
				android.R.layout.simple_spinner_item);
	}

	private void reactOnSpinnerChange() {
    	countryName.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				countryCode.setText(countryCodes.getItem(position));

			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub

			}
		});
	}

	private void fillSpinner() {
    	ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
    	        R.array.country_names_array, R.layout.spinner_text);
    	adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    	countryName.setAdapter(adapter);
	}

	private void findViews() {
    	countryName = (Spinner) findViewById(R.id.spinner_country_names);
    	countryCode = (EditText) findViewById(R.id.country_code);
    	countryCode.setEnabled(false);
		phoneNumber = (EditText) findViewById(R.id.phone_number_text);
		done = (Button) findViewById(R.id.done_button);
	}

	private String checkOnCountryCode() {
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		return tm.getSimCountryIso();
	}

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// getMenuInflater().inflate(R.menu.activity_enter_phone_number, menu);
	// return true;
	// }
}
