package com.example.ussoi;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class usb_handel {

    private static final String TAG                   = "usb_handel";
    private static final String ACTION_USB_PERMISSION = "com.example.ussoi.USB_PERMISSION";

    private final Context    ctx;
    private static UsbManager usbManager;

    private SharedPreferences prefs_in;

    public usb_handel(Context ctx) {
        this.ctx        = ctx;
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
            int baud = prefs.getInt("baud_rate", 115200);
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




        startReading(port);
        startDownlinkPolling(port,prefs_in.getString("http_ip","10.0.0.0")+"/downlink/");

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

                        sendDataHttp(prefs_in.getString("http_ip","10.0.0.0"),data,"null");

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


    public void stopReading() {
        reading = false;
        if (readThread != null) {
            try { readThread.join(500); } catch (InterruptedException ignored) {}
            readThread = null;
        }
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
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                Log.e("HTTP", "downlink fail", e);
                if (!downlinkCancelled) {
                    retry(port, url, 1000);
                }
            }

            @Override public void onResponse(okhttp3.Call call, okhttp3.Response resp) {
                try (okhttp3.ResponseBody body = resp.body()) {
                    if (!downlinkCancelled) {
                        if (resp.code() == 204 || body == null) {
                            // no data
                        } else if (resp.isSuccessful()) {
                            String s = body.string();
                            org.json.JSONObject json = new org.json.JSONObject(s);
                            String b64 = json.getString("b64");
                            byte[] mavlinkBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);

                            synchronized (port) {
                                port.write(mavlinkBytes, 100);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("HTTP", "downlink parse error", e);
                } finally {
                    // re-poll only if not cancelled
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
