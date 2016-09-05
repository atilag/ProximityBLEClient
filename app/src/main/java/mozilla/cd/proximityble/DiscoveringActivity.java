package mozilla.cd.proximityble;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebBackForwardList;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;

public class DiscoveringActivity extends AppCompatActivity {

    private static final String TAG = "DiscoveringActivity";

    private static final int BLE_DEVICE_NOT_FOUND = -1;
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    public static String EXTRA_DEVICE_NAME = "device_name";


    // Message types sent from the BluetoothChatService Handler
    private static final int MESSAGE_STATE_CHANGE = 1;
    private static final int MESSAGE_DEVICE_NAME = 2;

    private static final String DEVICE_NAME = "device_name";

    // Constants that indicate the current connection state
    private static final int STATE_NONE = 1;
    private static final int STATE_CONNECTING = 2;
    private static final int STATE_CONNECTED = 3;
    private static final int STATE_SENDING_SECRET = 4;
    private static final int STATE_SENDING_PASSWORD = 5;
    private static final int STATE_SENDING_SSID = 6;
    private static final int STATE_DONE = 7;

    public static final int MINIMUM_RSSI_THRESHOLD = -30;

    private static final Map<Integer, String> mStatusTexts;
    static {
        Map<Integer, String> aMap = new HashMap<Integer, String>();
        aMap.put(STATE_NONE, "Not connected");
        aMap.put(STATE_CONNECTING, "Device found, connecting...");
        aMap.put(STATE_CONNECTED, "Connected, waiting for the public key...");
        aMap.put(STATE_SENDING_SECRET, "Sending secret...");
        aMap.put(STATE_SENDING_PASSWORD, "Sending password...");
        aMap.put(STATE_SENDING_SSID, "Sending ssid...");
        aMap.put(STATE_DONE, "It's done!!");
        mStatusTexts = Collections.unmodifiableMap(aMap);
    }

    private class BLEDevice {
        public String mName;
        public String mAddress;
        public int mRssi;

        BLEDevice(String name, String address, int rssi){
            mName = name; mAddress = address; mRssi = rssi;
        }
        @Override
        public String toString() {
            return mName + "@" + mAddress;
        }
    }

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mConnectedGatt;

    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mAesSecretCharacteristic;
    private BluetoothGattCharacteristic mPasswordCharacteristic;
    private BluetoothGattCharacteristic mSsidCharacteristic;
    private CryptoHelper mCryptoHelper = null;

    private String mSsid;
    private String mPassword;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovering);

        WebView animation = (WebView)findViewById(R.id.webView);
        animation.loadUrl("file:///android_asset/discovering_animation.html");

        mBluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            //mBleDiscoveryCallback.onInitFailure("Bluetooth not supported in this device!!");
            return;
        }

        if (!getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            sendToastMsg("Bluetooth LE is not supported in this devices!!");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        Intent intent = getIntent();
        mSsid = intent.getStringExtra(MainActivity.EXTRA_SSID);
        mPassword = intent.getStringExtra(MainActivity.EXTRA_PASSWORD);

        startScan();
    }

    public void startScan() {

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(ProximityService.SERVICE_UUID))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
    }

    public void stopScan() {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: "+results.size()+" results");
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE Scan Failed: "+errorCode);
        }

        private void processResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "New LE Device: " + device.getName() + " @ " + result.getRssi());
            //mBleDiscoveryCallback.onScanResult(device, result.getRssi());
            // RSSI are negative values. The more far you are from the other peer,
            // the smaller the value is.
            if(result.getRssi() > MINIMUM_RSSI_THRESHOLD ){
                stopScan();
                device.connectGatt(DiscoveringActivity.this, false, mGattCallback);
                showStatus(STATE_CONNECTING);
            }
        }
    };


    public BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, final int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange: status: " + status + " newState: " + newState);
            if(status != BluetoothGatt.GATT_SUCCESS) {
                sendToastMsg("[!] Error: onConnectionStateChange(): status = " + status);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        WebView webView = (WebView) findViewById(R.id.webView);
                        webView.loadUrl("file:///android_asset/contact.html");
                    }
                });
                showStatus(STATE_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendToastMsg("[!] Disconnected");
                showStatus(STATE_NONE);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt,
                                         final int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered: status: " + status);
            if(status != BluetoothGatt.GATT_SUCCESS) {
                sendToastMsg("[!] Error Gatt operation failed! status: " + status);
                gatt.disconnect();
                return;
            }

            for (BluetoothGattService service : gatt.getServices()) {
                Log.d(TAG, "Service: " + service.getUuid());
                if (ProximityService.SERVICE_UUID.equals(service.getUuid())) {
                    mGatt = gatt;
                    // First thing we do, is ask for the public key to encrypt the whole conversation
                    gatt.readCharacteristic(service.getCharacteristic(ProximityService.CHARACTERISTIC_MY_PUBLIC_KEY_UUID));
                    // Get the rest of the characteristics to write them later
                    mAesSecretCharacteristic = service.getCharacteristic(ProximityService.CHARACTERISTIC_AES_SECRET_UUID);
                    mPasswordCharacteristic = service.getCharacteristic(ProximityService.CHARACTERISTIC_PASSWORD_UUID);
                    mSsidCharacteristic = service.getCharacteristic(ProximityService.CHARACTERISTIC_SSID_UUID);
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         final int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                sendToastMsg("[!] Error: Gatt operation failed! status: " + status);
                finish();
                return;
            }

            try {
                if (ProximityService.CHARACTERISTIC_MY_PUBLIC_KEY_UUID.equals(characteristic.getUuid())) {
                    final String msg = characteristic.getStringValue(0);
                    mCryptoHelper = new CryptoHelper(msg);
                    // Send the  AES secret, encrypted with their public key
                    mAesSecretCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    mAesSecretCharacteristic.setValue(mCryptoHelper.getEncryptedBase64AesSecret());
                    mGatt.writeCharacteristic(mAesSecretCharacteristic);
                    showStatus(STATE_SENDING_SECRET);
                } else {
                    sendToastMsg("[!] Error: Unknown characteristic! Oo");
                    gatt.disconnect();
                }
            } catch (final Exception ex) {
                sendToastMsg("[!] Error: onCharacteristicRead: Exception: " + ex.toString());
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           final int status){

            if(status != BluetoothGatt.GATT_SUCCESS) {
                sendToastMsg("[!] Error: Gatt operation failed! status: " + status);
                gatt.disconnect();
                finish();
                return;
            }
            if(ProximityService.CHARACTERISTIC_AES_SECRET_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "AES Secret successfully sent!");
                try{
                    byte[] passEncrypted = Base64.encode(mCryptoHelper.encryptWithAes(mPassword.getBytes("UTF-8")), Base64.NO_WRAP);
                    mPasswordCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    mPasswordCharacteristic.setValue(passEncrypted);
                    mGatt.writeCharacteristic(mPasswordCharacteristic);
                    showStatus(STATE_SENDING_PASSWORD);
                }catch (Exception ex){
                    sendToastMsg("Error sending password and ssid: ex: " + ex.toString());
                    gatt.disconnect();
                    finish();
                }
            }else if (ProximityService.CHARACTERISTIC_PASSWORD_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "Password successfully sent!");
                try {
                    byte[] ssidEncrypted = Base64.encode(mCryptoHelper.encryptWithAes(mSsid.getBytes("UTF-8")), Base64.NO_WRAP);
                    mSsidCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    mSsidCharacteristic.setValue(ssidEncrypted);
                    mGatt.writeCharacteristic(mSsidCharacteristic);
                    showStatus(STATE_SENDING_SSID);
                }catch(Exception ex){
                    sendToastMsg("Could not send SSID!: ex: " + ex.toString());
                    gatt.disconnect();
                    finish();
                }

            }else if (ProximityService.CHARACTERISTIC_SSID_UUID.equals(characteristic.getUuid())) {
                Log.d(TAG, "SSID successfully sent!");
                Log.d(TAG, "It's done!");
                showStatus(STATE_DONE);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        WebView webView = (WebView) findViewById(R.id.webView);
                        webView.loadUrl("file:///android_asset/done.html");
                    }
                });

            }else{
                sendToastMsg("[!] Error: Unknown characteristic! Oo");
                gatt.disconnect();
                finish();
            }
        }

    }; //End BluetoothGattCallback

    private final android.os.Handler mHandler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MainActivity.MESSAGE_TOAST:
                    Toast.makeText(DiscoveringActivity.this, msg.getData().getString(MainActivity.TOAST),
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, msg.getData().getString(MainActivity.TOAST));
                    break;
            }
        }
    };

    private void showStatus(final int status){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    TextView statusView = (TextView) findViewById(R.id.statusView);
                    statusView.setText(mStatusTexts.get(status));
                }catch(Exception ex){
                }
            }
        });
    }

    private void sendToastMsg(final String toastMsg){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Message msg = mHandler.obtainMessage(MainActivity.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(MainActivity.TOAST, toastMsg);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            }
        });

    }

}
