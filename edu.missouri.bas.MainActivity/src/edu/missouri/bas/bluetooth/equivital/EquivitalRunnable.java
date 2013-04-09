package edu.missouri.bas.bluetooth.equivital;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.util.Log;
import edu.missouri.bas.bluetooth.BluetoothRunnable;

public class EquivitalRunnable extends BluetoothRunnable {

	public EquivitalRunnable(Handler handler, BluetoothDevice device,
			UUID uuid, int mode, int type,
			File outputFile) {
		super(handler, device, uuid, mode, type, outputFile);
	}

	@Override
	protected void connectedFunction() {
		byte[] rawPacket = new byte[6];
		int read = 0;
		try {
			while(read < 6){
				read += mBluetoothSocket.getInputStream().read(rawPacket, read, 6-read);
			}
			if(read > 0) bufferedWriter.write(new String(rawPacket));
			Log.d("Equivital", "Read: "+ new String(rawPacket, 0, read)+ " - "+read);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			lostConnection();
			e.printStackTrace();
		}
		
	}
}
