package edu.missouri.bas;

import com.equivital.sdk.ISemConnection;
import com.equivital.sdk.connection.SemBluetoothConnection;
import com.equivital.sdk.decoder.BadLicenseException;
import com.equivital.sdk.decoder.SDKLicense;
import com.equivital.sdk.decoder.SemDevice;
import com.equivital.sdk.decoder.events.ISemDeviceSummaryEvents;
import com.equivital.sdk.decoder.events.ISemDeviceTimingEvents;
import com.equivital.sdk.decoder.events.SEMDateTimeDataEventArgs;
import com.equivital.sdk.decoder.events.SemSummaryDataEventArgs;
import com.equivital.sdk.decoder.events.SynchronisationTimerEventArgs;

import edu.missouri.bas.service.SensorService;
import android.R.string;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ChestSensorService extends Service implements ISemDeviceTimingEvents, ISemDeviceSummaryEvents {
		
	public static final String ACTION_CONNECT_CHEST = "INTENT_ACTION_CONNECT_CHEST";
	public static final String KEY_ADDRESS = "KEY_ADDRESS";
	private static final String TAG ="ChestSensorService";
	private static SemDevice device;
	BroadcastReceiver bluetoothReceiver1;
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		
		super.onCreate();
		SDKLicense sdk = SemDevice.getLicense();
		sdk.applicationName = "Test Harness";
		sdk.developerName = "Java Version";
		sdk.licenseCode = "ZAP0Q9FLGo/XwrdBBAtdFk8jK7i/6fXFMzKiaCtC7jNvChtpMoOxSaH7tdqtFkmMbjUaskRyLGFCTGVJdNlrFjfbBjSGng9NGL4pnJ49TRTNR8Zmq0E9wnydpo3Du8RAcBVdGYjTjTctplrJ/cYHPHxOnbY5QuHYkY3dXBF3CSE=";
		Toast.makeText(getApplicationContext(),"Device object is created",Toast.LENGTH_LONG).show();
		IntentFilter chestSensorDataFilter = new IntentFilter(ChestSensorService.ACTION_CONNECT_CHEST);
		ChestSensorService.this.registerReceiver(bluetoothReceiver1, chestSensorDataFilter);
		
	}
	
   
   
		

   
@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Toast.makeText(getApplicationContext(),"Service Started",Toast.LENGTH_LONG).show();
		bluetoothReceiver1 = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) 	{
				String action = intent.getAction();
				if(action.equals(ChestSensorService.ACTION_CONNECT_CHEST))
				{
					Toast.makeText(getApplicationContext(),"got the intent",Toast.LENGTH_LONG).show();					
					String address=intent.getStringExtra(KEY_ADDRESS);
					try
					{
						device = new SemDevice();
						device.setSummaryDataEnabled(true);
					} 
					catch (BadLicenseException e1)
					{
						Toast.makeText(getApplicationContext(),"ERROR:License Code and Developer Name don't match",Toast.LENGTH_LONG).show();
						return;
					}		
					
						connectToDevice(address);
				}
				
				
			
			}

			
	   };
	   
	   
		
		return START_STICKY;
		
		
	}

private void connectToDevice(String address) {
   	Log.d(TAG,"Entered connectToDevice Method");
	// TODO Auto-generated method stub
    device.addSummaryEventListener(this);
	device.addTimingEventListener(this);
	ISemConnection connection = SemBluetoothConnection.createConnection(address);	
	device.start(connection);	
}
@Override
public void summaryDataUpdated(SemDevice arg0, SemSummaryDataEventArgs arg1) {
	// TODO Auto-generated method stub
	
	addtoCSV(arg1.getSummary().getHeartRate().getEcgRate());
}

private void addtoCSV(double ecgRate) 
{
	// TODO Auto-generated method stub
	//Code to write the data to a csv
}


@Override
public void semDateTimeDataReceived(SemDevice arg0,
		SEMDateTimeDataEventArgs arg1) {
	// TODO Auto-generated method stub
	
}

@Override
public void synchronisationTimerDataReceived(SemDevice arg0,
		SynchronisationTimerEventArgs arg1) {
	// TODO Auto-generated method stub
	
}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Toast.makeText(getApplicationContext(),"Service Stopped",Toast.LENGTH_LONG).show();
		
	}
	
	

	

}
