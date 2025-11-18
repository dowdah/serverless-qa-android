package com.dowdah.asknow.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dowdah.asknow.utils.ErrorHandler;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * 消息仓库
 * 
 * 负责消息相关的数据操作，包括：
 * - 标记消息为已读（本地数据库 + 服务器同步）
 * - 获取未读消息数量
 * - 监控网络状态
 * 
 * 注意：消息发送通过 HTTP API 在 ChatViewModel 中处理，不在此类中
 */
@Singleton
public class MessageRepository {
    private static final String TAG = "MessageRepository";
    // 重试相关常量（用于标记已读功能）
    private static final int MAX_RETRY_COUNT = com.dowdah.asknow.constants.AppConstants.MAX_RETRY_COUNT;
    private static final long INITIAL_RETRY_DELAY = com.dowdah.asknow.constants.AppConstants.INITIAL_RETRY_DELAY_MS;
    private static final int RETRY_BACKOFF_MULTIPLIER = com.dowdah.asknow.constants.AppConstants.RETRY_BACKOFF_MULTIPLIER;
    
    private final com.dowdah.asknow.data.local.dao.MessageDao messageDao;
    private final com.dowdah.asknow.data.api.ApiService apiService;
    private final Context context;
    private final ExecutorService executor;
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
        @NonNull com.dowdah.asknow.data.local.dao.MessageDao messageDao,
        @NonNull com.dowdah.asknow.data.api.ApiService apiService,
        @NonNull @javax.inject.Named("single") ExecutorService executor
    ) {
        this.context = context;
        this.messageDao = messageDao;
        this.apiService = apiService;
        this.executor = executor;
        
        registerNetworkCallback();
        checkNetworkStatus();
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

