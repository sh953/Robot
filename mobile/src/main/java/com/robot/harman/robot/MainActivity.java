package com.robot.harman.robot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {

    public static int lightValue = 1;
    public static int timeValue = 1;
    public static int temperatureValue = 1;

    private static String GROVE_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static String CHARACTERISTIC_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static String CHARACTERISTIC_RX = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 5000; //5 seconds
    private static final String DEVICE_NAME = "HMSoft"; //display name for Grove BLE

    private BluetoothAdapter mBluetoothAdapter;//our local adapter
    private BluetoothGatt mBluetoothGatt; //provides the GATT functionality for communication
    private BluetoothGattService mBluetoothGattService; //service on mBlueoothGatt
    private static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();//discovered devices in range
    private BluetoothDevice mDevice; //external BLE device (Grove BLE module)

    private Timer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView temperature = (TextView) this.findViewById(R.id.temperatureValue);
        temperature.setText(String.valueOf(temperatureValue));

        TextView time = (TextView) this.findViewById(R.id.timeValue);
        time.setText(String.valueOf(timeValue));

        TextView light = (TextView) this.findViewById(R.id.lightValue);
        light.setText(String.valueOf(lightValue));

        statusUpdate("BLE supported on this device");

        //get a reference to the Bluetooth Manager
        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        //Open settings if Bluetooth isn't enabled
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        if (mBluetoothAdapter == null) {

        }

        //try to find the Grove BLE V1 module
        searchForDevices();

    }

    private void searchForDevices ()
    {
        statusUpdate("Searching for devices ...");

        if(mTimer != null) {
            mTimer.cancel();
        }

        scanLeDevice();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                statusUpdate("Search complete");
                findGroveBLE();
            }
        }, SCAN_PERIOD);
    }

    private void findGroveBLE ()
    {
        if(mDevices == null || mDevices.size() == 0)
        {
            statusUpdate("No BLE devices found");
            return;
        }
        else if(mDevice == null)
        {
            statusUpdate("Unable to find Grove BLE");
            return;
        }
        else
        {
            statusUpdate("Found Grove BLE V1");
            statusUpdate("Address: " + mDevice.getAddress());
            connectDevice();
        }
    }

    private boolean connectDevice ()
    {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDevice.getAddress());
        if (device == null) {
            statusUpdate("Unable to connect");
            return false;
        }
        // directly connect to the device
        statusUpdate("Connecting ...");
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        return true;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                statusUpdate("Connected");
                statusUpdate("Searching for services");
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                statusUpdate("Device disconnected");
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();

                for(BluetoothGattService gattService : gattServices) {
                    statusUpdate("Service discovered: " + gattService.getUuid());
                    if(GROVE_SERVICE.equals(gattService.getUuid().toString()))
                    {
                        mBluetoothGattService = gattService;
                        statusUpdate("Found communication Service");
                        sendMessage();
                    }
                }
            } else {
                statusUpdate("onServicesDiscovered received: " + status);
            }
        }
    };

    private void sendMessage ()
    {
        if (mBluetoothGattService == null)
            return;

        statusUpdate("Finding Characteristic...");
        BluetoothGattCharacteristic gattCharacteristic =
                mBluetoothGattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_TX));

        if(gattCharacteristic == null) {
            statusUpdate("Couldn't find TX characteristic: " + CHARACTERISTIC_TX);
            return;
        }

        statusUpdate("Found TX characteristic: " + CHARACTERISTIC_TX);

        statusUpdate("Sending message 'Hello Grove BLE'");

        String msg = "Hello Grove BLE";

        byte b = 0x00;
        byte[] temp = msg.getBytes();
        byte[] tx = new byte[temp.length + 1];
        tx[0] = b;

        for(int i = 0; i < temp.length; i++)
            tx[i+1] = temp[i];

        gattCharacteristic.setValue(tx);
        mBluetoothGatt.writeCharacteristic(gattCharacteristic);
    }

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            if (device != null) {
                if (mDevices.indexOf(device) == -1)//to avoid duplicate entries
                {
                    if (DEVICE_NAME.equals(device.getName())) {
                        mDevice = device;//we found our device!
                    }
                    mDevices.add(device);
                    statusUpdate("Found device " + device.getName());
                }
            }
        }
    };

    //output helper method
    private void statusUpdate (final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("513 " + msg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}
