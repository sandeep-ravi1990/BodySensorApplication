package edu.missouri.bas.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import edu.missouri.bas.MainActivity;
import edu.missouri.bas.R;
import edu.missouri.bas.bluetooth.BluetoothRunnable;
import edu.missouri.bas.bluetooth.affectiva.AffectivaPacket;
import edu.missouri.bas.bluetooth.affectiva.AffectivaRunnable;
import edu.missouri.bas.service.modules.location.LocationControl;
import edu.missouri.bas.service.modules.sensors.SensorControl;
import edu.missouri.bas.survey.XMLSurveyActivity;
import edu.missouri.bas.survey.answer.SurveyAnswer;


import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

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



public class SensorService extends Service  implements ISemDeviceTimingEvents, ISemDeviceSummaryEvents {

    private final String TAG = "SensorService";
	
	/*
	 * Android component variables used by the service
	 */
	private Context serviceContext;
	private IBinder mBinder = new ServiceBinder<SensorService>(SensorService.this);

	/*
	 * Notification variables
	 */
	private NotificationManager notificationManager;
	private Notification notification;
	
	/*
	 * Sensor variables
	 */
	private LocationManager mLocationManager;
	private SensorManager mSensorManager;
    private long stime;
	private SensorControl sensorControl = null;
	private LocationControl locationControl = null;
	
	/*
	 * File I/O Variables 
	 */
	private final String BASE_PATH = "/sdcard/TestResults/";
	private final String[] surveyNames = {"CRAVING_EPISODE","DRINKING_FOLLOWUP",
			"MORNING_REPORT","RANDOM_ASSESSMENT","MOOD_DYSREGULATION","INITIAL_DRINKING"};
	private HashMap<String, String> surveyFiles;
	
	
	PowerManager mPowerManager;
	WakeLock serviceWakeLock;
	
	/*
	 * Alarm manager variables, for scheduling intents
	 */
	private AlarmManager mAlarmManager;
	private PendingIntent scheduleSurvey;
	private PendingIntent scheduleSensor;
	private PendingIntent scheduleLocation;

	/*
	 * Static intent actions
	 */
	public static final String ACTION_SCHEDULE_SURVEY = "INTENT_ACTION_SCHEDULE_SURVEY";

	public static final String ACTION_SCHEDULE_SENSOR = "INTENT_ACTION_SCHEDULE_SENSOR";
	
	public static final String ACTION_SCHEDULE_LOCATION = "INTENT_ACTION_SCHEDULE_LOCATION";
	
	public static final String ACTION_SENSOR_DATA = "INTENT_ACTION_SENSOR_DATA";

	public static final String ACTION_TRIGGER_SURVEY = "INTENT_ACTION_TRIGGER_SURVEY";
	
	public static final String ACTION_CONNECT_BLUETOOTH = "INTENT_ACTION_CONNECT_BLUETOOTH";
	
	public static final String ACTION_DISCONNECT_BLUETOOTH = "INTENT_ACTION_DISCONNECT_BLUETOOTH";
	
	public static final String ACTION_GET_BLUETOOTH_STATE = "INTENT_ACTION_BLUETOOTH_STATE";
	
	public static final String ACTION_BLUETOOTH_RESULT = "INTENT_ACTION_BLUETOOTH_RESULT";
	
	public static final String ACTION_BLUETOOTH_STATE_RESULT = "INTENT_ACTION_BLUETOOTH_STATE_RESULT";

	public static final String INTENT_EXTRA_BT_DEVICE = "EXTRA_DEVICE_ADR";
	
	public static final String INTENT_EXTRA_BT_MODE = "EXTRA_DEVICE_MODE";
	
	public static final String INTENT_EXTRA_BT_TYPE = "EXTRA_DEVICE_TYPE";
	
	public static final String INTENT_EXTRA_BT_UUID = "EXTRA_DEVICE_UUID";
	
	public static final String INTENT_EXTRA_BT_RESULT = "EXTRA_BLUETOOTH_RESULT";
	
	public static final String INTENT_EXTRA_BT_STATE = "EXTRA_BLUETOOTH_STATE";
	
	public static final String INTENT_EXTRA_BT_DEVICE_NAME = null;

	public static final String INTENT_EXTRA_BT_DEVICE_ADDRESS = null;

	public static final int MESSAGE_BLUETOOTH_DATA = 0;

	public static final int MESSAGE_LOCATION_DATA = 1;

	public static final int MESSAGE_SENSOR_DATA = 3;
	
	public static final String Listening ="Listening";
	
	public static final String ACTION_CONNECT_CHEST = "INTENT_ACTION_CONNECT_CHEST";
	
	public static final String KEY_ADDRESS = "KEY_ADDRESS";
	
	public static final int CHEST_SENSOR_DATA = 109;
	
	
	
	
	
	/*
	 * Bluetooth Variables
	 */
	Thread bluetoothThread;
	AffectivaRunnable affectivaRunnable;
	String bluetoothMacAddress = "default";
	BluetoothAdapter mBluetoothAdapter;
	
	/*
	 * Worker Threads
	 */
	Thread httpPostThread;
	Thread FileWriterThread;
	HttpPostThread httpPostRunnable;
	Runnable fileWriterRunnable;
	
	Notification mainServiceNotification;
	public static final int SERVICE_NOTIFICATION_ID = 1;
	
	private static SemDevice device;

	
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	@Override
	public void onCreate(){
		
		super.onCreate();
		Log.d(TAG,"Starting sensor service");
		
		//Setup service context
		serviceContext = this;
		
		//Get sensor manager 
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		
		//Get alarm manager
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		
		//Get location manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		
		serviceWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorServiceLock");
		serviceWakeLock.acquire();
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetoothMacAddress = mBluetoothAdapter.getAddress();
		
		//Initialize start time
		stime = System.currentTimeMillis();
		
        //Setup calendar object
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(stime);
		
		/*
		 * Setup notification manager
		 */

		notification = new Notification(R.drawable.icon2,"Recorded",System.currentTimeMillis());
		notification.defaults=0; 
		notification.flags|=Notification.FLAG_ONGOING_EVENT;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		Intent notifyIntent = new Intent(Intent.ACTION_MAIN);
		notifyIntent.setClass(this, MainActivity.class);
		
		/*
		 * Display notification that service has started
		 */
        notification.tickerText="Sensor Service Running";
        PendingIntent contentIntent = PendingIntent.getActivity(SensorService.this, 0,
                notifyIntent, Notification.FLAG_ONGOING_EVENT);
        notification.setLatestEventInfo(SensorService.this, getString(R.string.app_name),
        		"Recording service started at: "+cal.getTime().toString(), contentIntent);
        notificationManager.notify(SensorService.SERVICE_NOTIFICATION_ID, notification);
		
		/*
		 * Setup IO for recording
		 */
		try {
			prepareIO();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		/*
		 * Setup location control object
		 */
		locationControl = new LocationControl(this, mLocationManager, 1000 * 60, 200, 5000);
		
		sensorControl = new SensorControl(mSensorManager, serviceContext, 30000);


		//Register for result intents from XML Survey
		IntentFilter activityResultFilter = 
				new IntentFilter(XMLSurveyActivity.INTENT_ACTION_SURVEY_RESULTS);
		SensorService.this.registerReceiver(alarmReceiver, activityResultFilter);

		IntentFilter sensorDataFilter = 
				new IntentFilter(SensorService.ACTION_SENSOR_DATA);
		SensorService.this.registerReceiver(alarmReceiver, sensorDataFilter);
		
		/*IntentFilter triggerFilter =
				new IntentFilter(SensorService.ACTION_TRIGGER_SURVEY);
		SensorService.this.registerReceiver(alarmReceiver, triggerFilter);*/
		
		Log.d(TAG,"Sensor service created.");
		
		
		httpPostRunnable = new HttpPostThread(this, "http://dslsrv8.cs.missouri.edu/BAS/InsertDebug.php");
		httpPostRunnable.start();
		httpPostThread = new Thread(httpPostRunnable);
		httpPostThread.start();
		
		
		try {
			prepareIO();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		prepareAlarms();
		//prepareBluetooth();
				
		SDKLicense sdk = SemDevice.getLicense();
		sdk.applicationName = "Test Harness";
		sdk.developerName = "Java Version";
		sdk.licenseCode = "ZAP0Q9FLGo/XwrdBBAtdFk8jK7i/6fXFMzKiaCtC7jNvChtpMoOxSaH7tdqtFkmMbjUaskRyLGFCTGVJdNlrFjfbBjSGng9NGL4pnJ49TRTNR8Zmq0E9wnydpo3Du8RAcBVdGYjTjTctplrJ/cYHPHxOnbY5QuHYkY3dXBF3CSE=";
		
	}
	
	
	

	private void prepareAlarms(){
		//Intent schedulePicture = new Intent(SensorService.ACTION_SCHEDULE_PICTURE);
		//Intent scheduleAudio = new Intent(SensorService.ACTION_SCHEDULE_AUDIO);
		//Intent scheduleTranscription = new Intent(SensorService.ACTION_SCHEDULE_TRANSCRIPTION);
		Random rand = new Random(System.currentTimeMillis());
		Intent scheduleSurveyIntent = 
				new Intent(SensorService.ACTION_SCHEDULE_SURVEY);
		scheduleSurvey = PendingIntent.getBroadcast(
				serviceContext, 0, scheduleSurveyIntent, 0);
		long randomTime = 60+rand.nextInt(60);
		//randomTime = 1;
		mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 1000 + (1000 * 60 * randomTime), scheduleSurvey);
		
		Intent scheduleSensorIntent = 
				new Intent(SensorService.ACTION_SCHEDULE_SENSOR);
		scheduleSensor = PendingIntent.getBroadcast(
				serviceContext, 0, scheduleSensorIntent, 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 10000, 1000 * 31, scheduleSensor);
		
		Intent scheduleLocationIntent = new Intent(SensorService.ACTION_SCHEDULE_LOCATION);
		scheduleLocation = PendingIntent.getBroadcast(
				serviceContext, 0, scheduleLocationIntent, 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 10000, 1000 * 60 * 5, scheduleLocation);
		
		IntentFilter sensorSchedulerFilter = 
				new IntentFilter(ACTION_SCHEDULE_SENSOR);
		IntentFilter surveySchedulerFilter = 
				new IntentFilter(XMLSurveyActivity.INTENT_ACTION_SURVEY_RESULTS);
		IntentFilter locationSchedulerFilter =
				new IntentFilter(ACTION_SCHEDULE_LOCATION);
		IntentFilter surveyScheduleFilter =
				new IntentFilter(ACTION_SCHEDULE_SURVEY);
		IntentFilter surveyTest =
				new IntentFilter("ACTION_SURVEY_TEST");

		IntentFilter bluetoothConnect = new IntentFilter(ACTION_CONNECT_BLUETOOTH);
		IntentFilter bluetoothDisconnect = new IntentFilter(ACTION_DISCONNECT_BLUETOOTH);
		IntentFilter bluetoothUpdate = new IntentFilter(ACTION_GET_BLUETOOTH_STATE);
		
		
		IntentFilter locationFoundFilter = new IntentFilter(LocationControl.INTENT_ACTION_LOCATION);
		SensorService.this.registerReceiver(alarmReceiver, sensorSchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, surveySchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, locationSchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, locationFoundFilter);
		SensorService.this.registerReceiver(alarmReceiver, surveyScheduleFilter);
		SensorService.this.registerReceiver(alarmReceiver, surveyTest);
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothConnect);
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothDisconnect);
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothUpdate);
		
		
		/*Chest Sensor Intent Filter*/
		
		IntentFilter chestSensorData = new IntentFilter(ACTION_CONNECT_CHEST);
		SensorService.this.registerReceiver(chestSensorReceiver,chestSensorData);
		
		
		
		

	}
	
	/*
	 * Prepare IO that will be used for different recording tasks
	 */
	private void prepareIO() throws IOException{
		surveyFiles = new HashMap<String, String>();
		
		//If the base directory doesn't exist, create it
		File DIR = new File(BASE_PATH);
		if(!DIR.exists())
			DIR.mkdir();

		for(String key: surveyNames){
			surveyFiles.put(key, BASE_PATH + key.toLowerCase() + ".txt");
		}
		/*
		 * Open files that will be used for various tasks and
		 * for battery level recordings
		 */
		//sensorFile = new File(BASE_PATH+sensorFileName);
			
		
		
		/*
		 * Open streams for recording battery information,
		 * other tasks will be responsible for open their own
		 * streams.
		 */
		//fileWriterSensor = new FileWriter(audioFile, true);
		//bufferedWriterSensor = new BufferedWriter(fileWriterSensor);
		
		Log.d(TAG,"IO Prepared");
	}

	/*
	 * Unregister battery receiver and make sure the sensors are done
	 * before destroying the service
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy(){
		locationControl.cancel();
		sensorControl.cancel();
		
		httpPostRunnable.stop();
		try {
			httpPostThread.interrupt();
			httpPostThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if(affectivaRunnable != null){
			affectivaRunnable.stop();
			try {
				bluetoothThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		notificationManager.cancel(SensorService.SERVICE_NOTIFICATION_ID);
		
		SensorService.this.unregisterReceiver(alarmReceiver);
		SensorService.this.unregisterReceiver(bluetoothReceiver);
		SensorService.this.unregisterReceiver(chestSensorReceiver);
		
		mAlarmManager.cancel(scheduleSurvey);
		mAlarmManager.cancel(scheduleSensor);
		mAlarmManager.cancel(scheduleLocation);
		
		serviceWakeLock.release();
		
		Log.d(TAG,"Service Stopped.");
		
		super.onDestroy();
		if(device!=null){
		device.stop();
		}
	}
	
	BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_SCHEDULE_SENSOR)){
				Log.d(TAG,"Received alarm event - schedule sensor");
				sensorControl.startRecording();
			}
			else if(action.equals(SensorService.ACTION_SENSOR_DATA)){
				Log.d(TAG,"Sensor Data Received");
				HashMap<String, String> sensorMap =
						new HashMap<String, String>();
				double[] avg = intent.getDoubleArrayExtra(SensorControl.SENSOR_AVERAGE);
				
				sensorMap.put("xVal", avg[0]+"");
	
				sensorMap.put("yVal", avg[1]+"");	

				sensorMap.put("zVal", avg[2]+"");
				List<NameValuePair> pairs = parseAssocToList(sensorMap);
				httpPostRunnable.post(new HttpPostRequest("", "PHONE_ACCELEROMETER", pairs));
			}
			else if(action.equals(SensorService.ACTION_SCHEDULE_LOCATION)){
				Log.d(TAG,"Received alarm event - schedule location");
				locationControl.startRecording();
			}
			else if(action.equals(LocationControl.INTENT_ACTION_LOCATION)){
				Log.d(TAG,"Received alarm event - location found");
				Location foundLocation = 
						intent.getParcelableExtra(LocationControl.LOCATION_INTENT_KEY);

				if(foundLocation != null){
					HashMap<String, String> locationMap = 
							new HashMap<String, String>();
					
					locationMap.put("accuracy", foundLocation.getAccuracy()+"");
					
					locationMap.put("longi", foundLocation.getLongitude()+"");
					
					locationMap.put("lat", foundLocation.getLatitude()+"");			
					
					locationMap.put("source", foundLocation.getProvider());	
					
					writeLocationToFile(foundLocation);
					List<NameValuePair> pairs = parseAssocToList(locationMap);
					httpPostRunnable.post(new HttpPostRequest("", "LOCATION", pairs));
				}
			}
			else if(action.equals(SensorService.ACTION_SCHEDULE_SURVEY)){
				Log.d(TAG,"Received alarm event - schedule survey");
				
				String name = intent.getStringExtra(SurveyAnswer.TRIGGER_NAME);
				String file = intent.getStringExtra(SurveyAnswer.TRIGGER_FILE);
				Intent i = new Intent(serviceContext, XMLSurveyActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				
				if(name != null && file != null){
					i.putExtra("survey_name",file);
					i.putExtra("survey_file",name);
				}
				else{
					long random = ((new Random(System.currentTimeMillis()).nextInt(60) ) + 60);
					mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
							SystemClock.elapsedRealtime()+ (1000 * 60 * random) , scheduleSurvey);
					i.putExtra("survey_name", "RANDOM_ASSESSMENT");
					i.putExtra("survey_file", "RandomAssessmentParcel.xml");
				}
				serviceContext.startActivity(i);
			}
			else if (action.equals(XMLSurveyActivity.INTENT_ACTION_SURVEY_RESULTS)){
				Log.d(TAG,"Got survey results");
				Calendar cal = Calendar.getInstance();
				
				cal.setTimeInMillis(System.currentTimeMillis());
				
				@SuppressWarnings("unchecked")
				HashMap<String, List<String>> results = 
						(HashMap<String, List<String>>) intent.getSerializableExtra(XMLSurveyActivity.INTENT_EXTRA_SURVEY_RESULTS);
				String surveyName = 
						intent.getStringExtra(XMLSurveyActivity.INTENT_EXTRA_SURVEY_NAME);
				
				try {
					writeSurveyToFile(surveyName, results, intent.getLongExtra(XMLSurveyActivity.INTENT_EXTRA_COMPLETION_TIME,0L));
				} catch (IOException e) {
					e.printStackTrace();
					Log.e(TAG,"ERROR: Failed to write survey to file!");
				}
				Log.d(TAG,"Done writing file");
				List<NameValuePair> pairs = parseMapToList(results);
				httpPostRunnable.post(new HttpPostRequest("", surveyName, pairs));
			}
		}
	};
	
	protected void writeLocationToFile(Location l){
		String toWrite;
		File f = new File(BASE_PATH+"locations.txt");
		toWrite = System.currentTimeMillis()+","+
			l.getLatitude()+","+l.getLongitude()+","+
			l.getAccuracy()+","+l.getProvider();
		if(f != null){
			try {
				writeToFile(f, toWrite);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
	}
	
	protected void writeSurveyToFile(String surveyName, 
			HashMap<String, List<String>> surveyData, long time) throws IOException{
		
		File f = new File(surveyFiles.get(surveyName));
		Log.d(TAG,"File: "+f.getName());
		
		StringBuilder sb = new StringBuilder(100);
		
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);
		
		List<String> sorted = new ArrayList<String>(surveyData.keySet());
		Collections.sort(sorted);
		sb.append(c.getTime().toString());
		
		sb.append(",");

		for(int i = 0; i < sorted.size(); i++){
			String key = sorted.get(i);
			List<String> data = surveyData.get(key);
			sb.append(key+":");
			if(data == null)
				sb.append("");
			else{
				for(int j = 0; j < data.size(); j++){
					sb.append(data.get(j));
					if(i != data.size()-1)sb.append(";");
				}
			}
			if(i != sorted.size()-1) sb.append(",");
		}

		sb.append("\n");
		writeToFile(f,sb.toString());
	}
	
	protected void writeToFile(File f, String toWrite) throws IOException{
		FileWriter fw = new FileWriter(f, true);
		fw.write(toWrite);
		fw.close();
	}
	
	/*protected void writeDataToFile(File f, String toWrite) throws IOException{
		FileWriter fw = new FileWriter(f, true);
		fw.append(toWrite);
	    fw.append('\n');
        fw.flush();
		fw.close();
	}*/
	
	protected List<NameValuePair> parseMapToList(HashMap<String, List<String>> map){
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		List<NameValuePair> result = new ArrayList<NameValuePair>();
		for(Map.Entry<String, List<String>> entry: map.entrySet()){
			List<String> temp = entry.getValue();
			if(temp == null){
				result.add(new BasicNameValuePair(entry.getKey(),""));
			}
			else{
				StringBuilder sb = new StringBuilder(10);
				for(int i = 0; i < temp.size(); i++){
					if(i != 0) sb.append(",");
					sb.append(entry.getValue().get(i));
				}
				result.add(new BasicNameValuePair(entry.getKey(),sb.toString()));
			}
		}
		result.add(new BasicNameValuePair("timestamp",cal.getTime().toString()));
		result.add(new BasicNameValuePair("userid",bluetoothMacAddress));
		return result;
	}
	
	protected List<NameValuePair> parseAssocToList(HashMap<String, String> map){
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		List<NameValuePair> result = new ArrayList<NameValuePair>();
		for(Map.Entry<String, String> entry: map.entrySet()){
			String temp = entry.getValue();
			if(temp == null){
				Log.d(TAG,"Value was skipped: "+entry.getKey());
				result.add(new BasicNameValuePair(entry.getKey(),""));
			}
			else{
				result.add(
						new BasicNameValuePair(entry.getKey(), entry.getValue()));
			}
		}
		result.add(new BasicNameValuePair("timestamp",cal.getTime().toString()));
		result.add(new BasicNameValuePair("userid",bluetoothMacAddress));		
		return result;
	}

	BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_CONNECT_BLUETOOTH)){
				if(affectivaRunnable != null &&
						(affectivaRunnable.getState() == BluetoothRunnable.BluetoothState.CONNECTED
						|| affectivaRunnable.getState() == BluetoothRunnable.BluetoothState.CONNECTING)){
					
				}
				else{
					/* Handler = null
					 * device = from intent
					 * uuid = from intent
					 * mode = from intent
					 * bluetoothsockettype = from intent
					 * file = null
					 */
					String deviceAdr = intent.getStringExtra(INTENT_EXTRA_BT_DEVICE);
					String uuidRaw = intent.getStringExtra(INTENT_EXTRA_BT_UUID);
					UUID uuid = UUID.fromString(uuidRaw);
					int mode = intent.getIntExtra(INTENT_EXTRA_BT_MODE, 1);
					int type = intent.getIntExtra(INTENT_EXTRA_BT_TYPE, 1);
					BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAdr);
					affectivaRunnable = new AffectivaRunnable(mHandler, device, uuid, mode, type, null);
					bluetoothThread = new Thread(affectivaRunnable);
					bluetoothThread.start();
				}
			}
			else if(action.equals(SensorService.ACTION_DISCONNECT_BLUETOOTH)){
				Intent i = new Intent(ACTION_BLUETOOTH_RESULT);
				if(affectivaRunnable != null &&
						(affectivaRunnable.getState() == BluetoothRunnable.BluetoothState.CONNECTED
						|| affectivaRunnable.getState() == BluetoothRunnable.BluetoothState.CONNECTING )){
					affectivaRunnable.stop();
					try {
						bluetoothThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					i.putExtra(INTENT_EXTRA_BT_RESULT, true);
				}
				else{
					i.putExtra(INTENT_EXTRA_BT_RESULT, false);
				}
				serviceContext.sendBroadcast(i);
				
			}
			else if(action.equals(SensorService.ACTION_GET_BLUETOOTH_STATE)){
				if(affectivaRunnable != null){
					Intent i = new Intent(ACTION_BLUETOOTH_STATE_RESULT);
					if(affectivaRunnable != null){ 
						Log.d(TAG,"Bluetooth state is: "+affectivaRunnable.getState());
						
						Bundle b = affectivaRunnable.getStateBundle();
						String deviceAddress = b.getString(BluetoothRunnable.KEY_DEVICE_ADDRESS);
						String deviceName = b.getString(BluetoothRunnable.KEY_DEVICE_NAME);
						int newState = b.getInt(BluetoothRunnable.KEY_OLD_STATE);
						String newStateString = null;
						
						switch(newState) {
							case BluetoothRunnable.BluetoothState.CONNECTED:
								newStateString = "Connected to: "+deviceName;
								break;
							case BluetoothRunnable.BluetoothState.CONNECTING:
								newStateString = "Attempting to connect to: "+deviceName;
								break;
							case BluetoothRunnable.BluetoothState.FAILED:
								newStateString = "Failed to connect";
								break;
							case BluetoothRunnable.BluetoothState.FINISHING:
								newStateString = "Connection finishing";
								break;
							case BluetoothRunnable.BluetoothState.LISTENING:
								newStateString = "Listening for a connection";
								break;
							case BluetoothRunnable.BluetoothState.NONE:
								newStateString = "Not connected to any device";
								break;
							case BluetoothRunnable.BluetoothState.STOPPED:
								newStateString = "Bluetooth thread was stopped";
								break;
							default: newStateString = "Invalid state: "+
												newState+", something went wrong";
								break;
						}	
						
						i.putExtra(INTENT_EXTRA_BT_STATE, newStateString);
						i.putExtra(SensorService.INTENT_EXTRA_BT_DEVICE_NAME, deviceName);
						i.putExtra(SensorService.INTENT_EXTRA_BT_DEVICE_ADDRESS, deviceAddress);
					}
					else
						i.putExtra(INTENT_EXTRA_BT_STATE, BluetoothRunnable.BluetoothState.NONE);
					serviceContext.sendBroadcast(i);
				}
			}
		}
	};
	
	Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			if(msg.what != MESSAGE_BLUETOOTH_DATA){
				Log.d(TAG,"Got message: "+msg.what);
			}
			if(msg.what == MESSAGE_BLUETOOTH_DATA){
				AffectivaPacket p = (AffectivaPacket) msg.obj;
				handleBluetooth(p);
			}
			else if(msg.what == BluetoothRunnable.MessageCode.DISCONNECTED){
				Intent i=new Intent();
				
				BluetoothDevice device = (BluetoothDevice) msg.obj;
				Toast.makeText(serviceContext, "Lost connection to device: "+device.getName(),
						Toast.LENGTH_LONG).show();
				Vibrator vibrator = 
					(Vibrator) serviceContext.getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(500);
			}
			else if(msg.what == BluetoothRunnable.MessageCode.STATE_CHANGED){
				Log.d(TAG,"State changed handled");
				Bundle b = (Bundle) msg.obj;
				String deviceAddress = b.getString(BluetoothRunnable.KEY_DEVICE_ADDRESS);
				String deviceName = b.getString(BluetoothRunnable.KEY_DEVICE_NAME);
				String oldState = b.getString(BluetoothRunnable.KEY_OLD_STATE);
				int newState = b.getInt(BluetoothRunnable.KEY_NEW_STATE);
				String newStateString = null;
				
				switch(newState) {
					case BluetoothRunnable.BluetoothState.CONNECTED:
						newStateString = "Connected to: "+deviceName;	
						Intent h=new Intent("fucku");
						h.putExtra("State_Change",newStateString);
						serviceContext.sendBroadcast(h);
						break;
					case BluetoothRunnable.BluetoothState.CONNECTING:
						newStateString = "Attempting to connect to: "+deviceName;						
						break;
					case BluetoothRunnable.BluetoothState.FAILED:
						newStateString = "Failed to connect";
						break;
					case BluetoothRunnable.BluetoothState.FINISHING:
						newStateString = "Connection finishing";
						break;
					case BluetoothRunnable.BluetoothState.LISTENING:
						newStateString = "Listening for a connection";
						break;
					case BluetoothRunnable.BluetoothState.NONE:
						newStateString = "Not connected to any device";
						break;
					case BluetoothRunnable.BluetoothState.STOPPED:
						newStateString = "Bluetooth thread was stopped";
						break;
					default: newStateString = "Invalid state: "+
						newState+", something went wrong";
						break;
				}	
				
				handleBluetoothStateChange(deviceAddress, deviceName, newStateString);
			}

		}
		
		private void handleBluetooth(AffectivaPacket p){
			HashMap<String, String> bluetoothMap = 
				new HashMap<String, String>();
			bluetoothMap.put("accel_x", p.getAccelerometer().getX()+"");
			bluetoothMap.put("accel_y", p.getAccelerometer().getY()+"");
			bluetoothMap.put("accel_z", p.getAccelerometer().getZ()+"");

			bluetoothMap.put("temp", p.getTemperature()+"");
			bluetoothMap.put("battery", p.getBattery()+"");
			
			bluetoothMap.put("eda", p.getEda()+"");
			bluetoothMap.put("seq_num", p.getSequenceNum());
	
			List<NameValuePair> pairs = parseAssocToList(bluetoothMap);
			httpPostRunnable.post(new HttpPostRequest("", "AFFECTIVA_SENSOR", pairs));
		}

		
		private void handleBluetoothStateChange(String deviceAddress, String deviceName, String newState){
			if(affectivaRunnable != null)
			{
				Intent i = new Intent(ACTION_BLUETOOTH_STATE_RESULT);
				if(affectivaRunnable != null)
				{ 
					i.putExtra(INTENT_EXTRA_BT_STATE, newState);
					i.putExtra(SensorService.INTENT_EXTRA_BT_DEVICE_NAME, deviceName);
					i.putExtra(SensorService.INTENT_EXTRA_BT_DEVICE_ADDRESS, deviceAddress);
					Log.d(TAG,"Sending state change");
				}
				else
				{
					i.putExtra(INTENT_EXTRA_BT_STATE, BluetoothRunnable.BluetoothState.NONE);
				}
				
				serviceContext.sendBroadcast(i);
			}
		}
	};
	
	/*Chest Sensor Code Starts From Here */
	
	BroadcastReceiver chestSensorReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_CONNECT_CHEST)){
				Toast.makeText(getApplicationContext(),"Intent Received",Toast.LENGTH_LONG).show();
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

	private void connectToDevice(String address) {
		Toast.makeText(getApplicationContext(), "Trying to connect to the device",Toast.LENGTH_LONG).show();
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
		updateSummary(arg1.getSummary().getMotion().name(),arg1.getSummary().getOrientation().name(),
				arg1.getSummary().getBreathingRate().getBeltSensorRate(),
				arg1.getSummary().getBreathingRate().getEcgDerivedRate(),arg1.getSummary().getBreathingRate().getImpedanceRate(),
				arg1.getSummary().getHeartRate().getEcgRate(),arg1.getSummary().getQualityConfidence().getBeltQuality(),
				arg1.getSummary().getQualityConfidence().getECGQuality(),
				arg1.getSummary().getQualityConfidence().getImpedanceQuality(),
				arg1.getSummary().getQualityConfidence().getHeartRateConfidence(),
				arg1.getSummary().getQualityConfidence().getBreathingRateConfidence());
		
	}

	private void updateSummary(String motion, String bodyPosition,
			double beltSensorRate, double ecgDerivedRate, double impedanceRate,
			double ecgRate, double beltQuality, double ecgQuality,
			double impedanceQuality, double heartRateConfidence,
			double breathingRateConfidence) {
		// TODO Auto-generated method stub
		 String dataFromChestSensor=motion+","+bodyPosition+","+String.valueOf(beltSensorRate)+","+String.valueOf(ecgDerivedRate)+","+
				 String.valueOf(impedanceRate)+","+String.valueOf(ecgRate)+","+String.valueOf(beltQuality)+","+String.valueOf(ecgQuality)+","+
				 String.valueOf(impedanceQuality)+","+String.valueOf(heartRateConfidence)+","+String.valueOf(breathingRateConfidence);	
		 Message msgData=new Message();
		 msgData.what = CHEST_SENSOR_DATA;
		 Bundle dataBundle = new Bundle();
		 dataBundle.putString("DATA",dataFromChestSensor);
		 msgData.obj=dataBundle;
		 chestSensorDataHandler.sendMessage(msgData);
	}
	
	Handler chestSensorDataHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			if(msg.what==CHEST_SENSOR_DATA)
			{
				Bundle resBundle =  (Bundle)msg.obj;
				writeChestSensorDatatoCSV(String.valueOf(resBundle.getString("DATA")));
				
			}
			
		}
		
	};

	private void writeChestSensorDatatoCSV(String chestSensorData) {
		// TODO Auto-generated method stub
		Toast.makeText(serviceContext,"Trying to write to the file",Toast.LENGTH_LONG).show();
		
        File f = new File(BASE_PATH,"chestsensordata.txt");		
		String dataToWrite = System.currentTimeMillis()+","+chestSensorData;
		if(f != null){
			try {
				writeToFile(f, dataToWrite);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	
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
}
