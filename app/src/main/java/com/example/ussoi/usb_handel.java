package com.example.ussoi;

import static com.example.ussoi.save_input_field.KEY_HTTP;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;

public class usb_handel {

    private static final String TAG                   = "usb_handel";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";

    private final Context    ctx;
    private static UsbManager usbManager;

    private SharedPreferences prefs_in;
    private   http_handel http_handel_obj ;
    private websocket_handel websocket_handel_obj ;
    private OkHttpClient sharedOkHttpClient;


    public usb_handel(Context ctx) {
        this.ctx  = ctx;
        this.sharedOkHttpClient = new OkHttpClient();
    }

    public void usb_setup(SharedPreferences prefs) {
        // Find all available drivers from attached devices.
        usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);

        this.prefs_in = prefs;

        List<UsbSerialDriver> availableDrivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "No USB serial drivers available");
            Toast.makeText(ctx, "No USB serial drivers available", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        // Check permission
        if (!usbManager.hasPermission(device)) {
            PendingIntent pi = PendingIntent.getBroadcast(
                    ctx,
                    0,
                    new Intent(ACTION_USB_PERMISSION).setPackage(ctx.getPackageName()),
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            usbManager.requestPermission(device, pi);
            Log.d(TAG, "Requested USB permission for device");
            return;
        }

        // Open a connection to the first available driver.
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device connection");
            Toast.makeText(ctx, "Failed to open USB device connection", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);

            // Read baud rate from prefs (default 115200)
            int baud = prefs_in.getInt("baud_rate", 115200);
            port.setParameters(
                    baud,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
            );

            Log.d(TAG, "USB port opened at baud " + baud);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up USB port", e);
            try {
                port.close();
            } catch (IOException ignored) {}
        }

        String ipAddress = prefs.getString("ip", "10.0.0.1");
        startReading(port);

        if (prefs_in.getBoolean(KEY_HTTP, false)) {
            Log.i(TAG, "Starting in HTTP Long-Polling mode.");
            http_handel_obj = new http_handel(); // Initialize HTTP handler
            http_handel_obj.startDownlinkPolling(
                    ipAddress,
                    mavlinkBytes -> {
                        try {
                            synchronized (port) {
                                port.write(mavlinkBytes, 100);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing to USB port from HTTP", e);
                        }
                    }
            );
        }
        else {
            Log.i(TAG, "Starting in WebSocket mode.");
            // 1. Define the callback for WebSocket events
            websocket_handel.MessageCallback wsCallback = new websocket_handel.MessageCallback() {
                @Override
                public void onOpen() {
                    Log.i(TAG, "WebSocket connected successfully to " + ipAddress);
                }

                @Override
                public void onMavlinkReceived(byte[] mavlinkBytes) {
                    // This is the replacement for the DownlinkPolling callback
                    try {
                        synchronized (port) {
                            port.write(mavlinkBytes, 100);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to USB port from WebSocket", e);
                    }
                }

                @Override
                public void onClosed(String reason) {
                    Log.w(TAG, "WebSocket closed: " + reason);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "WebSocket error: " + error);
                }
            };


            websocket_handel_obj = new websocket_handel(sharedOkHttpClient, ipAddress, wsCallback);

            // 3. Start the connection
            websocket_handel_obj.connect();
        }

    }


    private Thread readThread;
    private volatile boolean reading = false;

    private static final String TAG2 = "UsbSerial";  // Tag2 for Logcat

    private void startReading(UsbSerialPort port) {
        stopReading();
        reading = true;

        readThread = new Thread(() -> {
            byte[] buffer = new byte[4096]; // allocate buffer
            final int READ_WAIT_MILLIS = 2000;

            while (reading) {
                try {
                    int len = port.read(buffer, READ_WAIT_MILLIS);
                    if (len > 0) {
                        byte[] data = java.util.Arrays.copyOf(buffer, len);

                        if (prefs_in.getBoolean(KEY_HTTP, false)) {
                            if (http_handel_obj != null) {
                                http_handel_obj.sendData(prefs_in.getString("ip", "10.0.0.0"), data, "null");
                            }
                        } else {
                            if (websocket_handel_obj != null) {
                                websocket_handel_obj.sendData(data, "null");
                            }
                        }

                        Log.d(TAG2, "RX (" + len + " bytes): " );
                    }
                } catch (IOException e) {
                    Log.e(TAG2, "Serial read error", e);
                    break; // exit on connection loss
                }
            }
            reading = false;
        }, "UsbReadLoop");

        readThread.start();
    }

    public void stopReading() {
        reading = false;
        if (readThread != null) {
            try { readThread.join(500); } catch (InterruptedException ignored) {}
            readThread = null;
        }
    }

    public void stopAllServices(){
        stopReading();
        // --- REVISED: Stop the correct network handler ---
        if (http_handel_obj != null) {
            http_handel_obj.stopDownlinkPolling();
        }
        if (websocket_handel_obj != null) {
            websocket_handel_obj.disconnect();
        }
    }

}
