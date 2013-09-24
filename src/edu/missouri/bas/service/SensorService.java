package edu.missouri.bas.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import edu.missouri.bas.MainActivity;
import edu.missouri.bas.R;
import edu.missouri.bas.SensorConnections;
import edu.missouri.bas.SurveyStatus;
import edu.missouri.bas.bluetooth.BluetoothRunnable;
import edu.missouri.bas.bluetooth.affectiva.AffectivaPacket;
import edu.missouri.bas.bluetooth.affectiva.AffectivaRunnable;
import edu.missouri.bas.bluetooth.equivital.EquivitalRunnable;
import edu.missouri.bas.datacollection.InternalSensor;
import edu.missouri.bas.service.modules.location.ActivityRecognitionScan;
import edu.missouri.bas.service.modules.location.DetectionRemover;
import edu.missouri.bas.service.modules.location.LocationControl;
import edu.missouri.bas.service.modules.sensors.SensorControl;

import edu.missouri.bas.survey.XMLSurveyActivity;
import edu.missouri.bas.survey.XMLSurveyActivity.StartSound;
import edu.missouri.bas.survey.answer.SurveyAnswer;


import android.R.string;
import android.app.ActivityManager;
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.equivital.sdk.ISemConnection;
import com.equivital.sdk.connection.SemBluetoothConnection;
import com.equivital.sdk.decoder.BadLicenseException;
import com.equivital.sdk.decoder.SDKLicense;
import com.equivital.sdk.decoder.SemCalibrationParameterType;
import com.equivital.sdk.decoder.SemDevice;
import com.equivital.sdk.decoder.SemOperatingModeType;
import com.equivital.sdk.decoder.events.BatteryVoltageEventArgs;
import com.equivital.sdk.decoder.events.HeartRateEventArgs;
import com.equivital.sdk.decoder.events.ISemDeviceBatteryEvents;
import com.equivital.sdk.decoder.events.ISemDeviceBreathingRateEvents;
import com.equivital.sdk.decoder.events.ISemDeviceHeartRateEvents;
import com.equivital.sdk.decoder.events.ISemDeviceSummaryEvents;
import com.equivital.sdk.decoder.events.ISemDeviceTimingEvents;
import com.equivital.sdk.decoder.events.QualityConfidenceEventArgs;
import com.equivital.sdk.decoder.events.RRIntervalEventArgs;
import com.equivital.sdk.decoder.events.RespirationRateEventArgs;
import com.equivital.sdk.decoder.events.SEMDateTimeDataEventArgs;
import com.equivital.sdk.decoder.events.SemSummaryDataEventArgs;
import com.equivital.sdk.decoder.events.SynchronisationTimerEventArgs;
import com.google.android.gms.location.DetectedActivity;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;



public class SensorService extends Service  implements ISemDeviceSummaryEvents,SensorEventListener {//ISemDeviceHeartRateEvents,ISemDeviceBreathingRateEvents, 

    private final String TAG = "SensorService";   
	/*
	 * Android component variables used by the service
	 */
	public static Context serviceContext;
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
	private SensorEventListener mSensorListener;
    private long stime;
	private SensorControl sensorControl = null;
	private static LocationControl locationControl = null;
	
	/*
	 * File I/O Variables 
	 */
	public final static String BASE_PATH = "sdcard/TestResults/";
	public String PATH_TOREAD;
	//public static File BASE_PATH;
	private final String[] surveyNames = {"CRAVING_EPISODE","DRINKING_FOLLOWUP",
			"MORNING_REPORT","RANDOM_ASSESSMENT","MOOD_DYSREGULATION","INITIAL_DRINKING"};
	private HashMap<String, String> surveyFiles;
	
	
	PowerManager mPowerManager;
	WakeLock serviceWakeLock;
	
	/*
	 * Alarm manager variables, for scheduling intents
	 */
	public static AlarmManager mAlarmManager;
	private PendingIntent scheduleSurvey;
	private PendingIntent scheduleSensor;
	private static PendingIntent scheduleLocation;
	private static PendingIntent scheduleCheck;
	private static PendingIntent triggerSound;
	private static PendingIntent triggerSound2;
	//private PendingIntent surveyIntent;

	/*
	 * Static intent actions
	 */
	public static final String ACTION_SCHEDULE_SURVEY = "INTENT_ACTION_SCHEDULE_SURVEY";

	public static final String ACTION_SCHEDULE_SENSOR = "INTENT_ACTION_SCHEDULE_SENSOR";
	
	public static final String ACTION_SCHEDULE_LOCATION = "INTENT_ACTION_SCHEDULE_LOCATION";
	
	public static final String ACTION_STOP_LOCATIONCONTROL = "INTENT_ACTION_STOP_LOCATIONCONTROL";
	
	public static final String ACTION_SENSOR_DATA = "INTENT_ACTION_SENSOR_DATA";
	
	private static final String ACTION_TRIGGER_SOUND = "INTENT_ACTION_TRIGGER_SOUND";
	
	private static final String ACTION_TRIGGER_SOUND2 = "INTENT_ACTION_TRIGGER_SOUND2";	
	
	private static final String ACTION_SCHEDULE_CHECK = "INTENT_ACTION_SCHEDULE_CHECK";

	public static final String ACTION_TRIGGER_SURVEY = "INTENT_ACTION_TRIGGER_SURVEY";
	
	public static final String ACTION_START_SOUND    =  "INTENT_ACTION_START_SOUND";
	
	public static final String ACTION_CONNECT_BLUETOOTH = "INTENT_ACTION_CONNECT_BLUETOOTH";
	
	public static final String ACTION_DISCONNECT_BLUETOOTH = "INTENT_ACTION_DISCONNECT_BLUETOOTH";
	
	public static final String ACTION_GET_BLUETOOTH_STATE = "INTENT_ACTION_BLUETOOTH_STATE";
	
	public static final String ACTION_BLUETOOTH_RESULT = "INTENT_ACTION_BLUETOOTH_RESULT";
	
	public static final String ACTION_BLUETOOTH_STATE_RESULT = "INTENT_ACTION_BLUETOOTH_STATE_RESULT";
	
	public static final String ACTION_RECONNECT_CHESTSENSOR ="INTENT_ACTION_RECONNECT_CHESTSENSOR";	
	
	public static final String ACTION_START_RECONNECTING ="INTENT_ACTION_START_RECONNECTING";

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
	
    public static final String START_HOUR = "START_HOUR";
    
    public static final String END_HOUR = "END_HOUR";
    
    public static final String START_MIN = "START_MIN";
    
    public static final String END_MIN = "END_MIN";
    
    static String errMSG ="Please check your wifi or dataplan.\r\nThe phone is offline now.";
	
    static boolean IsScheduled = false;	
	
	boolean mExternalStorageAvailable = false;
	
	boolean mExternalStorageWriteable = false;
	
	public static Timer t1=new Timer();
	public static Timer t2=new Timer();
	public static Timer t3=new Timer();
	public static Timer t4=new Timer();
	public static Timer t5=new Timer();
	public static Timer t6=new Timer();
	
		
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
	InternalSensor Accelerometer;
	InternalSensor LightSensor;
	InternalSensor Pressure;
	
	Notification mainServiceNotification;
	public static final int SERVICE_NOTIFICATION_ID = 1;
	
	
	private static SemDevice device;

	
	
	//Variables for storing chest sensor values
	
	double brBeltValue;
	double brECGValue;
	double brImpedanceValue;
	double brConfidence;
	double brEDRValue;
	double hrConfidence;
	double heartRate;
	String motion;
	String orientation;
	
	ActivityRecognitionScan activityRecognition;
	DetectionRemover mDetectionRemover;
	public static int currentUserActivity=9;
	public static boolean IsRetrievingUpdates=false;
	
		
	private SoundPool mSoundPool;
	private int SOUND1=1;
	private int SOUND2=2;
	private HashMap<Integer, Integer> soundsMap;
	/*public StartSound ss;
	public StartSound2 ss2;
	public static StartSound mSound1;
	public static StartSound2 mSound2;*/
	static Timer mTimer;
	int reconnectionAttempts=0;
	/*
	 * (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
			}
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(){
		
		super.onCreate();
		Log.d(TAG,"Starting sensor service");
		mSoundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);
        soundsMap = new HashMap<Integer, Integer>();
        soundsMap.put(SOUND1, mSoundPool.load(this, R.raw.bodysensor_alarm, 1));
        soundsMap.put(SOUND2, mSoundPool.load(this, R.raw.voice_notification, 1));       
		//Setup service context
		serviceContext = this;
		/*mSound1=new StartSound();
		mSound2=new StartSound2();*/
		//Get sensor manager 
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		/*Accelerometer=new InternalSensor(mSensorManager,Sensor.TYPE_ACCELEROMETER,SensorManager.SENSOR_DELAY_NORMAL);
		Accelerometer.run();		
		LightSensor=new InternalSensor(mSensorManager,Sensor.TYPE_LIGHT,SensorManager.SENSOR_DELAY_NORMAL);
		LightSensor.run();*/
		//Pressure=new InternalSensor(mSensorManager,Sensor.TYPE_PRESSURE,SensorManager.SENSOR_DELAY_NORMAL);
		//Pressure.run();
		/*mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL);
		*///Get alarm manager
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		
		//Get location manager
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		/*activityRecognition=new ActivityRecognitionScan(getApplicationContext());
		activityRecognition.startActivityRecognitionScan();*/
		
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
		locationControl = new LocationControl(this, mLocationManager, 1000 * 60, 200,3000);
		
		//sensorControl = new SensorControl(mSensorManager, serviceContext, 30000);


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
		
		Calendar c=Calendar.getInstance();
		SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
		String dateObj =curFormater.format(c.getTime()); 		
		String file_name="Mac_Address"+dateObj+".txt";
		String Path = Environment.getExternalStorageDirectory().getPath() + "/TestResults/"+file_name;
		File file = new File(Path);
		String address =  null;
		if(file.exists())
		{
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(Path));
				StringBuilder sb = new StringBuilder();
				String SCurrentLine = "";
		        while ((SCurrentLine = br.readLine()) != null) 
		        {		            
		            address = SCurrentLine;
		        }
		        if(!(address.equals(null)))
		        {
		        Intent i = new Intent();
		        i.setClass(this, SensorConnections.class);
		        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(i);
				Timer connectionTimer=new Timer();
				connectionTimer.schedule(new ConnectSensor(address),1000*3);
		        //EquivitalRunnable equivitalThread=new EquivitalRunnable(address);
				//equivitalThread.run();
		        }
				
			} 		    
		     catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		
		Intent scheduleCheckConnection = new Intent(SensorService.ACTION_SCHEDULE_CHECK);
		scheduleCheck = PendingIntent.getBroadcast(serviceContext, 0, scheduleCheckConnection , 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+1000*60*1,1000*60*1,scheduleCheck);
		
	   }
	
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		// TODO Auto-generated method stub	
		this.startForeground(SensorService.SERVICE_NOTIFICATION_ID, notification);
		return START_REDELIVER_INTENT;
	}



	BroadcastReceiver checkRequestReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			// TODO Auto-generated method stub
			String action = intent.getAction();
			//Log.d(TAG, "Check Request Recieved");
			if(action.equals(SensorService.ACTION_SCHEDULE_CHECK)){
				int state=SemBluetoothConnection.getState();			
				if(state==0 || state==1)
				{				
					File f=new File(BASE_PATH,"ConnectionAttempts.txt");
					Calendar cal=Calendar.getInstance();
			   		cal.setTimeZone(TimeZone.getTimeZone("US/Central"));
					try {
						writeToFile(f,String.valueOf(cal.getTime())+" "+String.valueOf(reconnectionAttempts)+" "+SemBluetoothConnection.equivitalAddress);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					reconnectionAttempts++;
					Intent i=new Intent(SensorService.ACTION_RECONNECT_CHESTSENSOR);
					serviceContext.sendBroadcast(i);
				}
				
				/*ActivityManager activityManager = (ActivityManager)serviceContext.getSystemService(ACTIVITY_SERVICE);
				Runtime info = Runtime.getRuntime();
			    long freeSize = info.freeMemory();
		        long totalSize= info.totalMemory();
		        long usedSize = totalSize - freeSize;
				Log.d(TAG,String.valueOf(usedSize/1024)+"/"+String.valueOf(totalSize/1024));*/
				
			}
		}
		
	};
        
	
	
	BroadcastReceiver soundRequestReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			// TODO Auto-generated method stub
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_START_SOUND)){
				Log.d(TAG,"Task Scheduled to run Sound Effects");
				startSound();				
			}
			else if(action.equals(SensorService.ACTION_TRIGGER_SOUND))
			{
				playSound(1,1.0f);
				Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		        v.vibrate(1000);   
			}
			else if(action.equals(SensorService.ACTION_TRIGGER_SOUND2))
			{
				playSound(2,1.0f);				 
			}

		}
		
	};
	
		
	
	 public void startSound()
	{  		 
	    /*mTimer=new Timer();
	    ss=new StartSound();
		ss2=new StartSound2();
		mTimer.schedule(ss,1000);
		mTimer.schedule(ss2,1000*19);*/
		 Intent scheduleTriggerSound = new Intent(SensorService.ACTION_TRIGGER_SOUND);
		 Intent scheduleTriggerSound2 = new Intent(SensorService.ACTION_TRIGGER_SOUND2);
		 triggerSound = PendingIntent.getBroadcast(serviceContext, 0, scheduleTriggerSound , 0);
		 triggerSound2 = PendingIntent.getBroadcast(serviceContext, 0, scheduleTriggerSound2 , 0);		 
		 mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime()+1000 ,triggerSound);	
		 mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime()+1000*20 ,triggerSound2);
		
	}
	
	 public class ConnectSensor extends TimerTask
	 {
		 String mAddress;
		 public ConnectSensor(String Mac_Address)
		 {
			 mAddress=Mac_Address;
			 
		 }
		@Override
		public void run() {
			// TODO Auto-generated method stub
			Intent connectChest = new Intent(SensorService.ACTION_CONNECT_CHEST);	
			connectChest.putExtra(SensorService.KEY_ADDRESS,mAddress);
			serviceContext.sendBroadcast(connectChest);
		}
		 
		  
	 }
	/*public class StartSound extends TimerTask
	{
		@Override
		public void run() {			
			// TODO Auto-generated method stub
			//MediaPlayer.create(serviceContext, R.raw.bodysensor_alarm).start();
			playSound(1,1.0f);
			Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
	        v.vibrate(1000);        
	        
		}	
	}
	
	public class StartSound2 extends TimerTask
	{
		@Override
		public void run() {
			// TODO Auto-generated method stub
			playSound(2,1.0f);
		}	
	}*/
	
	
	public void playSound(int sound, float fSpeed) {
        AudioManager mgr = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        float streamVolumeCurrent = mgr.getStreamVolume(AudioManager.STREAM_MUSIC);        
        float volume = streamVolumeCurrent;
        mSoundPool.play(soundsMap.get(sound), volume, volume, 1, 0, fSpeed);  
       
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
		
		/*Intent scheduleSensorIntent = 
				new Intent(SensorService.ACTION_SCHEDULE_SENSOR);
		scheduleSensor = PendingIntent.getBroadcast(
				serviceContext, 0, scheduleSensorIntent, 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 10000, 1000 * 31, scheduleSensor);*/
		
		/*Intent scheduleLocationIntent = new Intent(SensorService.ACTION_SCHEDULE_LOCATION);
		scheduleLocation = PendingIntent.getBroadcast(
				serviceContext, 0, scheduleLocationIntent, 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
				SystemClock.elapsedRealtime() + 10000, 1000 * 60 * 5, scheduleLocation);*/
		
		IntentFilter sensorSchedulerFilter = 
				new IntentFilter(ACTION_SCHEDULE_SENSOR);
		IntentFilter surveySchedulerFilter = 
				new IntentFilter(XMLSurveyActivity.INTENT_ACTION_SURVEY_RESULTS);
		IntentFilter locationSchedulerFilter =
				new IntentFilter(ACTION_SCHEDULE_LOCATION);
		IntentFilter locationInterruptSchedulerFilter =
				new IntentFilter(ACTION_STOP_LOCATIONCONTROL);
		IntentFilter surveyScheduleFilter =
				new IntentFilter(ACTION_SCHEDULE_SURVEY);
		IntentFilter surveyTest =
				new IntentFilter("ACTION_SURVEY_TEST");

		IntentFilter bluetoothConnect = new IntentFilter(ACTION_CONNECT_BLUETOOTH);		
		IntentFilter bluetoothDisconnect = new IntentFilter(ACTION_DISCONNECT_BLUETOOTH);
		IntentFilter bluetoothUpdate = new IntentFilter(ACTION_GET_BLUETOOTH_STATE);
		IntentFilter soundRequest=new IntentFilter(ACTION_START_SOUND);
		IntentFilter checkRequest=new IntentFilter(ACTION_SCHEDULE_CHECK);
		IntentFilter locationFoundFilter = new IntentFilter(LocationControl.INTENT_ACTION_LOCATION);
		IntentFilter sound1=new IntentFilter(ACTION_TRIGGER_SOUND);
		IntentFilter sound2=new IntentFilter(ACTION_TRIGGER_SOUND2);
		//SensorService.this.registerReceiver(alarmReceiver, sensorSchedulerFilter);
		//SensorService.this.registerReceiver(alarmReceiver, surveySchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, locationSchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, locationFoundFilter);
		SensorService.this.registerReceiver(alarmReceiver, locationInterruptSchedulerFilter);
		SensorService.this.registerReceiver(alarmReceiver, surveyScheduleFilter);
		SensorService.this.registerReceiver(alarmReceiver, surveyTest);
		SensorService.this.registerReceiver(soundRequestReceiver,soundRequest);
		SensorService.this.registerReceiver(soundRequestReceiver,sound1);
		SensorService.this.registerReceiver(soundRequestReceiver,sound2);
		SensorService.this.registerReceiver(checkRequestReceiver,checkRequest);
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothConnect);		
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothDisconnect);
		SensorService.this.registerReceiver(bluetoothReceiver, bluetoothUpdate);
		
		
		
		/*Chest Sensor Intent Filter*/
		
		IntentFilter chestSensorData = new IntentFilter(ACTION_CONNECT_CHEST);
		SensorService.this.registerReceiver(chestSensorReceiver,chestSensorData);
		}
	
	
	
	private class ScheduleSurvey extends TimerTask
	{
		int TriggerInterval;
		public ScheduleSurvey(int Time)
		{
			TriggerInterval=Time;
			
		}

				@Override
		public void run() {
					
			// TODO Auto-generated method stub
		  Random rand=new Random();
		  int TriggerTime=rand.nextInt(TriggerInterval)+1;		 
		  Intent i = new Intent(serviceContext, XMLSurveyActivity.class);
		  i.putExtra("survey_name", "RANDOM_ASSESSMENT");
		  i.putExtra("survey_file", "RandomAssessmentParcel.xml");	
		  PendingIntent surveyIntent = PendingIntent.getActivity(SensorService.this, 0,
				                i, Intent.FLAG_ACTIVITY_NEW_TASK);
		  mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
					SystemClock.elapsedRealtime()+1000*60*TriggerTime , surveyIntent);
		}
		
		
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
		//locationControl.cancel();
		//sensorControl.cancel();
		//SensorThread.stopRecording();
		
		File f = new File(BASE_PATH,"SensorServiceEvents.txt");
		Calendar cal=Calendar.getInstance();
		try {
			writeToFile(f,"Destroyed at "+String.valueOf(cal.getTime()));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}	
		
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
		SensorService.this.unregisterReceiver(soundRequestReceiver);
		SensorService.this.unregisterReceiver(checkRequestReceiver);
		mSensorManager.unregisterListener(this);
		mAlarmManager.cancel(scheduleSurvey);
		mAlarmManager.cancel(scheduleSensor);
		mAlarmManager.cancel(scheduleLocation);	
		mAlarmManager.cancel(scheduleCheck);
		mAlarmManager.cancel(triggerSound);
		mAlarmManager.cancel(triggerSound2);
		/*activityRecognition.stopActivityRecognitionScan();
		Accelerometer.stop();
		LightSensor.stop();*/
		//Pressure.stop();
		//mAlarmManager.cancel(surveyIntent);
		
		serviceWakeLock.release();
		CancelTimers();
		setStatus(false);
		
		Log.d(TAG,"Service Stopped.");
		
		super.onDestroy();
		if(device!=null){
		device.stop();
		}
	}
		
	public static void requestLocationUpdates()
	{
		if(IsRetrievingUpdates!=true)
		{
		Intent scheduleLocationIntent = new Intent(SensorService.ACTION_SCHEDULE_LOCATION);
		scheduleLocation = PendingIntent.getBroadcast(serviceContext, 0, scheduleLocationIntent, 0);
		mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,0, 1000 * 60, scheduleLocation);
		}
	}
	
	public static void stopLocationUpdates()
	{
		if(IsRetrievingUpdates!=false)
		{
		Intent locationIntent = new Intent(SensorService.ACTION_STOP_LOCATIONCONTROL);
	    serviceContext.sendBroadcast(locationIntent);
	    mAlarmManager.cancel(scheduleLocation);	
		}
	}
	
	public static void setStatusLocationUpdates(boolean status)
	{
		IsRetrievingUpdates=status;
	}
	
	public boolean getStatusLocationUpdates()
	{
	     return IsRetrievingUpdates;
	}
	
	public static  void setCurrentUserActivity(int Activity,int Confidence)
	{
		currentUserActivity=Activity;
		switch (currentUserActivity) {
		case DetectedActivity.IN_VEHICLE:		
		case DetectedActivity.ON_BICYCLE:		
		case DetectedActivity.ON_FOOT:
			if(Confidence>=75)
			{
			   requestLocationUpdates();
			   setStatusLocationUpdates(true);
			}
		case DetectedActivity.TILTING:				
		case DetectedActivity.STILL:
	    case DetectedActivity.UNKNOWN:
			if(Confidence>=75)
			{
				stopLocationUpdates();
				setStatusLocationUpdates(false);
			}
		
		default:
			  //stopLocationUpdates();
      }
		
	}
	
	BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_SCHEDULE_SENSOR)){
				Log.d(TAG,"Received alarm event - schedule sensor");
				//sensorControl.startRecording(); 
				
			}			
			else if(action.equals(SensorService.ACTION_SENSOR_DATA)){
				/*Log.d(TAG,"Sensor Data Received");
				HashMap<String, String> sensorMap =
						new HashMap<String, String>();
				double[] avg = intent.getDoubleArrayExtra(SensorControl.SENSOR_AVERAGE);
				
				sensorMap.put("xVal", avg[0]+"");
	
				sensorMap.put("yVal", avg[1]+"");	

				sensorMap.put("zVal", avg[2]+"");
				List<NameValuePair> pairs = parseAssocToList(sensorMap);
				httpPostRunnable.post(new HttpPostRequest("", "PHONE_ACCELEROMETER", pairs));*/
			}
			else if(action.equals(SensorService.ACTION_STOP_LOCATIONCONTROL)){
				Log.d(TAG,"Stoping Location Upates");
				locationControl.cancel();
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
			else if(action.equals(SensorService.ACTION_SCHEDULE_SURVEY))
			{
							Log.d(TAG,"Received alarm event - schedule survey");								
							int StartHour=intent.getIntExtra(START_HOUR,0);
							int EndHour=intent.getIntExtra(END_HOUR,0);
							int StartMin=intent.getIntExtra(START_MIN,0);
							int EndMin=intent.getIntExtra(END_MIN,0);
							int Interval=(((EndHour-StartHour)*60)+(EndMin-StartMin))/6;
							int delay=Interval/2;
							int Increment=Interval+delay;
							int TriggerInterval=Interval-delay;
							Log.d(TAG,String.valueOf(Interval));
							
							Date dt1=new Date();				
							dt1.setHours(StartHour);
							dt1.setMinutes(StartMin+delay);
							Date dt2=new Date();
							dt2.setHours(StartHour);
							dt2.setMinutes(StartMin+Increment);				
							Date dt3=new Date();
							dt3.setHours(StartHour);
							dt3.setMinutes(StartMin+Increment+Interval);
							Date dt4=new Date();
							dt4.setHours(StartHour);
							dt4.setMinutes(StartMin+Increment+(Interval*2));
							Date dt5=new Date();
							dt5.setHours(StartHour);
							dt5.setMinutes(StartMin+Increment+(Interval*3));
							Date dt6=new Date();
							dt6.setHours(StartHour);
							dt6.setMinutes(StartMin+Increment+(Interval*4));
							t1.schedule(new ScheduleSurvey(TriggerInterval),dt1);	
							t2.schedule(new ScheduleSurvey(TriggerInterval),dt2);
							t3.schedule(new ScheduleSurvey(TriggerInterval),dt3);
							t4.schedule(new ScheduleSurvey(TriggerInterval),dt4);
							t5.schedule(new ScheduleSurvey(TriggerInterval),dt5);
							t6.schedule(new ScheduleSurvey(TriggerInterval),dt6);
							setStatus(true);
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
	
   public void setBrightness(float value)
   {		
	   int brightnessInt=(int)(value*255);
	   Settings.System.putInt(getContentResolver(),
			   Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
			   Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightnessInt);
   }
   

	
	public static void CancelTimers()
	{
		if(t1!=null&&t2!=null&&t3!=null&&t4!=null&&t5!=null&&t6!=null&&mTimer!=null)
		{
		t1.cancel();
		t1.purge();
		t2.cancel();
		t2.purge();
		t3.cancel();
		t3.purge();
		t4.cancel();
		t4.purge();
		t5.cancel();
		t5.purge();
		t6.cancel();
		t6.purge();	
		mTimer.cancel();
		}
	}	
	
	public static void setStatus(boolean value)
	{
		IsScheduled = value;		
	}
	
	
	public static boolean getStatus()
	{		
		return IsScheduled;
	}
	
	protected void writeLocationToFile(Location l){
		
		String toWrite;
		Calendar cl=Calendar.getInstance();
		SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
		String dateObj =curFormater.format(cl.getTime());
		File f = new File(BASE_PATH,"locations_"+dateObj+".txt");
		
		Calendar cal=Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("US/Central"));	
		toWrite = String.valueOf(cal.getTime())+","+
			l.getLatitude()+","+l.getLongitude()+","+
			l.getAccuracy()+","+l.getProvider()+","+getNameFromType(currentUserActivity);
		if(f != null){
			try {
				writeToFile(f, toWrite);
				sendDatatoServer("locations_"+dateObj,toWrite);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
	}
	
	private String getNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
                
        }
        return "unknown";
    }
	
	
	protected void writeSurveyToFile(String surveyName, 
			HashMap<String, List<String>> surveyData, long time) throws IOException{
		Calendar cl=Calendar.getInstance();
		SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
		String dateObj =curFormater.format(cl.getTime());
		File f = new File(BASE_PATH,surveyName+"_"+dateObj+".txt");
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
				sb.append("-1");
			else{
				for(int j = 0; j < data.size(); j++){
					sb.append(data.get(j));
					if(i != data.size()-1)sb.append("");
				}
			}
			if(i != sorted.size()-1) sb.append(",");
		}

		sb.append("\n");
		sendDatatoServer(surveyName+"_"+dateObj,sb.toString());
		writeToFile(f,sb.toString());
	}
	
	protected void writeToFile(File f, String toWrite) throws IOException{
		FileWriter fw = new FileWriter(f, true);
		fw.write(toWrite+'\n');		
        fw.flush();
		fw.close();
	}
	
	
	
	
	
	
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

//------------------------------------------Wrist Sensor Code Starts From Here ------------------------------------------------------

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
						Intent h=new Intent("statechange");
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
	
//------------------------------------------Chest Sensor Code Starts From Here ------------------------------------------------------
	
	BroadcastReceiver chestSensorReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(action.equals(SensorService.ACTION_CONNECT_CHEST)){
				Toast.makeText(getApplicationContext(),"Intent Received",Toast.LENGTH_LONG).show();
				String address=intent.getStringExtra(KEY_ADDRESS);				
				
				/*try
				{
					device = new SemDevice();
					device.setSummaryDataEnabled(true);
				} 
				catch (BadLicenseException e1)
				{
					Toast.makeText(getApplicationContext(),"ERROR:License Code and Developer Name don't match",Toast.LENGTH_LONG).show();
					return;
				}*/		
				EquivitalRunnable equivitalThread=new EquivitalRunnable(address);
				equivitalThread.run();
				Calendar c=Calendar.getInstance();
				SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
				String dateObj =curFormater.format(c.getTime()); 		
				String file_name="Mac_Address"+dateObj+".txt";			
				
				Calendar cal=Calendar.getInstance();
				cal.setTimeZone(TimeZone.getTimeZone("US/Central"));			
				
		        File f = new File(BASE_PATH,file_name);		
				
				if(f != null){
					try {
						writeToFile(f, address);
					} catch (IOException e) {
						e.printStackTrace();
					}
							
					
				}	
				
			    //connectToDevice(address);
			}
		}
		
	};

	private void connectToDevice(String address) 
	{
		Toast.makeText(getApplicationContext(), "Trying to connect to the device",Toast.LENGTH_LONG).show();
	   	Log.d(TAG,"Entered connectToDevice Method");
		// TODO Auto-generated method stub
	    device.addSummaryEventListener(this);	   	
		device.setSummaryDataEnabled(true);
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
				arg1.getSummary().getQualityConfidence().getBreathingRateConfidence(),arg1.getSummary().getGalvanicSkinResistance());
		
	}

	private void updateSummary(String motion, String bodyPosition,
			double beltSensorRate, double ecgDerivedRate, double impedanceRate,
			double ecgRate, double beltQuality, double ecgQuality,
			double impedanceQuality, double heartRateConfidence,
			double breathingRateConfidence,double GSR) {
		// TODO Auto-generated method stub
		 String dataFromChestSensor=motion+","+bodyPosition+","+String.valueOf(beltSensorRate)+","+String.valueOf(ecgDerivedRate)+","+
				 String.valueOf(impedanceRate)+","+String.valueOf(ecgRate)+","+String.valueOf(beltQuality)+","+String.valueOf(ecgQuality)+","+
				 String.valueOf(impedanceQuality)+","+String.valueOf(heartRateConfidence)+","+String.valueOf(breathingRateConfidence)+","+String.valueOf(GSR);	
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
		//Toast.makeText(serviceContext,"Trying to write to the file",Toast.LENGTH_LONG).show();
		Calendar c=Calendar.getInstance();
		SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
		String dateObj =curFormater.format(c.getTime()); 		
		String file_name="chestsensor_"+dateObj+".txt";
		
		
		Calendar cal=Calendar.getInstance();
		cal.setTimeZone(TimeZone.getTimeZone("US/Central"));			
		
        File f = new File(BASE_PATH,file_name);		
		String dataToWrite = String.valueOf(cal.getTime())+","+chestSensorData;
		sendDatatoServer("chestsensor_"+dateObj,dataToWrite);
		if(f != null){
			try {
				writeToFile(f, dataToWrite);
			} catch (IOException e) {
				e.printStackTrace();
			}
					
			
		}	
		
		
		
	}


//---------------------------------------Code to upload data to the server----------------------------------------------//
	
	public static void sendDatatoServer(String FileName,String DataToSend)
	{
		if (checkDataConnectivity())
    	{

        HttpPost request = new HttpPost("http://babbage.cs.missouri.edu/~rs79c/Android/writeStrToFile.php");
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        //file_name 
        params.add(new BasicNameValuePair("file_name",FileName));
        //data
        params.add(new BasicNameValuePair("data",DataToSend+"\n"));

        
        try {
            request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
            HttpResponse response = new DefaultHttpClient().execute(request);
            if(response.getStatusLine().getStatusCode() == 200){
                String result = EntityUtils.toString(response.getEntity());
                //Toast.makeText(serviceContext,"Data Point Successfully Uploaded", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            
           Toast.makeText(serviceContext,"Error during HTTP POST REQUEST",Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    	}
    	else Toast.makeText(serviceContext, errMSG, Toast.LENGTH_LONG).show();
    }
		
	
	 public static boolean checkDataConnectivity() {
	    	ConnectivityManager connectivity = (ConnectivityManager) serviceContext
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (connectivity != null) {
				NetworkInfo[] info = connectivity.getAllNetworkInfo();
				if (info != null) {
					for (int i = 0; i < info.length; i++) {
						if (info[i].getState() == NetworkInfo.State.CONNECTED) {
							return true;
						}
					}
				}
			}
			return false;
		}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		
		Sensor sensor = event.sensor;
	    Calendar c=Calendar.getInstance();
   		SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
   		String dateObj =curFormater.format(c.getTime()); 
   		Calendar cal=Calendar.getInstance();
   		cal.setTimeZone(TimeZone.getTimeZone("US/Central"));
   		
   		if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) 
	      {	
	    		String Acclerometer_Values = String.valueOf(cal.getTime())+","+event.values[0]+","+event.values[1]+","+event.values[2];
	    		String file_name="Accelerometer_"+dateObj+".txt";
	            File f = new File(SensorService.BASE_PATH,file_name);
	            //sendDatatoServer("Accelerometer_"+dateObj,Acclerometer_Values);
	    		try {
					writeToFile(f,Acclerometer_Values);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	      
	      }
   			 else if (sensor.getType() == Sensor.TYPE_LIGHT) 
	        {
	            //TODO: get values 
   				 
   				 //Changes screen brightness based on surrounding  light
   				float lux=event.values[0];	        	
	        	if(lux<500)
	        	{
	        		setBrightness(0.3F);
	        	}
	        	else if(lux>500 && lux<10000)
	        	{
	        		setBrightness(0.6F);			        		
	        	}
	        	
	        	else if(lux>10000 && lux <25000)
	        	{
	        		
	        		setBrightness(0.8F);
	        	}
	        	
	        	else if(lux>25000)
	        	{
	        		
	        		setBrightness(1F);
	        	}
	        	String LightIntensity= String.valueOf(cal.getTime())+","+event.values[0];
	        	String file_name="LightSensor_"+dateObj+".txt";
	            File f = new File(SensorService.BASE_PATH,file_name);
	            ///sendDatatoServer("LightSensor_"+dateObj,LightIntensity);
	    		try {
					writeToFile(f,LightIntensity);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	    				        
	        }
	        else if(sensor.getType()==Sensor.TYPE_PRESSURE){				        	
	        	
	        	/*String Pressure= String.valueOf(cal.getTime())+","+event.values[0];
	        	String file_name="PressureSensor_"+dateObj+".txt";
	            File f = new File(SensorService.BASE_PATH,file_name);
	            //sendDatatoServer("PressureSensor_"+dateObj,Pressure);
	    		try {
					writeToFile(f,Pressure);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
	        	
	        }
	     }
	
	
 }

