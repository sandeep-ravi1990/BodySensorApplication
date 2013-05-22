package edu.missouri.bas.bluetooth.affectiva;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.widget.Toast;

import edu.missouri.bas.service.modules.sensors.AccelerometerReading;

public class AffectivaPacket {
	private AccelerometerReading accelerometer;
	private float eda;
	private float battery;
	private float temperature;
	private String sequenceNum;
	private final static String BASE_PATH = "sdcard/TestResults/";
	
	public AffectivaPacket(String seq, AccelerometerReading accel, float battery,
			float temp, float eda){
		this.sequenceNum = seq;
		this.accelerometer = accel;
		this.battery = battery;
		this.temperature = temp;
		this.eda = eda;
	}
	
	public static  AffectivaPacket packetFromString(String s){
		String[] splitString = s.split(",");
		if(splitString.length != 7) return null;
		else{
			String seq = splitString[0];			
			String datatoWrite=System.currentTimeMillis()+","+splitString[3]+","+
							splitString[2]+","+
							splitString[1]+","+
					        splitString[4]+","+
					        splitString[5]+","+
					        splitString[6];
			/*Code to write wrist sensor data to a CSV file */
			Calendar c=Calendar.getInstance();
			SimpleDateFormat curFormater = new SimpleDateFormat("MMMMM_dd"); 
			String dateObj =curFormater.format(c.getTime()); 
			String file_name="wristsensor"+dateObj+".txt";				
	        File f = new File(BASE_PATH,file_name);	
			try {
				writeToFile(f,datatoWrite);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			return new AffectivaPacket(seq,
					new AccelerometerReading(
							Float.parseFloat(splitString[3]),
							Float.parseFloat(splitString[2]),
							Float.parseFloat(splitString[1])),
					Float.parseFloat(splitString[4]),
					Float.parseFloat(splitString[5]),
					Float.parseFloat(splitString[6]));
			
		}
		
	}
	
	
	
	protected static void writeToFile(File f, String toWrite) throws IOException{
		FileWriter fw = new FileWriter(f, true);
		fw.write(toWrite+'\n');		
        fw.flush();
		fw.close();
	}
	
	public AccelerometerReading getAccelerometer(){
		return accelerometer;
	}
	
	public float getEda(){
		return eda;
	}
	
	public float getBattery(){
		return battery;
	}
	
	public float getTemperature(){
		return temperature;
	}
	
	public String getSequenceNum(){
		return sequenceNum;
	}
	
	@Override
	public  String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append(sequenceNum+" ");
		sb.append(accelerometer.toString());
		sb.append(" EDA (microsiemens): "+eda);
		sb.append(" Battery: "+battery);
		sb.append(" Temperature (celsius): "+temperature);
		return sb.toString();
	}

	public String toCSV() {
		StringBuilder sb = new StringBuilder();
		sb.append(sequenceNum+",");
		sb.append(accelerometer.toCSV());
		sb.append(","+eda);
		sb.append(","+battery);
		sb.append(","+temperature);
		return sb.toString();
	}
	
	
}
