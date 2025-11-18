package com.dowdah.asknow.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dowdah.asknow.data.api.WebSocketClient;
import com.dowdah.asknow.data.local.dao.PendingMessageDao;
import com.dowdah.asknow.data.local.entity.PendingMessageEntity;
import com.dowdah.asknow.data.model.WebSocketMessage;
import com.dowdah.asknow.utils.ErrorHandler;
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
    private NetworkAvailableListener networkAvailableListener;
    
    /**
     * 网络恢复监听器接口
     */
    public interface NetworkAvailableListener {
        void onNetworkAvailable();
    }
    
    @Inject
    public MessageRepository(
        @NonNull @ApplicationContext Context context, 
        @NonNull PendingMessageDao pendingMessageDao,
        @NonNull com.dowdah.asknow.data.local.dao.MessageDao messageDao,
        @NonNull com.dowdah.asknow.data.api.ApiService apiService,
        @NonNull @javax.inject.Named("single") ExecutorService executor,
        @NonNull Gson gson
    ) {
        this.context = context;
        this.pendingMessageDao = pendingMessageDao;
        this.messageDao = messageDao;
        this.apiService = apiService;
        this.executor = executor;
        this.gson = gson;
        
        registerNetworkCallback();
        checkNetworkStatus();
    }
    
    public void setWebSocketClient(@Nullable WebSocketClient client) {
        this.webSocketClient = client;
    }
    
    /**
     * 设置网络恢复监听器
     * 
     * @param listener 网络恢复监听器
     */
    public void setNetworkAvailableListener(@Nullable NetworkAvailableListener listener) {
        this.networkAvailableListener = listener;
    }
    
    /**
     * 检查线程池是否可用
     * 
     * 防止在线程池关闭后提交任务导致 RejectedExecutionException
     * 
     * @return true 如果线程池可用，false 否则
     */
    private boolean isExecutorAvailable() {
        if (executor == null) {
            Log.e(TAG, "Executor is null");
            return false;
        }
        if (executor.isShutdown()) {
            Log.e(TAG, "Executor is shutdown");
            return false;
        }
        if (executor.isTerminated()) {
            Log.e(TAG, "Executor is terminated");
            return false;
        }
        return true;
    }
    
    /**
     * 通过WebSocket发送消息。如果离线，保存到数据库待后续发送
     * 
     * @param messageType 消息类型
     * @param data 消息数据
     */
    public void sendMessage(@NonNull String messageType, @NonNull JsonObject data) {
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
     * 保存待发送消息到数据库（用于离线发送）
     * 
     * @param messageType 消息类型
     * @param message 消息对象
     * @param messageId 消息ID
     */
    private void savePendingMessage(String messageType, WebSocketMessage message, String messageId) {
        // 检查线程池是否可用
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot save pending message: executor not available");
            return;
        }
        
        executor.execute(() -> {
            try {
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
            } catch (Exception e) {
                Log.e(TAG, "Error saving pending message", e);
            }
        });
    }
    
    /**
     * WebSocket连接建立时调用
     */
    public void onWebSocketConnected() {
        Log.d(TAG, "WebSocket connected, sending pending messages");
        sendPendingMessages();
    }
    
    /**
     * 发送数据库中所有待发送消息
     */
    private void sendPendingMessages() {
        // 检查线程池是否可用（这是崩溃的关键位置）
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot send pending messages: executor not available");
            return;
        }
        
        executor.execute(() -> {
            try {
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
            } catch (Exception e) {
                Log.e(TAG, "Error in sendPendingMessages", e);
            }
        });
    }
    
    /**
     * 收到服务器ACK确认时调用
     * 
     * @param messageId 消息ID
     */
    public void onMessageAcknowledged(@NonNull String messageId) {
        // 检查线程池是否可用
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot acknowledge message: executor not available");
            return;
        }
        
        executor.execute(() -> {
            try {
                PendingMessageEntity entity = pendingMessageDao.getMessageByMessageId(messageId);
                if (entity != null) {
                    pendingMessageDao.deleteMessage(entity.getId());
                    Log.d(TAG, "Pending message acknowledged and removed: " + messageId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error acknowledging message", e);
            }
        });
    }
    
    /**
     * 注册网络回调以监控网络连接状态
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
     * 检查当前网络状态
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
     * 网络恢复可用时调用
     * 通知监听器而不是直接连接，避免重复连接
     */
    private void onNetworkAvailable() {
        Log.d(TAG, "Network available, notifying listener");
        if (networkAvailableListener != null) {
            networkAvailableListener.onNetworkAvailable();
        }
    }
    
    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }
    
    /**
     * 获取待发送消息数量
     * 
     * @return 待发送消息数量
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
     * 获取指定问题的未读消息数量（LiveData）
     * 
     * @param questionId 问题ID
     * @param currentUserId 当前用户ID
     * @return 未读消息数量的LiveData
     */
    @NonNull
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
    public void getUnreadMessageCountAsync(long questionId, long currentUserId, @Nullable UnreadCountCallback callback) {
        // 检查线程池是否可用
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot get unread count: executor not available");
            if (callback != null) {
                callback.onError("线程池不可用，无法获取未读消息数量");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                int count = messageDao.getUnreadMessageCount(questionId, currentUserId);
                if (callback != null) {
                    callback.onCountReceived(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting unread message count", e);
                if (callback != null) {
                    callback.onError(ErrorHandler.getDetailedErrorMessage(e));
                }
            }
        });
    }
    
    /**
     * 未读消息数量回调接口
     */
    public interface UnreadCountCallback {
        void onCountReceived(int count);
        void onError(@NonNull String error);
    }
    
    /**
     * 标记指定问题的所有消息为已读
     * 
     * @param token 认证令牌
     * @param questionId 问题ID
     * @param currentUserId 当前用户ID
     * @param callback 回调接口
     */
    public void markMessagesAsRead(@NonNull String token, long questionId, long currentUserId, @Nullable MarkReadCallback callback) {
        // 检查线程池是否可用
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot mark messages as read: executor not available");
            if (callback != null) {
                callback.onError("线程池不可用，无法标记消息为已读");
            }
            return;
        }
        
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
                    String errorMessage = ErrorHandler.getDetailedErrorMessage(e);
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
        void onError(@NonNull String error);
    }
    
    /**
     * 带重试机制的标记已读
     * 使用指数退避策略重试失败的网络请求
     * 
     * @param token 认证令牌
     * @param questionId 问题ID
     * @param currentUserId 当前用户ID
     * @param callback 回调接口
     */
    public void markMessagesAsReadWithRetry(@NonNull String token, long questionId, long currentUserId, @Nullable MarkReadCallback callback) {
        markMessagesAsReadWithRetry(token, questionId, currentUserId, callback, 0);
    }
    
    private void markMessagesAsReadWithRetry(String token, long questionId, long currentUserId, MarkReadCallback callback, int retryCount) {
        // 检查线程池是否可用
        if (!isExecutorAvailable()) {
            Log.e(TAG, "Cannot mark messages as read with retry: executor not available");
            if (callback != null) {
                callback.onError("线程池不可用，无法标记消息为已读");
            }
            return;
        }
        
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
            com.dowdah.asknow.utils.ThreadUtils.executeOnMainDelayed(() -> {
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
     * 清理资源以防止内存泄漏
     * 当Repository不再需要时应调用此方法
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

