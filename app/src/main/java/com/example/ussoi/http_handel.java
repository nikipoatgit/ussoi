package com.example.ussoi;

import android.util.Log;

import androidx.annotation.NonNull;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class http_handel {

    private static final String TAG = "http_handel";
    private static final okhttp3.MediaType JSON
            = okhttp3.MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final OkHttpClient longPollClient;

    // For stopping the long-poll loop
    private volatile boolean downlinkCancelled = false;

    // Callback so USB layer can write bytes when data arrives
    public interface DownlinkCallback {
        void onReceived(byte[] mavlinkBytes) throws IOException;
    }

    public http_handel() {
        httpClient = new OkHttpClient();
        longPollClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS) // keep-alive for long polling
                .build();
    }

    /** Simple POST of base64 data */
    public void sendData(String urlStr, byte[] data, String config) {
        if (!urlStr.startsWith("http")) urlStr = "http://" + urlStr ;

        HttpUrl url = HttpUrl.parse(urlStr);
        if (url == null) { Log.e(TAG, "Invalid URL"); return; }

        String b64 = okio.ByteString.of(data).base64();
        JSONObject obj = new JSONObject();
        try {
            obj.put("mavlink_out", b64);
            obj.put("config", config);
            obj.put("encoding", "base64");
        } catch (Exception e) { Log.e(TAG, "JSON error", e); return; }

        okhttp3.RequestBody body =
                okhttp3.RequestBody.create(JSON, obj.toString());
        Request req = new Request.Builder().url(url).post(body).build();

        httpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, IOException e) {
                Log.e(TAG, "POST failed", e);
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response resp) {
                resp.close();
            }
        });
    }

    /** Start long-polling for downlink data */
    public void stopDownlinkPolling(){
        downlinkCancelled = true;
    }
    public void startDownlinkPolling(String url, DownlinkCallback callback) {
        downlinkCancelled = false;
        pollOnce(url, callback);
    }

    private void pollOnce(String url, DownlinkCallback callback) {
        if (downlinkCancelled) return;

        if (!url.startsWith("http")) url = "http://" + url;
        HttpUrl httpUrl = HttpUrl.parse(url+ "/downlink");
        if (httpUrl == null) { Log.e(TAG, "Bad downlink URL"); return; }

        Request req = new Request.Builder().url(httpUrl).get().build();
        String finalUrl = url;

        longPollClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Log.e(TAG, "downlink fail", e);
                if (!downlinkCancelled) retry(finalUrl, callback, 1000);
            }

            @Override
            public void onResponse(@NonNull Call call, okhttp3.Response resp) {
                try (okhttp3.ResponseBody body = resp.body()) {
                    if (!downlinkCancelled && resp.isSuccessful() && body != null) {
                        if (resp.code() != 204) { // 204 = no content
                            String s = body.string();
                            org.json.JSONObject json = new org.json.JSONObject(s);
                            String b64 = json.getString("b64");
                            byte[] mavlinkBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);

                            // Call the callback instead of directly writing to port
                            // Inside pollOnce
                            callback.onReceived(mavlinkBytes);

                        }
                    }
                } catch (Exception e) {
                    Log.e("HTTP", "downlink parse error", e);
                } finally {
                    if (!downlinkCancelled) {
                        pollOnce(finalUrl, callback);
                    }
                }
            }
        });
    }


    private void retry(String url, DownlinkCallback callback, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> startDownlinkPolling(url, callback), delayMs);
    }

}
