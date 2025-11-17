package com.dowdah.asknow.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import com.dowdah.asknow.data.api.WebSocketClient;
import com.dowdah.asknow.data.local.dao.PendingMessageDao;
import com.dowdah.asknow.data.local.entity.PendingMessageEntity;
import com.dowdah.asknow.data.model.WebSocketMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class MessageRepository {
    private static final String TAG = "MessageRepository";
    // 使用 AppConstants 替代硬编码值
    private static final int MAX_RETRY_COUNT = com.dowdah.asknow.constants.AppConstants.MAX_RETRY_COUNT;
    private static final long INITIAL_RETRY_DELAY = com.dowdah.asknow.constants.AppConstants.INITIAL_RETRY_DELAY_MS;
    private static final int RETRY_BACKOFF_MULTIPLIER = com.dowdah.asknow.constants.AppConstants.RETRY_BACKOFF_MULTIPLIER;
    
    private final PendingMessageDao pendingMessageDao;
    private final com.dowdah.asknow.data.local.dao.MessageDao messageDao;
    private final com.dowdah.asknow.data.api.ApiService apiService;
    private final Context context;
    private final ExecutorService executor;
    private final Gson gson;
    private WebSocketClient webSocketClient;
    private boolean isNetworkAvailable = false;
    private ConnectivityManager.NetworkCallback networkCallback;
    
    @Inject
    public MessageRepository(
        @ApplicationContext Context context, 
        PendingMessageDao pendingMessageDao,
        com.dowdah.asknow.data.local.dao.MessageDao messageDao,
        com.dowdah.asknow.data.api.ApiService apiService
    ) {
        this.context = context;
        this.pendingMessageDao = pendingMessageDao;
        this.messageDao = messageDao;
        this.apiService = apiService;
        this.executor = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
        
        registerNetworkCallback();
        checkNetworkStatus();
    }
    
    public void setWebSocketClient(WebSocketClient client) {
        this.webSocketClient = client;
    }
    
    /**
     * Send message through WebSocket. If offline, save to database for later.
     */
    public void sendMessage(String messageType, JsonObject data) {
        String messageId = UUID.randomUUID().toString();
        WebSocketMessage message = new WebSocketMessage(
            messageType,
            data,
            String.valueOf(System.currentTimeMillis()),
            messageId
        );
        
        if (webSocketClient != null && webSocketClient.isConnected()) {
            // Send directly if connected
            webSocketClient.sendMessage(message);
            Log.d(TAG, "Message sent directly: " + messageId);
        } else {
            // Save to database if offline
            savePendingMessage(messageType, message, messageId);
            Log.d(TAG, "Message saved for later: " + messageId);
        }
    }
    
    /**
     * Save message to database for offline sending
     */
    private void savePendingMessage(String messageType, WebSocketMessage message, String messageId) {
        executor.execute(() -> {
            String payload = gson.toJson(message);
            PendingMessageEntity entity = new PendingMessageEntity(
                messageType,
                payload,
                0,
                System.currentTimeMillis(),
                messageId
            );
            pendingMessageDao.insert(entity);
            Log.d(TAG, "Pending message saved to database");
        });
    }
    
    /**
     * Called when WebSocket connection is established
     */
    public void onWebSocketConnected() {
        Log.d(TAG, "WebSocket connected, sending pending messages");
        sendPendingMessages();
    }
    
    /**
     * Send all pending messages from database
     */
    private void sendPendingMessages() {
        executor.execute(() -> {
            List<PendingMessageEntity> pendingMessages = pendingMessageDao.getAllPendingMessages();
            Log.d(TAG, "Found " + pendingMessages.size() + " pending messages");
            
            for (PendingMessageEntity entity : pendingMessages) {
                if (entity.getRetryCount() >= MAX_RETRY_COUNT) {
                    Log.w(TAG, "Message exceeded max retries, removing: " + entity.getMessageId());
                    pendingMessageDao.deleteMessage(entity.getId());
                    continue;
                }
                
                try {
                    WebSocketMessage message = gson.fromJson(entity.getPayload(), WebSocketMessage.class);
                    if (webSocketClient != null && webSocketClient.isConnected()) {
                        webSocketClient.sendMessage(message);
                        Log.d(TAG, "Pending message sent: " + entity.getMessageId());
                        // Don't delete yet, wait for ACK from server
                    } else {
                        Log.w(TAG, "WebSocket disconnected during sending");
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error sending pending message", e);
                    pendingMessageDao.updateRetryCount(entity.getId(), entity.getRetryCount() + 1);
                }
            }
        });
    }
    
    /**
     * Called when ACK is received from server
     */
    public void onMessageAcknowledged(String messageId) {
        executor.execute(() -> {
            PendingMessageEntity entity = pendingMessageDao.getMessageByMessageId(messageId);
            if (entity != null) {
                pendingMessageDao.deleteMessage(entity.getId());
                Log.d(TAG, "Pending message acknowledged and removed: " + messageId);
            }
        });
    }
    
    /**
     * Register network callback to monitor connectivity
     */
    private void registerNetworkCallback() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            return;
        }
        
        NetworkRequest networkRequest = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network available");
                isNetworkAvailable = true;
                onNetworkAvailable();
            }
            
            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network lost");
                isNetworkAvailable = false;
            }
        };
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }
    
    /**
     * Check current network status
     */
    private void checkNetworkStatus() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            return;
        }
        
        Network network = connectivityManager.getActiveNetwork();
        if (network != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            isNetworkAvailable = capabilities != null && 
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
    }
    
    /**
     * Called when network becomes available
     */
    private void onNetworkAvailable() {
        if (webSocketClient != null && !webSocketClient.isConnected()) {
            Log.d(TAG, "Network available, attempting to reconnect WebSocket");
            webSocketClient.connect();
        }
    }
    
    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }
    
    /**
     * Get count of pending messages
     */
    public int getPendingMessageCount() {
        try {
            return pendingMessageDao.getAllPendingMessages().size();
        } catch (Exception e) {
            Log.e(TAG, "Error getting pending message count", e);
            return 0;
        }
    }
    
    /**
     * 获取指定问题的未读消息数量
     */
    public androidx.lifecycle.LiveData<Integer> getUnreadMessageCount(long questionId, long currentUserId) {
        return messageDao.getUnreadMessageCountLive(questionId, currentUserId);
    }
    
    /**
     * 获取指定问题的未读消息数量（异步）
     * 注意：此方法在后台线程执行，避免ANR风险
     * 
     * @param questionId 问题ID
     * @param currentUserId 当前用户ID
     * @param callback 回调接口
     */
    public void getUnreadMessageCountAsync(long questionId, long currentUserId, UnreadCountCallback callback) {
        executor.execute(() -> {
            try {
                int count = messageDao.getUnreadMessageCount(questionId, currentUserId);
                if (callback != null) {
                    callback.onCountReceived(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting unread message count", e);
                if (callback != null) {
                    callback.onError(getDetailedErrorMessage(e));
                }
            }
        });
    }
    
    /**
     * 未读消息数量回调接口
     */
    public interface UnreadCountCallback {
        void onCountReceived(int count);
        void onError(String error);
    }
    
    /**
     * 标记指定问题的所有消息为已读
     */
    public void markMessagesAsRead(String token, long questionId, long currentUserId, MarkReadCallback callback) {
        executor.execute(() -> {
            try {
                // 先更新本地数据库
                messageDao.markMessagesAsRead(questionId, currentUserId);
                Log.d(TAG, "Marked messages as read locally for question " + questionId);
                
                // 然后通知服务器（如果网络可用）
                if (isNetworkAvailable && apiService != null) {
                    String authHeader = "Bearer " + token;
                    JsonObject requestBody = new JsonObject();
                    requestBody.addProperty("questionId", questionId);
                    
                    apiService.markMessagesAsRead(authHeader, requestBody).enqueue(new retrofit2.Callback<JsonObject>() {
                        @Override
                        public void onResponse(retrofit2.Call<JsonObject> call, retrofit2.Response<JsonObject> response) {
                            if (response.isSuccessful()) {
                                Log.d(TAG, "Successfully marked messages as read on server");
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            } else {
                                Log.w(TAG, "Failed to mark messages as read on server: " + response.code());
                                // 本地已更新，不算失败
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            }
                        }
                        
                        @Override
                        public void onFailure(retrofit2.Call<JsonObject> call, Throwable t) {
                            Log.e(TAG, "Error marking messages as read on server", t);
                            // 本地已更新，不算失败
                            if (callback != null) {
                                callback.onSuccess();
                            }
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error marking messages as read", e);
                if (callback != null) {
                    String errorMessage = getDetailedErrorMessage(e);
                    callback.onError(errorMessage);
                }
            }
        });
    }
    
    /**
     * 标记已读回调接口
     */
    public interface MarkReadCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * 带重试机制的标记已读
     * 使用指数退避策略重试失败的网络请求
     */
    public void markMessagesAsReadWithRetry(String token, long questionId, long currentUserId, MarkReadCallback callback) {
        markMessagesAsReadWithRetry(token, questionId, currentUserId, callback, 0);
    }
    
    private void markMessagesAsReadWithRetry(String token, long questionId, long currentUserId, MarkReadCallback callback, int retryCount) {
        executor.execute(() -> {
            try {
                // 先更新本地数据库
                messageDao.markMessagesAsRead(questionId, currentUserId);
                Log.d(TAG, "Marked messages as read locally for question " + questionId);
                
                // 然后通知服务器（如果网络可用）
                if (isNetworkAvailable && apiService != null) {
                    String authHeader = "Bearer " + token;
                    JsonObject requestBody = new JsonObject();
                    requestBody.addProperty("questionId", questionId);
                    
                    apiService.markMessagesAsRead(authHeader, requestBody).enqueue(new retrofit2.Callback<JsonObject>() {
                        @Override
                        public void onResponse(retrofit2.Call<JsonObject> call, retrofit2.Response<JsonObject> response) {
                            if (response.isSuccessful()) {
                                Log.d(TAG, "Successfully marked messages as read on server");
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            } else {
                                // 服务器返回错误，尝试重试
                                handleRetry(token, questionId, currentUserId, callback, retryCount, 
                                    "Server error: " + response.code());
                            }
                        }
                        
                        @Override
                        public void onFailure(retrofit2.Call<JsonObject> call, Throwable t) {
                            // 网络错误，尝试重试
                            handleRetry(token, questionId, currentUserId, callback, retryCount, 
                                "Network error: " + t.getMessage());
                        }
                    });
                } else {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error marking messages as read", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }
    
    /**
     * 处理重试逻辑
     */
    private void handleRetry(String token, long questionId, long currentUserId, 
                            MarkReadCallback callback, int retryCount, String error) {
        if (retryCount < MAX_RETRY_COUNT) {
            long delay = INITIAL_RETRY_DELAY * (long) Math.pow(RETRY_BACKOFF_MULTIPLIER, retryCount);
            Log.w(TAG, error + ". Retrying in " + delay + "ms (attempt " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");
            
            // 延迟后重试
            // 使用非弃用的 Handler 构造函数（显式传入 null 作为 callback）
            new android.os.Handler(android.os.Looper.getMainLooper(), null).postDelayed(() -> {
                markMessagesAsReadWithRetry(token, questionId, currentUserId, callback, retryCount + 1);
            }, delay);
        } else {
            Log.e(TAG, "Max retry count reached for marking messages as read. " + error);
            // 本地已更新，不算彻底失败
            if (callback != null) {
                callback.onSuccess();
            }
        }
    }
    
    /**
     * 获取详细的错误信息
     * 
     * @param error 异常对象
     * @return 用户友好的错误信息
     */
    private String getDetailedErrorMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        
        // 网络超时
        if (error instanceof java.net.SocketTimeoutException) {
            return "网络连接超时，请检查网络后重试";
        }
        
        // 无网络连接
        if (error instanceof java.net.UnknownHostException) {
            return "无法连接到服务器，请检查网络设置";
        }
        
        // 连接被拒绝
        if (error instanceof java.net.ConnectException) {
            return "服务器拒绝连接，请稍后重试";
        }
        
        // 通用IO错误
        if (error instanceof java.io.IOException) {
            return "网络错误: " + error.getMessage();
        }
        
        // 数据库错误
        if (error instanceof android.database.sqlite.SQLiteException) {
            return "数据库错误，请稍后重试";
        }
        
        // 其他错误
        String message = error.getMessage();
        return message != null && !message.isEmpty() ? 
            "操作失败: " + message : "操作失败，请重试";
    }
    
    /**
     * Clean up resources to prevent memory leaks
     * Should be called when the repository is no longer needed
     */
    public void cleanup() {
        // Unregister network callback
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = 
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                    Log.d(TAG, "Network callback unregistered");
                } catch (Exception e) {
                    Log.e(TAG, "Error unregistering network callback", e);
                }
            }
            networkCallback = null;
        }
        
        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Log.d(TAG, "Executor shutdown");
        }
    }
}

