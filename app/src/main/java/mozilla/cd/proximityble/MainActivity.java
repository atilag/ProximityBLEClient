package mozilla.cd.proximityble;

import android.app.Activity;
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
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    public static final int MESSAGE_TOAST = 3;
    public static final String TOAST = "toast";

    public static final String EXTRA_SSID = "extra_ssid";
    public static final String EXTRA_PASSWORD = "extra_passeord";

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        final Button button = (Button) findViewById(R.id.readyButton);
        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                final EditText ssid = (EditText)findViewById(R.id.ssidText);
                final EditText password = (EditText)findViewById(R.id.passText);
                if(ssid.getText().toString().isEmpty() || password.getText().toString().isEmpty()){
                    sendToastMsg("Please provide an SSID and a password");
                    return;
                }else if(password.getText().toString().length() < 8 || password.getText().toString().length() > 64){
                    sendToastMsg("Password must be 8 to 63 characters");
                    return;
                }

                Intent scanningIntent = new Intent(getApplicationContext(), DiscoveringActivity.class);
                scanningIntent.putExtra(EXTRA_SSID, ssid.getText().toString());
                scanningIntent.putExtra(EXTRA_PASSWORD, password.getText().toString());
                startActivity(scanningIntent);
            }
        }); // End setOnClickListener

    }

    private final android.os.Handler mHandler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, msg.getData().getString(MainActivity.TOAST));
                    break;
            }
        }
    };

    private void sendToastMsg(final String toastMsg){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(TOAST, toastMsg);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            }
        });

    }
}
