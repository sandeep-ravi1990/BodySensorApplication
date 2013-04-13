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
import android.R.string;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ChestSensor extends Service implements ISemDeviceTimingEvents, ISemDeviceSummaryEvents {

		
	private static SemDevice device;
	

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		SDKLicense sdk = SemDevice.getLicense();
		sdk.applicationName = "Test Harness";
		sdk.developerName = "Java Version";
		sdk.licenseCode = "ZAP0Q9FLGo/XwrdBBAtdFk8jK7i/6fXFMzKiaCtC7jNvChtpMoOxSaH7tdqtFkmMbjUaskRyLGFCTGVJdNlrFjfbBjSGng9NGL4pnJ49TRTNR8Zmq0E9wnydpo3Du8RAcBVdGYjTjTctplrJ/cYHPHxOnbY5QuHYkY3dXBF3CSE=";

	}
	
	
	public void connectoChestSensor(String address)
	{
		// (1) CONSTRUCT A SemDevice PARSER INSTANCE:
		// ==========================================
		
		try
		{
			// Firstly, we construct a SemDevice instance.
			// This is the core decoder object which parses SEM data streams.
			device = new SemDevice();
			
			// We are interested in summary data so we enable this now.
			device.setSummaryDataEnabled(true);
		} 
		catch (BadLicenseException e1)
		{
			// You must ensure you have entered your license code details in the program using the static SemDevice.getLicense() method.
			// Failure to do this will result in the SemDevice construction failing and an exception being thrown.
			
			Toast.makeText(getApplicationContext(),"ERROR:License Code and Developer Name don't match",Toast.LENGTH_LONG).show();
			return;
		}		

		
		// (2) HOOK UP SOME EVENT HANDLERS FOR EVENTS YOU ARE INTERESTED IN:
		// =================================================================
		
		// Hook up some event handlers on the SemDevice instance for the things that you are interested in.
		
		// In this case we are interested in summary updates, connection and timing events.
		// The event handlers must implement the type of interface required for each kind of event.
		// We're implementing all the necessary interfaces in this class so we just pass ourselves in as the listener..
		
		device.addSummaryEventListener(this);
		device.addTimingEventListener(this);		// Subscribe to timing events (1-second sync marker, data-time updates, etc)
			
		// (3) CREATE A CONNECTION OBJECT THAT IS RESPONSIBLE FOR PROVIDING THE SEM DATA:
		// ==============================================================================
		
		// A SemDevice instance requires a data source "connection" to parse data from (and to).  You can provide your
		// own data source by implementing the ISemConnection interface directly, or use one of the stock connections
		// provided as part of the development kit.
		
		
		// TO PARSE DATA BEING SENT VIA BLUEOOTH:
		// **************************************
		ISemConnection connection = SemBluetoothConnection.createConnection(address);
		
			
		// (4) START THE PARSER!
		// =====================

		// Start the parser by invoking the "start" method with the connection you want to use.
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
	
	

	

}
