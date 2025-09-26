package com.example.ussoi;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.psp.bluetoothlibrary.BluetoothListener;
import com.psp.bluetoothlibrary.Connection;
import com.psp.bluetoothlibrary.SendReceive;

public class bt_handel {

    public static final String ACTION_BT_FAILED = "com.example.ussoi.BT_CONNECTION_FAILED";
    private static final String TAG = "bt_handel";
    private final Context ctx;
    private final Connection connection;
    private final BluetoothDevice device;      // chosen device is passed in
    private SharedPreferences prefs_in;

    private final http_handel http_handel_obj = new http_handel();

    public bt_handel(Context ctx, BluetoothDevice device) {
        this.ctx = ctx;
        this.device = device;
        this.connection = new Connection(ctx);
    }

    public void bt_service_start(SharedPreferences prefs) {
        this.prefs_in = prefs;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(ctx, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(ctx, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 12+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ctx, "BLUETOOTH_CONNECT permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Immediately connect to the supplied device
        connectToDevice(device);

        // Start HTTP downlink polling to forward received bytes
//        Log.d("services_handel", "Downlink URL: " + prefs.getString("ip","10.0.0.1"));

        http_handel_obj.startDownlinkPolling(
                prefs.getString("ip", "10.0.0.1"),
                this::sendData
        );
    }

    private void connectToDevice(BluetoothDevice device) {
        BluetoothListener.onConnectionListener connectionListener =
                new BluetoothListener.onConnectionListener() {
                    @Override
                    public void onConnectionStateChanged(android.bluetooth.BluetoothSocket socket, int state) {
                        switch (state) {
                            case Connection.CONNECTING:
                                Log.d(TAG, "Connecting…");
                                Toast.makeText(ctx, "Connecting…", Toast.LENGTH_SHORT).show();
                                break;
                            case Connection.CONNECTED:
                                Log.d(TAG, "Connected successfully");
                                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                                        != PackageManager.PERMISSION_GRANTED) return;
                                Toast.makeText(ctx, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                                setupReceiveListener();
                                break;
                            case Connection.DISCONNECTED:
                                Log.d(TAG, "Disconnected");
                                Toast.makeText(ctx, "Disconnected", Toast.LENGTH_SHORT).show();
                                connection.disconnect();
                                break;
                        }
                    }

                    @Override
                    public void onConnectionFailed(int errorCode) {
                        Log.d(TAG, "Connection failed, code " + errorCode);
                        Toast.makeText(ctx, "Connection failed", Toast.LENGTH_SHORT).show();
                        //  Create and send the broadcast to main activity to stop bg thread
                        Intent intent = new Intent(ACTION_BT_FAILED);
                        ctx.sendBroadcast(intent);

                        connection.disconnect();
                    }
                };

        connection.connect(device, true, connectionListener, null);
    }

    private void setupReceiveListener() {
        SendReceive.getInstance().setOnReceiveListener(receivedData -> {
            Log.d(TAG, "[RX] " + receivedData);
            http_handel_obj.sendData(
                    prefs_in.getString("ip", "10.0.0.0"),
                    receivedData.getBytes(),
                    "null"
            );
        });
    }

    public void sendData(byte[] mavlinkBytes) {
        String base64 = android.util.Base64.encodeToString(mavlinkBytes, android.util.Base64.NO_WRAP);
        boolean success = SendReceive.getInstance().send(base64);
        Log.d(TAG, success ? "[TX] " + base64 : "[TX] Failed: " + base64);
    }

    public void disconnect() {
        connection.disconnect();
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public void stopAllServices() {
        disconnect();
        http_handel_obj.stopDownlinkPolling();
    }
}
