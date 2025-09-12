package com.example.ussoi;


import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.psp.bluetoothlibrary.Bluetooth;
import com.psp.bluetoothlibrary.BluetoothListener;
import com.psp.bluetoothlibrary.Connection;
import com.psp.bluetoothlibrary.SendReceive;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class bt_handel {


    private static final String TAG = "bt_handel";
    private final Context ctx;
    private final Bluetooth bluetooth;
    private Connection connection;
    private SharedPreferences prefs_in;


    public bt_handel(Context ctx) {
        this.ctx = ctx;
        this.bluetooth = new Bluetooth(ctx);
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

        // Check permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(ctx, "BLUETOOTH_CONNECT permission required", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Get paired devices
        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired.isEmpty()) {
            Toast.makeText(ctx, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create device selection dialog
        showDeviceSelectionDialog(new ArrayList<>(paired));

        new Thread(() -> {
            // Wait until connection is established
            while (!isConnected()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
            // Send 10 times, 2 seconds apart
            for (int i = 0; i < 10; i++) {
                sendData("Hello Arduino\n");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();

    }

    private void showDeviceSelectionDialog(List<BluetoothDevice> devices) {
        String[] deviceNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                deviceNames[i] = devices.get(i).getName() + "\n" + devices.get(i).getAddress();
            }
        }

        new AlertDialog.Builder(ctx)
                .setTitle("Select Bluetooth Device")
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice selectedDevice = devices.get(which);
                    connectToDevice(selectedDevice);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void connectToDevice(BluetoothDevice device) {
        // Set up connection listener
        BluetoothListener.onConnectionListener connectionListener = new BluetoothListener.onConnectionListener() {
            @Override
            public void onConnectionStateChanged(android.bluetooth.BluetoothSocket socket, int state) {
                switch (state) {
                    case Connection.CONNECTING:
                        Log.d(TAG, "Connecting...");
                        Toast.makeText(ctx, "Connecting...", Toast.LENGTH_SHORT).show();
                        break;
                    case Connection.CONNECTED:
                        Log.d(TAG, "Connected successfully");
                        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
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
                switch (errorCode) {
                    case Connection.SOCKET_NOT_FOUND:
                        Log.d(TAG, "Socket not found");
                        break;
                    case Connection.CONNECT_FAILED:
                        Log.d(TAG, "Connect failed");
                        break;
                }
                Toast.makeText(ctx, "Connection failed", Toast.LENGTH_SHORT).show();
                connection.disconnect();
            }
        };

        // Connect to the selected device
        connection.connect(device, true, connectionListener, null);
    }

    private void setupReceiveListener() {
        // Set up global receive listener using SendReceive singleton
        SendReceive.getInstance().setOnReceiveListener(new BluetoothListener.onReceiveListener() {
            @Override
            public void onReceived(String receivedData) {
                Log.d(TAG, "[RX] Bytes: " + receivedData);
                sendDataHttp(prefs_in.getString("http_ip","10.0.0.0"),receivedData.getBytes(),"null");
                // Not used in this example
            }
        });
    }

    // Method to send string data
    public boolean sendData(String data) {
        if (SendReceive.getInstance().send(data)) {
            Log.d(TAG, "[TX] " + data);
            return true;
        } else {
            Log.d(TAG, "[TX] Failed: " + data);
            return false;
        }
    }


    // Method to disconnect
    public void disconnect() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    // Method to check if connected
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }



    private static final okhttp3.OkHttpClient HTTP = new okhttp3.OkHttpClient();
    private static final okhttp3.MediaType JSON
            = okhttp3.MediaType.parse("application/json; charset=utf-8");

    static void sendDataHttp(String urlStr, byte[] data, String config) {
        // Ensure scheme
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }
        okhttp3.HttpUrl url = okhttp3.HttpUrl.parse(urlStr);
        if (url == null) {
            android.util.Log.e("HTTP", "Invalid URL: " + urlStr);
            return;
        }

        // Encode bytes for JSON
        String b64 = okio.ByteString.of(data).base64();

        // Build JSON payload
        org.json.JSONObject obj = new org.json.JSONObject();
        try {
            obj.put("mavlink_out", b64);
            obj.put("config", config);
            obj.put("encoding", "base64"); // optional hint for server
        } catch (org.json.JSONException e) {
            android.util.Log.e("HTTP", "JSON build failed", e);
            return;
        }

        // POST JSON
        okhttp3.RequestBody body = okhttp3.RequestBody.create(JSON, obj.toString());
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build();

        HTTP.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                android.util.Log.e("HTTP", "POST JSON failed", e);
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response resp) {
                resp.close();
            }
        });
    }

    private static final okhttp3.OkHttpClient HTTP2 = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS) // > server hold time
            .build();

    private volatile boolean downlinkCancelled = false;

    public void startDownlinkPolling(UsbSerialPort port, String url) {
        downlinkCancelled = false; // allow polling
        pollOnce(port, url);
    }

    public void stopDownlinkPolling() {
        downlinkCancelled = true;
    }

    private void pollOnce(UsbSerialPort port, String url) {
        if (downlinkCancelled) return; // exit cleanly

        okhttp3.Request req = new okhttp3.Request.Builder().url(url).get().build();
        HTTP2.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e("HTTP", "downlink fail", e);
                if (!downlinkCancelled) {
                    retry(port, url, 1000);
                }
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response resp) {
                try (okhttp3.ResponseBody body = resp.body()) {
                    if (!downlinkCancelled && resp.isSuccessful() && body != null && resp.code() != 204) {
                        String s = body.string();
                        JSONObject json = new JSONObject(s);
                        String b64 = json.getString("b64");
                        byte[] mavlinkBytes = Base64.decode(b64, Base64.DEFAULT);

                        // Convert bytes to String—choose correct charset or encoding
                        String payload = new String(mavlinkBytes, StandardCharsets.UTF_8);

                        // Send via your helper instead of port.write()
                        if (sendData(payload)) {
                            Log.d("bt_rec_data", "[TX] " + payload);
                        } else {
                            Log.e("bt_rec_data", "[TX] Failed: " + payload);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "downlink parse error", e);
                } finally {
                    if (!downlinkCancelled) {
                        pollOnce(port, url);
                    }
                }
            }

        });
    }


    private void retry(UsbSerialPort port, String url, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> startDownlinkPolling(port, url), delayMs);
    }


}
