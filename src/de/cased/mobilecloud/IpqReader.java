package de.cased.mobilecloud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.util.Log;

public class IpqReader extends Thread{
	
	private static String TAG = "IPQ_READER";
	private BufferedReader reader;
	private IpqWorker worker;
	private boolean running = true;
	
	public IpqReader(InputStream is, IpqWorker worker){
		reader = new BufferedReader(new InputStreamReader(is));
		this.worker = worker;
	}
	
	
	@Override
	public void run() {
		try {
			String line;
			while(running && (line=reader.readLine()) != null){
				Log.d(TAG, "read:" + line);
				worker.addElementToWorkspace(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void halt(){
		running = false;
	}
}
