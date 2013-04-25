package edu.missouri.bas;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import edu.missouri.bas.activities.DeviceListActivity;
import edu.missouri.bas.bluetooth.BluetoothRunnable;
import edu.missouri.bas.service.SensorService;
import edu.missouri.bas.survey.XMLSurveyMenu;

public class MainActivity extends ListActivity {

	private boolean mIsRunning=false;
	
	private BluetoothAdapter mAdapter= BluetoothAdapter.getDefaultAdapter();	
	private final static String TAG = "SensorServiceActivity";	
	public static final int REQUEST_ENABLE_BT = 3;
	public static final int INTENT_SELECT_DEVICES = 0;
	public static final int INTENT_DISCOVERY = 1;
	public static final int INTENT_VIEW_DEVICES = 2;	
	protected static final int START = 0;
	protected static final int STOP = 1;
	protected static final int SURVEY = 2;	
	protected static final int CONNECTIONS = 3;
	public  final MainActivity thisActivity = this;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
       
        
    	String[] options = {"Start Service", "Stop Service", "Survey Menu",
		"External Sensor Connections"};
    	
    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
    			android.R.layout.simple_list_item_1, options);
    	
    	setListAdapter(adapter);    
        ListView listView = getListView();        
        listView.setOnItemClickListener(new OnItemClickListener() {

			
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
		    	switch(position){
	    		case START:
	    			startSService();
	    			break;
	    		case STOP:
	    			stopSService();
	    			break;
	    		case SURVEY: 
	    			startSurveyMenu();
	    			break;	    		
	    		case CONNECTIONS:
	    			startConnections();
	    			break;
		    	}
			}
        	
        });

        
        
        if(mAdapter == null){
        	Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
        	finish();
        	return;
        }
        
        if (!mAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableIntent);
        }
        
       
        
        startSService();
                
        
    }
    
    private void startSurveyMenu(){
		Intent i = new Intent(getApplicationContext(), XMLSurveyMenu.class);
		startActivity(i);
    }
    
    private void startConnections(){
		Intent i = new Intent(getApplicationContext(), SensorConnections.class);
		startActivity(i);
    }
    
    private void stopSService() {
    	mIsRunning = false;    	
    	this.stopService(new Intent(MainActivity.this,SensorService.class));
    	 
    }
    private void startSService() {
        if (! mIsRunning) {
            mIsRunning = true;            
	         this.startService(new Intent(MainActivity.this,SensorService.class));
	            
            
        }
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("SensorServiceActivity", "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session

            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Bluetooth could not be enabled, exiting",
                		Toast.LENGTH_LONG).show();
                finish();
            }
            break;
		case MainActivity.INTENT_SELECT_DEVICES:
			// When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) 
            {
				String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);				
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
				Toast.makeText(getApplicationContext(), "No device is selected",Toast.LENGTH_LONG).show();				
				
			}
			
		   break;
			
				
        }
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.bs_menu, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(item.getItemId() == R.id.Connect){
			if(mAdapter.isEnabled())
			{			
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, MainActivity.INTENT_SELECT_DEVICES);
            return true;
            }
			else
			{			    
				Toast.makeText(getApplicationContext(),"Enable BT before connecting",Toast.LENGTH_LONG).show();			
			}					
		}
		
		else if (item.getItemId() == R.id.Enable){
			if(mAdapter.isEnabled())
			{
				Toast.makeText(getApplicationContext(),"Bluetooth is already enabled ",Toast.LENGTH_LONG).show();
				
			}
			else
			{
				
				turnOnBt();				
			}
			
            return true;
		}
		
		else if (item.getItemId() == R.id.Disable){
			mAdapter.disable();
			Toast.makeText(getApplicationContext(),"Bluetooth is disabled",Toast.LENGTH_LONG).show();			
            return true;
		}
		return false;
	}
	
	public boolean turnOnBt() {
		// TODO Auto-generated method stub
		Intent Enable_Bluetooth=new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(Enable_Bluetooth, 1234);
		return true;
	}
	
	

	@Override
	public void onDestroy(){
	
		super.onDestroy();
	}
	
	
}

