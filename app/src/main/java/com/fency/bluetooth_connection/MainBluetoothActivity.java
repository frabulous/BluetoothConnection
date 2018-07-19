package com.fency.bluetooth_connection;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainBluetoothActivity extends AppCompatActivity {

    private static final UUID appUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int ENABLE_BT_REQUEST_CODE = 1, ACTION_REQUEST_MULTIPLE_PERMISSION = 3;

    private static final int STATE_BT_OFF = -1, STATE_NOT_DISCOVERABLE = 0, STATE_DISCOVERABLE = 2,
            STATE_SCANNING = 3, STATE_CONNECTING = 4, STATE_CONNECTED = 5;

    private int state_current = -5;

    private TextView tv_state;
    private String myMacAddress, opponentMacAddress, myName;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private BluetoothSocket bluetoothSocket;
    private ArrayList<BluetoothDevice> aListPaired, aListNearby;
    private ListView listViewPaired, listViewNearby;
    private FloatingActionButton discoverBtn, serverBtn;
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
        setListOnClickListener(listViewPaired);
        setListOnClickListener(listViewNearby);

        discoverBtn = findViewById(R.id.fab);
        serverBtn = findViewById(R.id.fab2);
        tv_state = findViewById(R.id.tv_state);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        myMacAddress = android.provider.Settings.Secure
                .getString(getApplicationContext().getContentResolver(), "bluetooth_address");

        if(checkCompatibility()){
            initialize();

            //get paired devices list
            listPairedDevices();
        }
        else {
            Toast.makeText(getApplicationContext(),
                    "This device does not support Bluetooth",
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
        goToState(getStartingState());
        initButtons();

        myName = bluetoothAdapter.getName();

        //enter in discoverable mode (and, eventually, enable BT)
        setDiscoverable();

        // register a broadcastReceiver to be notified about bluetooth events
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

        registerReceiver(broadcastReceiver, filter);

        tv_state.setText("stato: "+ state_current);

    }//end initialize

    private void checkPermissions(){
        // Necessary for Android 6.0.1 devices, API 23 or greater
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int pCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            pCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            pCheck += this.checkSelfPermission("Manifest.permission.BLUETOOTH_ADMIN");
            pCheck += this.checkSelfPermission("Manifest.permission.BLUETOOTH");
            if (pCheck != 0) {
                this.requestPermissions(
                        new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.BLUETOOTH},
                        ACTION_REQUEST_MULTIPLE_PERMISSION);
            }
        }
    }

    private int getStartingState(){
        int state;
        if (!bluetoothAdapter.isEnabled())
            state = STATE_BT_OFF;
        else {
            //TODO: check if this condition is the right one
            if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
                state = STATE_NOT_DISCOVERABLE;
            else
                state = STATE_DISCOVERABLE;
        }
        return state;
    }

    private void goToState(int targetState){
        state_current = targetState;
        tv_state.setText("stato: " + state_current);
    }

    private void initButtons(){
        discoverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(state_current <= STATE_NOT_DISCOVERABLE){
                    setDiscoverable();
                }
                else if (state_current == STATE_DISCOVERABLE) {
                    //start discovering nearby devices
                    if (startScanning()) {
                        discoverBtn.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));
                        Snackbar.make(view, "Scanning...", Snackbar.LENGTH_LONG).show();
                    } else {
                        // error occurred
                        discoverBtn.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
                    }
                }
                else if (state_current == STATE_SCANNING) {
                    Snackbar.make(view, "Still scanning!", Snackbar.LENGTH_LONG).show();
                }
                else {
                    //TODO
                }
            }
        });
        serverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (state_current == STATE_DISCOVERABLE){
                    connectAsServer();
                    serverBtn.setBackgroundTintList(ColorStateList.valueOf(Color.YELLOW));
                    Snackbar.make(view, "Waiting for a client", Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    private void listPairedDevices(){
        listViewPaired.setVisibility(View.VISIBLE);
        listViewNearby.setVisibility(View.INVISIBLE);

        ArrayList<String> aList_string = new ArrayList<>();
        Set<BluetoothDevice> pairedDevicesSet = bluetoothAdapter.getBondedDevices();

        for(BluetoothDevice device : pairedDevicesSet) {
            if (!aListPaired.contains(device)) {
                aListPaired.add(device);
                aList_string.add(device.getName() + "\n" + device.getAddress());

                Log.i("BT", device.getName() + "\n" + device.getAddress());
            }
        }

        listViewPaired.setAdapter(new ArrayAdapter<>(
                getApplicationContext(),
                android.R.layout.simple_selectable_list_item,
                aList_string));
    }

    private void setDiscoverable(){
        Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180);
        startActivity(discoveryIntent);
    }

    private boolean startScanning(){
        listViewPaired.setOnItemClickListener(null);
        listViewPaired.setVisibility(View.INVISIBLE);
        listViewNearby.setVisibility(View.VISIBLE);
        return bluetoothAdapter.startDiscovery();
    }

    private void connectAsServer() {
        goToState(STATE_CONNECTING);
        //run a separate thread waiting for a client request
        AcceptThread thread = new AcceptThread();
        thread.start();
        Toast.makeText(getApplicationContext(),
                "trying connection as server", Toast.LENGTH_SHORT).show();
    }

    private void connectAsClient(BluetoothDevice targetDevice){
        goToState(STATE_CONNECTING);
        //run a separate thread that attempts to connect to targetDevice
        ConnectThread thread = new ConnectThread(targetDevice);
        thread.start();
        Toast.makeText(
                getApplicationContext(),
                "trying connection as client to "+ targetDevice.getName(),
                Toast.LENGTH_SHORT)
                .show();
    }
    private void manageMyConnectedSocket(BluetoothSocket socket){
        goToState(STATE_CONNECTED);
        bluetoothSocket = socket;
        Toast.makeText(
                getApplicationContext(),
                "connected to device ",
                Toast.LENGTH_LONG)
                .show();
        //if(isClient)
        //else
        //TODO
        //https://developer.android.com/guide/topics/connectivity/bluetooth
    }

    private void setListOnClickListener(ListView list){
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> list, View view, int position, long id) {
                // Get the selected item text from ListView
                String selectedItem = (String) list.getItemAtPosition(position);
                if (state_current == STATE_DISCOVERABLE || state_current == STATE_NOT_DISCOVERABLE ||
                        state_current == STATE_SCANNING)
                {
                    ArrayList<BluetoothDevice> arrayList = aListPaired;
                    if (list.equals(listViewNearby))
                        arrayList = aListNearby;
                    else if (list.equals(aListPaired))
                        arrayList = aListPaired;

                    for (BluetoothDevice device : arrayList){
                        if (selectedItem.equals(device.getName() +"\n"+ device.getAddress())){
                            connectAsClient(device);
                            return;
                        }
                    }
                }
                Log.i("HelloListView",
                        "You clicked Item: " + id + " at position:" + position);
            }
        });
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
        if (id == R.id.action_list_paired) {
            listPairedDevices();
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

    private class AcceptThread extends Thread {
        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(myName, appUUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    manageMyConnectedSocket(socket);
                    cancel();
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(appUUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            // You should always call cancelDiscovery() before connect()
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                cancel();
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Whenever a remote Bluetooth device is found...//
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //….retrieve the BluetoothDevice object and its EXTRA_DEVICE field,
                //which contains information about the device’s characteristics and capabilities//
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                ArrayList<String> aList_string = new ArrayList<>();
                if (!aListNearby.contains(device)) {
                    aListNearby.add(device);
                    aList_string.add(device.getName() + "\n" + device.getAddress());
                    listViewNearby.setAdapter(new ArrayAdapter<>(
                            getApplicationContext(),
                            android.R.layout.simple_list_item_1,
                            aList_string));
                }
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                goToState(STATE_SCANNING);
                Toast.makeText(getApplicationContext(),
                        "cerco dispositivi vicini...",
                        Toast.LENGTH_SHORT).show();
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                goToState(STATE_DISCOVERABLE);
                Toast.makeText(getApplicationContext(),
                        "fine ricerca.",
                        Toast.LENGTH_SHORT).show();
            }
            else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_OFF:
                        Toast.makeText(getApplicationContext(),"BT off!",Toast.LENGTH_SHORT).show();
                        goToState(STATE_BT_OFF);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Toast.makeText(getApplicationContext(),"BT closing...",Toast.LENGTH_SHORT).show();
                        goToState(STATE_BT_OFF);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        goToState(STATE_NOT_DISCOVERABLE);
                        Toast.makeText(getApplicationContext(),"BT ready!",Toast.LENGTH_SHORT).show();
                        //unregisterReceiver(this);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Toast.makeText(getApplicationContext(),"BT opening...",Toast.LENGTH_SHORT).show();
                        break;
                }
            }
            else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)){
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                switch(mode) {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        goToState(STATE_DISCOVERABLE);
                        Toast.makeText(getApplicationContext(),
                                "Device is now discoverable",Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        goToState(STATE_NOT_DISCOVERABLE);
                        Toast.makeText(getApplicationContext(),
                                "Device is not discoverable but connectable",Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        goToState(STATE_NOT_DISCOVERABLE);
                        Toast.makeText(getApplicationContext(),
                                "Device isn't discoverable nor connectable",Toast.LENGTH_SHORT).show();
                        break;
                }
            }
            else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)){
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch(state) {
                    case BluetoothAdapter.STATE_CONNECTING:
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Toast.makeText(getApplicationContext(), "Connesso!", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTING:
                        break;
                    case BluetoothAdapter.STATE_DISCONNECTED:
                        Toast.makeText(getApplicationContext(), "Disconnesso!", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    };
}