package com.example.ussoi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Handles a persistent WebSocket connection to replace HTTP long-polling.
 * This class is more efficient for real-time, bidirectional communication.
 */
public class websocket_handel {

    private static final String TAG = "WebSocketHandler";
    private static final int NORMAL_CLOSURE_STATUS = 1000;
    private static final long RECONNECT_INTERVAL_MS = 5000; // Retry connection every 5 seconds

    private final String wsUrl;
    private final OkHttpClient client;
    private final MessageCallback callback;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private WebSocket webSocket;
    private boolean isManuallyDisconnected = false;

    /**
     * Callback interface for the consumer to handle WebSocket events.
     * This replaces the old `DownlinkCallback`.
     */
    public interface MessageCallback {
        void onOpen();
        void onMavlinkReceived(byte[] mavlinkBytes);
        void onClosed(String reason);
        void onError(String error);
    }

    /**
     * Constructor for the WebSocket handler.
     *
     * @param client   A shared OkHttpClient instance.
     * @param url      The server URL (e.g., "192.168.1.10:8080"). The "ws://" prefix will be added automatically.
     * @param callback The callback to handle incoming messages and connection events.
     */
    public websocket_handel(OkHttpClient client, String url, MessageCallback callback) {
        this.client = client;
        this.callback = callback;

        // Convert http URL to ws URL if necessary and add prefix
        String tempUrl;
        if (url.startsWith("https://")) {
            tempUrl = url.substring(8);
            this.wsUrl = "wss://" + tempUrl;
        } else if (url.startsWith("http://")) {
            tempUrl = url.substring(7);
            this.wsUrl = "ws://" + tempUrl;
        } else if (url.startsWith("ws://") || url.startsWith("wss://")) {
            this.wsUrl = url; // already correct
        } else {
            this.wsUrl = "ws://" + url; // fallback
        }

    }

    /**
     * Establishes the WebSocket connection.
     * Replaces the initial call to `startDownlinkPolling`.
     */
    public void connect() {
        if (webSocket != null) {
            Log.w(TAG, "Already connected or trying to connect.");
            return;
        }
        isManuallyDisconnected = false;
        Log.i(TAG, "Connecting to WebSocket: " + wsUrl);
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new SocketListener());
    }

    /**
     * Closes the WebSocket connection gracefully.
     * Replaces `stopDownlinkPolling`.
     */
    public void disconnect() {
        isManuallyDisconnected = true;
        reconnectHandler.removeCallbacksAndMessages(null); // Stop any pending reconnect attempts
        if (webSocket != null) {
            webSocket.close(NORMAL_CLOSURE_STATUS, "Client disconnected.");
            webSocket = null;
        }
    }

    /**
     * Sends MAVLink data to the server over the WebSocket.
     * This is the direct replacement for the `sendData` method in `http_handel`.
     *
     * @param data   The raw byte array of MAVLink data.
     * @param config The configuration string to be sent.
     * @return true if the message was successfully queued for sending.
     */
    public boolean sendData(byte[] data, String config) {
        if (webSocket == null) {
            Log.e(TAG, "Cannot send data, WebSocket is not connected.");
            return false;
        }

        // Create the same JSON payload as the HTTP version
        String b64 = ByteString.of(data).base64();
        JSONObject obj = new JSONObject();
        try {
            obj.put("mavlink_out", b64);
            obj.put("config", config);
            obj.put("encoding", "base64");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create JSON for sending", e);
            return false;
        }

        return webSocket.send(obj.toString());
    }

    private void attemptReconnect() {
        if (isManuallyDisconnected) return; // Don't reconnect if disconnect() was called

        webSocket = null; // Ensure the old socket is cleared
        Log.i(TAG, "Will attempt to reconnect in " + RECONNECT_INTERVAL_MS / 1000 + " seconds.");
        reconnectHandler.postDelayed(this::connect, RECONNECT_INTERVAL_MS);
    }

    private final class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
            Log.i(TAG, "WebSocket connection opened successfully.");
            webSocket = ws;
            if (callback != null) callback.onOpen();
        }

        /**
         * This method is the core of receiving data.
         * It replaces the entire long-polling loop (`pollOnce`, `onResponse`, `retry`).
         */
        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            try {
                JSONObject json = new JSONObject(text);
                if (json.has("b64")) {
                    String b64 = json.getString("b64");
                    byte[] mavlinkBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    if (callback != null) {
                        callback.onMavlinkReceived(mavlinkBytes);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing incoming message", e);
                if (callback != null) callback.onError("JSON parse error on message: " + text);
            }
        }

        @Override
        public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.w(TAG, "Server closing connection: " + code + " " + reason);
            ws.close(NORMAL_CLOSURE_STATUS, null);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
            Log.w(TAG, "Connection closed: " + code + " " + reason);
            if (callback != null) callback.onClosed(reason);
            attemptReconnect();
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
            Log.e(TAG, "Connection failure", t);
            if (callback != null) callback.onError("Failure: " + t.getMessage());
            attemptReconnect();
        }
    }
}