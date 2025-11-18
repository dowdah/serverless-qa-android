package com.dowdah.asknow.data.api;

import android.util.Log;

import com.dowdah.asknow.data.model.WebSocketMessage;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket客户端
 * 管理WebSocket连接，提供自动重连功能
 */
public class WebSocketClient {
    private static final String TAG = "WebSocketClient";
    private static final int[] BACKOFF_DELAYS = com.dowdah.asknow.constants.AppConstants.WEBSOCKET_BACKOFF_DELAYS;
    private static final int MAX_RETRY_COUNT = com.dowdah.asknow.constants.AppConstants.WEBSOCKET_MAX_RETRY_COUNT;
    
    private WebSocket webSocket;
    private final OkHttpClient client;
    private final String url;
    private final WebSocketCallback callback;
    private int retryCount = 0;
    private boolean isManuallyDisconnected = false;
    private Thread reconnectThread;
    
    /**
     * WebSocket回调接口
     */
    public interface WebSocketCallback {
        /**
         * 连接建立时回调
         */
        void onConnected();
        
        /**
         * 收到消息时回调
         * 
         * @param message WebSocket消息
         */
        void onMessage(WebSocketMessage message);
        
        /**
         * 连接断开时回调
         */
        void onDisconnected();
        
        /**
         * 发生错误时回调
         * 
         * @param error 错误信息
         */
        void onError(Throwable error);
    }
    
    public WebSocketClient(OkHttpClient client, String url, WebSocketCallback callback) {
        this.client = client;
        this.url = url;
        this.callback = callback;
    }
    
    public void connect() {
        if (webSocket != null) {
            Log.d(TAG, "WebSocket already connected");
            return;
        }
        
        Log.d(TAG, "Connecting to WebSocket: " + url);
        isManuallyDisconnected = false;
        
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected");
                retryCount = 0;
                callback.onConnected();
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received message: " + text);
                try {
                    Gson gson = new Gson();
                    WebSocketMessage message = gson.fromJson(text, WebSocketMessage.class);
                    callback.onMessage(message);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing message", e);
                }
            }
            
            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                webSocket.close(com.dowdah.asknow.constants.AppConstants.WEBSOCKET_NORMAL_CLOSURE_CODE, null);
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                WebSocketClient.this.webSocket = null;
                callback.onDisconnected();
                
                if (!isManuallyDisconnected) {
                    reconnect();
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket error", t);
                WebSocketClient.this.webSocket = null;
                callback.onError(t);
                callback.onDisconnected();
                
                if (!isManuallyDisconnected) {
                    reconnect();
                }
            }
        });
    }
    
    public void disconnect() {
        isManuallyDisconnected = true;
        
        // 中断待处理的重连线程
        if (reconnectThread != null && reconnectThread.isAlive()) {
            reconnectThread.interrupt();
            reconnectThread = null;
            Log.d(TAG, "Reconnect thread interrupted");
        }
        
        if (webSocket != null) {
            webSocket.close(com.dowdah.asknow.constants.AppConstants.WEBSOCKET_NORMAL_CLOSURE_CODE, "Manual disconnect");
            webSocket = null;
        }
    }
    
    public void sendMessage(WebSocketMessage message) {
        if (webSocket != null) {
            Gson gson = new Gson();
            String json = gson.toJson(message);
            webSocket.send(json);
            Log.d(TAG, "Sent message: " + json);
        } else {
            Log.w(TAG, "Cannot send message: WebSocket not connected");
        }
    }
    
    public boolean isConnected() {
        return webSocket != null;
    }
    
    private void reconnect() {
        // 检查是否超过最大重连次数
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.e(TAG, "Max retry count reached. Giving up reconnection.");
            callback.onError(new Exception("Max retry count reached"));
            return;
        }
        
        // 取消之前的重连线程（如果存在）
        if (reconnectThread != null && reconnectThread.isAlive()) {
            reconnectThread.interrupt();
        }
        
        int delay = BACKOFF_DELAYS[Math.min(retryCount, BACKOFF_DELAYS.length - 1)];
        Log.d(TAG, "Reconnecting in " + delay + "ms (attempt " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");
        
        retryCount++;
        
        // 使用新线程处理延迟重连，避免阻塞 WebSocket 回调线程
        reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(delay);
                if (!isManuallyDisconnected) {
                    connect();
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Reconnect cancelled due to interrupt");
                Thread.currentThread().interrupt();
            }
        });
        reconnectThread.start();
    }
    
    /**
     * 重置重连计数器
     * 当手动重连时使用
     */
    public void resetRetryCount() {
        retryCount = 0;
    }
}

