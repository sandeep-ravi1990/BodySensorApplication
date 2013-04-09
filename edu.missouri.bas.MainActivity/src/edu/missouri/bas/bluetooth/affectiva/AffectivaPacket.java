package edu.missouri.bas.bluetooth.affectiva;

import edu.missouri.bas.service.modules.sensors.AccelerometerReading;

public class AffectivaPacket {
	private AccelerometerReading accelerometer;
	private float eda;
	private float battery;
	private float temperature;
	private String sequenceNum;
	
	public AffectivaPacket(String seq, AccelerometerReading accel, float battery,
			float temp, float eda){
		this.sequenceNum = seq;
		this.accelerometer = accel;
		this.battery = battery;
		this.temperature = temp;
		this.eda = eda;
	}
	
	public static AffectivaPacket packetFromString(String s){
		String[] splitString = s.split(",");
		if(splitString.length != 7) return null;
		else{
			String seq = splitString[0];

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
	public String toString(){
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
