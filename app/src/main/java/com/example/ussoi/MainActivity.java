package com.example.ussoi;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.psp.bluetoothlibrary.Bluetooth;
import com.psp.bluetoothlibrary.BluetoothListener;

import java.util.ArrayList;
import java.util.HashMap;


public class MainActivity extends AppCompatActivity {
    private static final String TAG                   = "USB_DEVICE_INFO";
    private static final String TAG3 = "BluetoothAct";
    private static final String PREFS_NAME            = "UsbAppPrefs";
    private static final String KEY_BAUD_RATE         = "baud_rate";
    private static final String KEY_HTTP_IP           = "http_ip";
    private static final String KEY_TCP_IP            = "tcp_ip";
    private static final String KEY_UDP_IP            = "udp_ip";
    private static final String KEY_HTTP_SEND         = "http_send";
    private static final String KEY_TCP_SEND          = "tcp_send";
    private static final String KEY_UDP_SEND          = "udp_send";
    private static final String KEY_HTTP_RECEIVE      = "http_receive";
    private static final String KEY_TCP_RECEIVE       = "tcp_receive";
    private static final String KEY_UDP_RECEIVE       = "udp_receive";
    private static final String btswitch_key       = "bt_enable";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";

    private UsbManager usbManager;
    private TextView   usbInfoText;
    private EditText   baudrateInfoText, httpUrl, tcpUrl, udpUrl;
    private CheckBox   httpSend, tcpSend, udpSend, httpReceive, tcpReceive, udpReceive;
    private Button     serviceButton ;

    private Switch btswitch;

    boolean requestedAny = false;


    private usb_handel usbHandler;

    private bt_handel btHandel;
    private Bluetooth bluetooth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

//        Runtime permission request

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            };
            ActivityCompat.requestPermissions(this, perms, 1);
        }


        // Initialize USB manager and views
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        bindViews();

        // 1️⃣ Register USB attach/detach receiver (system broadcasts, no flag needed)
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, usbFilter);

        // 2️⃣ Register permissionReceiver (non-system broadcast, must specify exported state)
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        ContextCompat.registerReceiver(
                this,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        // Initialize bluetooth
        bluetooth = new Bluetooth(this);
        initBluetoothLists();


        // Restore UI state and preferences
        restorePreferences();
        setupStartServiceButton();

        // Immediately request permissions for any already connected devices
        checkExistingDevices();
        btHandel = new bt_handel(this);
        usbHandler = new usb_handel(this);

        if (btswitch.isChecked() ){

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        getPairedDevices();  // safe to call
                    } else {
                        // Request permission
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
                    }
                } else {
                    // Below Android 12, no runtime permission needed
                    getPairedDevices();
                }

            }
        }


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        unregisterReceiver(permissionReceiver);
    }

    private void bindViews() {
        usbInfoText      = findViewById(R.id.usbInfoText);
        baudrateInfoText = findViewById(R.id.baudrateInfoText);
        httpUrl          = findViewById(R.id.httpUrl);
        tcpUrl           = findViewById(R.id.tcpUrl);
        udpUrl           = findViewById(R.id.udpUrl);
        httpSend         = findViewById(R.id.httpSend);
        tcpSend          = findViewById(R.id.tcpSend);
        udpSend          = findViewById(R.id.udpSend);
        httpReceive      = findViewById(R.id.httpReceive);
        tcpReceive       = findViewById(R.id.tcpReceive);
        udpReceive       = findViewById(R.id.udpReceive);
        serviceButton     = findViewById(R.id.serviceButton);
        btswitch = findViewById(R.id.btswitch1);
    }

    private void restorePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        baudrateInfoText.setText(String.valueOf(prefs.getInt(KEY_BAUD_RATE, 115200)));
        httpUrl.setText(prefs.getString(KEY_HTTP_IP, ""));
        tcpUrl.setText(prefs.getString(KEY_TCP_IP, ""));
        udpUrl.setText(prefs.getString(KEY_UDP_IP, ""));
        httpSend.setChecked(prefs.getBoolean(KEY_HTTP_SEND, false));
        tcpSend.setChecked(prefs.getBoolean(KEY_TCP_SEND, false));
        udpSend.setChecked(prefs.getBoolean(KEY_UDP_SEND, false));
        httpReceive.setChecked(prefs.getBoolean(KEY_HTTP_RECEIVE, false));
        tcpReceive.setChecked(prefs.getBoolean(KEY_TCP_RECEIVE, false));
        udpReceive.setChecked(prefs.getBoolean(KEY_UDP_RECEIVE, false));
        btswitch.setChecked(prefs.getBoolean(btswitch_key, false));

    }

    private void setupStartServiceButton() {

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);



        serviceButton.setOnClickListener(v -> {
            int baud = Integer.parseInt(baudrateInfoText.getText().toString().trim());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_BAUD_RATE, baud);
            editor.putString(KEY_HTTP_IP,  httpUrl.getText().toString().trim());
            editor.putString(KEY_TCP_IP,   tcpUrl.getText().toString().trim());
            editor.putString(KEY_UDP_IP,   udpUrl.getText().toString().trim());
            editor.putBoolean(KEY_HTTP_SEND,    httpSend.isChecked());
            editor.putBoolean(KEY_TCP_SEND,     tcpSend.isChecked());
            editor.putBoolean(KEY_UDP_SEND,     udpSend.isChecked());
            editor.putBoolean(KEY_HTTP_RECEIVE, httpReceive.isChecked());
            editor.putBoolean(KEY_TCP_RECEIVE,  tcpReceive.isChecked());
            editor.putBoolean(KEY_UDP_RECEIVE,  udpReceive.isChecked());
            editor.putBoolean(btswitch_key,  btswitch.isChecked());
            editor.apply();

            if(serviceButton.getText().toString().equals("Start Service")) {
                // start your service here
                if (requestedAny){
                    // 3. Call your handler to perform setup
                    usbHandler.usb_setup(prefs);
                    serviceButton.setText("Stop Service");
                }
                else{
                    if (btswitch.isChecked()){
                        btHandel.bt_service_start(prefs);
                        serviceButton.setText("Stop Service");
                    }
                    else {
                        Toast.makeText(this,"usb device not connect !", Toast.LENGTH_SHORT).show();
                    }
                }


            } else {
                // stop your service here
                stopAllServices();
                serviceButton.setText("Start Service");
            }


        });


    }

    private void stopAllServices(){
        btHandel.disconnect();
        btHandel.stopDownlinkPolling();
        usbHandler.stopDownlinkPolling();
        usbHandler.stopReading();
    }




    private void checkExistingDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            usbInfoText.setText("No USB device connected");
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            // Always request permission for every detected device
            requestUsbPermission(device);
            requestedAny = true;
        }
        usbInfoText.setText(requestedAny
                ? "USB device(s) detected - requesting permission..."
                : buildDeviceInfo()
        );
    }

    private void requestUsbPermission(UsbDevice device) {
        Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        usbManager.requestPermission(device, pi);
        Log.d(TAG, "Requesting permission for device: " + device.getDeviceName());
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    checkExistingDevices();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                usbInfoText.setText("No USB device connected");
                stopAllServices();
                Log.d(TAG, "USB detached – cleared info");

            }
        }
    };

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) {
                    usbInfoText.setText(buildDeviceInfo());
                    Log.d(TAG, "USB permission granted – info updated");
                } else {
                    usbInfoText.setText("USB device detected but permission denied");
                    Log.d(TAG, "USB permission denied");
                }
            }
        }
    };

    private String buildDeviceInfo() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            return "No USB device connected";
        }
        StringBuilder sb = new StringBuilder();
        for (UsbDevice device : deviceList.values()) {
            sb.append("Device: ").append(device.getDeviceName()).append("\n")
                    .append("VID: 0x").append(String.format("%04X", device.getVendorId()))
                    .append("   PID: 0x").append(String.format("%04X", device.getProductId())).append("\n")
                    .append("Class: ").append(device.getDeviceClass())
                    .append("  Subclass: ").append(device.getDeviceSubclass())
                    .append("  Protocol: ").append(device.getDeviceProtocol()).append("\n");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                sb.append("Manufacturer: ").append(
                                device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown")
                        .append("\nProduct: ").append(
                                device.getProductName() != null ? device.getProductName() : "Unknown")
                        .append("\nSerial#: ").append(
                                device.getSerialNumber() != null ? device.getSerialNumber() : "Unknown")
                        .append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // Lists to hold device names
    private ArrayList<String> listDetectDevicesString;
    private ArrayList<String> listPairedDevicesString;

    // Lists to hold actual BluetoothDevice objects
    private ArrayList<BluetoothDevice> listDetectBluetoothDevices;
    private ArrayList<BluetoothDevice> listPairedBluetoothDevices;

    private void initBluetoothLists() {
        listDetectDevicesString = new ArrayList<>();
        listPairedDevicesString = new ArrayList<>();
        listDetectBluetoothDevices = new ArrayList<>();
        listPairedBluetoothDevices = new ArrayList<>();
    }

    private void getPairedDevices() {
        ArrayList<BluetoothDevice> devices = bluetooth.getPairedDevices();

        if(devices.size() > 0) {
            for(BluetoothDevice device: devices) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                listPairedDevicesString.add(device.getName());
                listPairedBluetoothDevices.add(device);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                Log.d(TAG3,"Paired device is "+device.getName());
            }
        }
        else {
            Log.d(TAG3,"Paired device list not found");
        }
    }




}


