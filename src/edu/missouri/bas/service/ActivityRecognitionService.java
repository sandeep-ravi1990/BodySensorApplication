package edu.missouri.bas.service;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;


import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ActivityRecognitionService extends IntentService{

private static final String TAG ="ActivityRecognition";
public final static String BASE_PATH = "sdcard/TestResults/";
ActivityRecognitionResult result;
public static int currentUserActivity=9;
public static boolean IsRetrievingUpdates=false;
PendingIntent scheduleLocation;
AlarmManager mAlarmManager;

public ActivityRecognitionService() {
super("ActivityRecognitionService");
}

/**
* Google Play Services calls this once it has analysed the sensor data
*/
@Override
protected void onHandleIntent(Intent intent) {
   if (ActivityRecognitionResult.hasResult(intent)) {
	   result=null;
	   result = ActivityRecognitionResult.extractResult(intent);
	   setCurrentUserActivity(result.getMostProbableActivity().getType(),result.getMostProbableActivity().getConfidence());
		//Log.d(TAG, "ActivityRecognitionResult: "+getFriendlyName(result.getMostProbableActivity().getType(),result.getMostProbableActivity().getConfidence()));

      }
}

/**
* When supplied with the integer representation of the activity returns the activity as friendly string
* @param type the DetectedActivity.getType()
* @return a friendly string of the
*/
public  void requestLocationUpdates()
{
	if(IsRetrievingUpdates!=true)
	{
	Intent scheduleLocationIntent = new Intent(SensorService.ACTION_SCHEDULE_LOCATION);
	scheduleLocation = PendingIntent.getBroadcast(this, 0, scheduleLocationIntent, 0);
	mAlarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,0, 1000 * 60, scheduleLocation);
	}
}

public void stopLocationUpdates()
{
	if(IsRetrievingUpdates!=false)
	{
	Intent locationIntent = new Intent(SensorService.ACTION_STOP_LOCATIONCONTROL);
    this.sendBroadcast(locationIntent);
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

public  void setCurrentUserActivity(int Activity,int Confidence)
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
			requestLocationUpdates();
			setStatusLocationUpdates(true);
		}
	
	default:
		  //stopLocationUpdates();
  }
	
}

@Override
public void onCreate() {
	// TODO Auto-generated method stub
	super.onCreate();
	mAlarmManager=(AlarmManager)getSystemService(Context.ALARM_SERVICE);
}
}
