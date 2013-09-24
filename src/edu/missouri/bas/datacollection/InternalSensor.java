package edu.missouri.bas.datacollection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import edu.missouri.bas.service.SensorService;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class InternalSensor implements Runnable, SensorEventListener {
	
	static SensorManager mSensorManager;
	int SensorType;
	int SamplingRate;
	static int Count=0;
	static String Temp=null;
	List<String> dataPoints=new ArrayList<String>();

	public InternalSensor(SensorManager sensorManager,int sensorType,int samplingRate)
	{
		mSensorManager = sensorManager;
		SensorType=sensorType;
		SamplingRate=samplingRate;
	}	
	
	@Override
	public void run() 
	{  // TODO Auto-generated method stub
		setup(SensorType,SamplingRate);		
	}

	public void setup(int sensorType, int samplingRate) 
	{
		// TODO Auto-generated method stub
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(sensorType),samplingRate);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		SensorService mSensorService=new SensorService();			
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
   				/*float lux=event.values[0];	        	
	        	if(lux<500)
	        	{
	        		mSensorService.setBrightness(0.3F);
	        	}
	        	else if(lux>500 && lux<10000)
	        	{
	        		mSensorService.setBrightness(0.6F);			        		
	        	}
	        	
	        	else if(lux>10000 && lux <25000)
	        	{
	        		
	        		mSensorService.setBrightness(0.8F);
	        	}
	        	
	        	else if(lux>25000)
	        	{
	        		
	        		mSensorService.setBrightness(1F);
	        	}*/
	        	String LightIntensity= String.valueOf(cal.getTime())+","+event.values[0];
	        	String file_name="LightSensor_"+dateObj+".txt";
	            File f = new File(SensorService.BASE_PATH,file_name);
	            if(dataPoints.size()!=100)
	            {	   
                       	dataPoints.add(LightIntensity+";");
	            	if(dataPoints.size()==90)
	            	{
	            	    List<String> subList = dataPoints.subList(0,46);
	     	            String data=subList.toString();	     	            
	     	            String formatedData=data.replaceAll("[\\[\\]]","");		     	            
	     	            //sendDatatoServer("LightSensor_18"+dateObj,formatedData);
	     	            subList.clear();  
	     	         }	            		            			            	
	            }
	    		try {
					writeToFile(f,LightIntensity);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	    				        
	        }
	        else if(sensor.getType()==Sensor.TYPE_PRESSURE){				        	
	        	
	        	String Pressure= String.valueOf(cal.getTime())+","+event.values[0];
	        	String file_name="PressureSensor_"+dateObj+".txt";
	            File f = new File(SensorService.BASE_PATH,file_name);
	            //sendDatatoServer("PressureSensor_"+dateObj,Pressure);
	    		try {
					writeToFile(f,Pressure);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	
	        }
	}
	
	protected static void writeToFile(File f, String toWrite) throws IOException{
		FileWriter fw = new FileWriter(f, true);
		fw.write(toWrite+'\n');		
        fw.flush();
		fw.close();
	}
	
	
	
	protected static boolean checkDataConnectivity() {    	
		boolean value=SensorService.checkDataConnectivity();
		return value;
	}
	
	public void stop()
	{
		mSensorManager.unregisterListener(this);
	}	
	
	
	
	public static void sendDatatoServer(String FileName,String DataToSend)
	{
		if(checkDataConnectivity())
		{
		
        HttpPost request = new HttpPost("http://babbage.cs.missouri.edu/~rs79c/Android/Test/writeArrayToFile.php");
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        //file_name 
        params.add(new BasicNameValuePair("file_name",FileName));        
        //data                       
        params.add(new BasicNameValuePair("data",DataToSend));
        try {
        	        	
            request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
            HttpResponse response = new DefaultHttpClient().execute(request);
            if(response.getStatusLine().getStatusCode() == 200){
                String result = EntityUtils.toString(response.getEntity());
                Log.d("Sensor Data Point Info",result);                
               // Log.d("Wrist Sensor Data Point Info","Data Point Successfully Uploaded!");
            }
        } catch (Exception e) {
            
            e.printStackTrace();
        }
	  }
    	
    else {
    	Log.d("Sensor Data Point Info","No Network Connection:Data Point was not uploaded");
        
    	 }
    }
}
