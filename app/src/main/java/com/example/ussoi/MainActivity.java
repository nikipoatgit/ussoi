package com.example.ussoi;

import static com.example.ussoi.save_input_field.KEY_HTTP;
import static com.example.ussoi.save_input_field.KEY_WEBSOCKET;

import android.Manifest;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";

    private UsbManager usbManager;
    private TextView usbInfoText;
    private boolean requestedAny = false;

    private EditText baudrateInfoText;
    private EditText url_ip;
    private CheckBox http_checkbox;
    private CheckBox websocket_checkbox;
    private Switch btswitch;
    private Button serviceButton;
    private boolean serviceRunning = false;
    private save_input_field save_input_field_obj;

    private  SharedPreferences in_prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Bind UI elements
        usbInfoText      = findViewById(R.id.usbInfoText);
        baudrateInfoText = findViewById(R.id.baudrateInfoText);
        url_ip           = findViewById(R.id.url_ip);
        http_checkbox    = findViewById(R.id.http_checkbox);
        websocket_checkbox = findViewById(R.id.websocket_checkbox);
        btswitch         = findViewById(R.id.btswitch1);
        serviceButton    = findViewById(R.id.serviceButton);

// Initialize preference helper (singleton)
        save_input_field_obj = save_input_field.getInstance(this);
        in_prefs = save_input_field_obj.get_shared_pref();

// Restore preferences into UI
        save_input_field_obj.restorePreferences(
                baudrateInfoText,
                url_ip,
                http_checkbox,
                websocket_checkbox,
                btswitch
        );


        // Request Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            ActivityCompat.requestPermissions(this, perms, 1);
        }

        // Register USB attach/detach receiver
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, usbFilter);

        // Register USB permission receiver (backward-compatible)
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, permissionReceiver, permissionFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(permissionReceiver, permissionFilter);
        }

        // Check connected USB devices at startup
        checkExistingDevices();


        // Save preferences & start background service on button click
        serviceButton.setOnClickListener(v -> {

            if (  in_prefs.getBoolean(KEY_WEBSOCKET, false) && in_prefs.getBoolean(KEY_HTTP, false) ) {
                if (!serviceRunning) {
                    // Save all UI prefs first
                    save_input_field_obj.savePreferences(
                            baudrateInfoText,
                            url_ip,
                            http_checkbox,
                            websocket_checkbox,
                            btswitch
                    );

                    // If BT is enabled in UI, show picker first
                    if (btswitch.isChecked()) {
                        pickBluetoothDeviceThenStart();
                    } else {
                        startMainService();
                    }
                } else {
                    stopMainService();
                }
            }
            else {
                Toast.makeText(this, "Only Select one check box", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void startMainService() {
        Intent serviceIntent = new Intent(this, services_handel.class);
        // Pass the BT device address (if chosen) to the service
        if (selectedBtDevice != null) {
            serviceIntent.putExtra("bt_address", selectedBtDevice.getAddress());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        serviceButton.setText("Stop Service");
        serviceRunning = true;
    }

    private void stopMainService() {
        Intent serviceIntent = new Intent(this, services_handel.class);
        stopService(serviceIntent);
        serviceButton.setText("Start Service");
        serviceRunning = false;
    }


    // Member variable to hold the chosen device
    private BluetoothDevice selectedBtDevice;

    private void pickBluetoothDeviceThenStart() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

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
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired.isEmpty()) {
            Toast.makeText(this, "No paired devices", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<BluetoothDevice> devices = new ArrayList<>(paired);
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
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
            names[i] = devices.get(i).getName() + "\n" + devices.get(i).getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Device")
                .setItems(names, (dialog, which) -> {
                    selectedBtDevice = devices.get(which);   // remember the choice
                    startMainService();                      // start service after selection
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    private void checkExistingDevices() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            usbInfoText.setText("No USB device connected");
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            requestUsbPermission(device);
            requestedAny = true;
        }

        usbInfoText.setText(requestedAny ? "USB device(s) detected - requesting permission..." : buildDeviceInfo());
    }

    private void requestUsbPermission(UsbDevice device) {
        Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        usbManager.requestPermission(device, pi);
        Log.d(TAG, "Requesting permission for device: " + device.getDeviceName());
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) checkExistingDevices();
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
        if (deviceList.isEmpty()) return "No USB device connected";

        StringBuilder sb = new StringBuilder();
        for (UsbDevice device : deviceList.values()) {
            sb.append("Device: ").append(device.getDeviceName()).append("\n")
                    .append("VID: 0x").append(String.format("%04X", device.getVendorId()))
                    .append("   PID: 0x").append(String.format("%04X", device.getProductId())).append("\n")
                    .append("Class: ").append(device.getDeviceClass())
                    .append("  Subclass: ").append(device.getDeviceSubclass())
                    .append("  Protocol: ").append(device.getDeviceProtocol()).append("\n");
            sb.append("Manufacturer: ").append(device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown")
                    .append("\nProduct: ").append(device.getProductName() != null ? device.getProductName() : "Unknown")
                    .append("\nSerial#: ").append(device.getSerialNumber() != null ? device.getSerialNumber() : "Unknown")
                    .append("\n");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private void stopAllServices() {
        Intent serviceIntent = new Intent(this, services_handel.class);
        stopService(serviceIntent);
    }

}
