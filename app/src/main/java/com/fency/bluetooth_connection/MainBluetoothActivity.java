package com.fency.bluetooth_connection;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainBluetoothActivity extends AppCompatActivity {

    private static final int ENABLE_BT_REQUEST_CODE = 1;
    private static final int REQUEST_DISCOVERABLE_CODE = 2;
    private static final int ACTION_REQUEST_MULTIPLE_PERMISSION = 3;


    private BluetoothAdapter bluetoothAdapter;
    private Set<BluetoothDevice> pairedDevices, nearbyDevices;
    private ArrayList<String> aListPaired, aListNearby;
    private ListView listViewPaired, listViewNearby;
    private FloatingActionButton blueBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_bluetooth);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listViewPaired = findViewById(R.id.lv_paired);
        listViewNearby = findViewById(R.id.lv_nearby);
        aListPaired = new ArrayList<>();
        aListNearby = new ArrayList<>();

        blueBtn = findViewById(R.id.fab);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(checkCompatibility()){
            initialize();
        }
        else {
            Toast.makeText(getApplicationContext(),
                    "This device doesn’t support Bluetooth",
                    Toast.LENGTH_SHORT).show();
            //quit
        }
    }//end onCreate

    private boolean checkCompatibility(){
        boolean res = true;
        if (bluetoothAdapter == null) {
            res = false;
        }
        return res;
    }
    private void initialize(){
        checkPermissions();
        initButtons();
        //enable BT
        //enableBluetooth();
        //get paired devices list
        listPairedDevices();
        //enter in discoverable mode
        setDiscoverable();
        // register a broadcastReceiver to be notified when a new device is discovered
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(broadcastReceiver, filter);

    }//end initialize

    private void checkPermissions(){
        // Necessary for Android 6.0.1 devices, API 23 or greater
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int pCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            pCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            pCheck += this.checkSelfPermission("Manifest.permission.BLUETOOTH_ADMIN");
            pCheck += this.checkSelfPermission("Manifest.permission.BLUETOOTH");
            if (pCheck != 0) {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH}, ACTION_REQUEST_MULTIPLE_PERMISSION);
            }
        }
    }

    private void initButtons(){
        blueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!bluetoothAdapter.isEnabled()){
                    setDiscoverable();
                }
                else {
                    if (bluetoothAdapter.isDiscovering()) {
                        Snackbar.make(view, "Still scanning!", Snackbar.LENGTH_LONG)
                                .setAction("Action2", null).show();
                    } else {
                        //start discovering nearby devices
                        if (startDiscovery()) {
                            blueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
                            Snackbar.make(view, "Scanning for nearby devices...", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        } else
                            blueBtn.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                    }
                }
            }
        });
    }

    /*private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, ENABLE_BT_REQUEST_CODE);
            Toast.makeText(getApplicationContext(), "Enabling Bluetooth!", Toast.LENGTH_LONG).show();
        }
    }*/

    private void listPairedDevices(){
        pairedDevices = bluetoothAdapter.getBondedDevices();

        for(BluetoothDevice device : pairedDevices) {
            aListPaired.add(device.getName() + "\n" + device.getAddress());
            Log.i("BT", device.getName() + "\n" + device.getAddress());
        }

        listViewPaired.setAdapter(new ArrayAdapter<>(
                getApplicationContext(),
                android.R.layout.simple_list_item_1,
                aListPaired));
    }

    private void setDiscoverable(){
        Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        //Specify how long the device will be discoverable for, in seconds.//
        discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(discoveryIntent);
    }
    private boolean startDiscovery(){
        listViewPaired.setVisibility(View.INVISIBLE);
        return bluetoothAdapter.startDiscovery();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_bluetooth, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        //Check what request we’re responding to//
        if (requestCode == ENABLE_BT_REQUEST_CODE) {
            //If the request was successful…//
            if (resultCode == Activity.RESULT_OK) {
                //...then display the following toast.//
                Toast.makeText(getApplicationContext(),
                        "Bluetooth has been enabled",
                        Toast.LENGTH_SHORT).show();
                initialize();
            }
            //If the request was unsuccessful...//
            if(resultCode == RESULT_CANCELED){
                //...then display this alternative toast.//
                Toast.makeText(getApplicationContext(),
                        "An error occurred while attempting to enable Bluetooth",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    @Override
    protected void onPause()
    {
        super.onPause();
        if (bluetoothAdapter!=null)
            bluetoothAdapter.cancelDiscovery();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter!=null)
            bluetoothAdapter.cancelDiscovery();
        unregisterReceiver(broadcastReceiver);

    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Whenever a remote Bluetooth device is found...//
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //….retrieve the BluetoothDevice object and its EXTRA_DEVICE field,
                //which contains information about the device’s characteristics and capabilities//
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (!aListNearby.contains(device.getName() + "\n" + device.getAddress())) {
                    aListNearby.add(device.getName() + "\n" + device.getAddress());
                    Toast.makeText(getApplicationContext(),
                            "found device "+device.getName()+" | MAC address: "+device.getAddress(),
                            Toast.LENGTH_SHORT).show();
                    listViewNearby.setAdapter(new ArrayAdapter<>(
                            getApplicationContext(),
                            android.R.layout.simple_list_item_1,
                            aListNearby));
                }


            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                Toast.makeText(getApplicationContext(),
                        "cerco dispositivi vicini...",
                        Toast.LENGTH_SHORT).show();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                Toast.makeText(getApplicationContext(),
                        "fine ricerca.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    };
}
