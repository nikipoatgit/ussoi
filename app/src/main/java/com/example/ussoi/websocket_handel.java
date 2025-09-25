package com.example.ussoi;

import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Handles a WebSocket connection with built-in send/receive support.
 */
public class websocket_handel {

    private OkHttpClient client;
    private WebSocket webSocket;

    /** Callback interface for received messages */
    public interface MessageCallback {
        void onTextMessage(String message);
        void onBinaryMessage(ByteString bytes);
        void onConnectionOpened();
        void onConnectionClosed(int code, String reason);
        void onError(Throwable t);
    }

    private final MessageCallback callback;

    public websocket_handel(String wsUrl, MessageCallback callback) {
        this.callback = callback;
        client = new OkHttpClient();

        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (callback != null) callback.onConnectionOpened();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (callback != null) callback.onTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (callback != null) callback.onBinaryMessage(bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
                if (callback != null) callback.onConnectionClosed(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e("WS", "Error: " + t.getMessage(), t);
                if (callback != null) callback.onError(t);
            }
        });
    }

    /** Send a text message to the server */
    public void send(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    /** Send binary data if needed */
    public void send(ByteString bytes) {
        if (webSocket != null) {
            webSocket.send(bytes);
        }
    }

    /** Close connection gracefully */
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Closing normally");
            client.dispatcher().executorService().shutdown();
        }
    }
}
