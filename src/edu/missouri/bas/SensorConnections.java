package edu.missouri.bas;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.missouri.bas.activities.DeviceListActivity;
import edu.missouri.bas.bluetooth.BluetoothRunnable;
import edu.missouri.bas.service.SensorService;

public class SensorConnections extends Activity {
	TextView tvSetWristStatus;
	TextView tvSetChestStatus;
	Button 	 btnConnectWrist;
	Button   btnConnectChest;
	ToggleButton tbBluetooth;
	public static final int INTENT_CONNECT_WRIST = 0;
	public static final int INTENT_CONNECT_CHEST = 1;
	
	BluetoothAdapter btAdapter=BluetoothAdapter.getDefaultAdapter();
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connections);
		tbBluetooth = (ToggleButton) findViewById(R.id.tbBluetooth);
		btnConnectWrist=(Button)findViewById(R.id.btnConnectWrist);
		btnConnectChest=(Button)findViewById(R.id.btnConnectChest);
		if(btAdapter.enable())
		{
			tbBluetooth.setChecked(true);
		}
		
		else
		{
			
			tbBluetooth.setChecked(false);
		}
		
		
		tbBluetooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
		    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		        if (isChecked) 
		        {
		            turnOnBt();
		        } 
		        else 
		        {
		        	btAdapter.disable();
			        
		        }
		    }
		});
		
		btnConnectWrist.setOnClickListener(new View.OnClickListener(){

			public void onClick(View v) {
				
				if(btAdapter.isEnabled())
				{			
				Intent serverIntent = new Intent(getApplicationContext(),DeviceListActivity.class);
	            startActivityForResult(serverIntent,SensorConnections.INTENT_CONNECT_WRIST);	            
	            }
				else
				{
				    
					Toast.makeText(getApplicationContext(),"Enable BT before connecting",Toast.LENGTH_LONG).show();
					
					
				}
				
				
			}
        });
		
		btnConnectChest.setOnClickListener(new View.OnClickListener(){

			public void onClick(View v) {
        		
				if(btAdapter.isEnabled())
				{			
				Intent serverIntent = new Intent(getApplicationContext(),DeviceListActivity.class);
	            startActivityForResult(serverIntent,SensorConnections.INTENT_CONNECT_CHEST);	            
	            }
				else
				{
				    
					Toast.makeText(getApplicationContext(),"Enable BT before connecting",Toast.LENGTH_LONG).show();
					
					
				}
				
			}
        });
		
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("SensorServiceActivity", "onActivityResult " + resultCode);
        switch (requestCode) {
        
		case SensorConnections.INTENT_CONNECT_WRIST:
			// When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) 
            {
				String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice device = btAdapter.getRemoteDevice(address);
				if(device.getName().contains("AffectivaQ"))
				{
				Intent connectIntent = new Intent(SensorService.ACTION_CONNECT_BLUETOOTH);
				connectIntent.putExtra(SensorService.INTENT_EXTRA_BT_DEVICE,address);
				connectIntent.putExtra(SensorService.INTENT_EXTRA_BT_MODE,
						BluetoothRunnable.BluetoothMode.CLIENT);
				connectIntent.putExtra(SensorService.INTENT_EXTRA_BT_TYPE,
						BluetoothRunnable.BluetoothSocketType.INSECURE);
				connectIntent.putExtra(SensorService.INTENT_EXTRA_BT_UUID,
						"00001101-0000-1000-8000-00805F9B34FB");
				this.sendBroadcast(connectIntent);
				}
				else
				{
					
					Toast.makeText(getApplicationContext(),"Please select the devices with 'Affectiva' Prefix",Toast.LENGTH_LONG).show();
				
				}
			}
			else
			{
				Toast.makeText(getApplicationContext(), "No device is selected",Toast.LENGTH_LONG).show();				
				
			}
			
		   break;
			
		   
		case SensorConnections.INTENT_CONNECT_CHEST:
			if (resultCode == Activity.RESULT_OK) 
            {
				String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				BluetoothDevice device = btAdapter.getRemoteDevice(address);
				if(device.getName().contains("EQ"))
				{
				
					
					
				
				}
				else
				{
					
					Toast.makeText(getApplicationContext(),"Please select the devices with 'EQ' Prefix",Toast.LENGTH_LONG).show();
				
				}
			}
			else
			{
				Toast.makeText(getApplicationContext(), "No device is selected",Toast.LENGTH_LONG).show();				
				
			}
			
		   break;
				
        }
    }
	
	public boolean turnOnBt() {
		// TODO Auto-generated method stub
		Intent Enable_Bluetooth=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(Enable_Bluetooth, 1234);
		return true;
	}

}
