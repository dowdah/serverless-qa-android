package com.dowdah.asknow.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 重试工具类 - 提供带指数退避的重试机制
 */
public class RetryHelper {
    private static final String TAG = "RetryHelper";
    
    // 默认重试配置
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY = 1000; // 1秒
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_DELAY = 30000; // 30秒
    
    private final Handler handler;
    private final int maxRetries;
    private final long initialDelay;
    private final double backoffMultiplier;
    private final long maxDelay;
    
    /**
     * 重试配置类
     */
    public static class Config {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialDelay = DEFAULT_INITIAL_DELAY;
        private double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;
        private long maxDelay = DEFAULT_MAX_DELAY;
        
        public Config setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Config setInitialDelay(long initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }
        
        public Config setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public Config setMaxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }
    }
    
    /**
     * 重试任务接口
     */
    public interface RetryTask {
        void execute(int attemptNumber);
    }
    
    /**
     * 重试回调接口
     */
    public interface RetryCallback {
        /**
         * 是否应该重试
         * @param attemptNumber 当前尝试次数（从1开始）
         * @param error 错误信息
         * @return true表示应该重试，false表示放弃
         */
        boolean shouldRetry(int attemptNumber, Throwable error);
        
        /**
         * 所有重试都失败后调用
         * @param lastError 最后一次的错误
         */
        void onAllRetriesFailed(Throwable lastError);
    }
    
    public RetryHelper() {
        this(new Config());
    }
    
    public RetryHelper(Config config) {
        this.handler = new Handler(Looper.getMainLooper(), null);
        this.maxRetries = config.maxRetries;
        this.initialDelay = config.initialDelay;
        this.backoffMultiplier = config.backoffMultiplier;
        this.maxDelay = config.maxDelay;
    }
    
    /**
     * 执行带重试的任务
     * 
     * @param task 要执行的任务
     * @param callback 重试回调
     */
    public void executeWithRetry(RetryTask task, RetryCallback callback) {
        executeWithRetry(task, callback, 1);
    }
    
    /**
     * 内部递归重试方法
     */
    private void executeWithRetry(RetryTask task, RetryCallback callback, int attemptNumber) {
        try {
            task.execute(attemptNumber);
        } catch (Exception e) {
            handleRetry(task, callback, attemptNumber, e);
        }
    }
    
    /**
     * 处理重试逻辑
     */
    private void handleRetry(RetryTask task, RetryCallback callback, int attemptNumber, Throwable error) {
        if (attemptNumber >= maxRetries) {
            Log.e(TAG, "Max retries (" + maxRetries + ") reached, giving up");
            callback.onAllRetriesFailed(error);
            return;
        }
        
        if (!callback.shouldRetry(attemptNumber, error)) {
            Log.d(TAG, "Callback indicated not to retry");
            callback.onAllRetriesFailed(error);
            return;
        }
        
        long delay = calculateDelay(attemptNumber);
        Log.d(TAG, "Retry attempt " + attemptNumber + "/" + maxRetries + " scheduled in " + delay + "ms");
        
        handler.postDelayed(() -> {
            executeWithRetry(task, callback, attemptNumber + 1);
        }, delay);
    }
    
    /**
     * 计算延迟时间（指数退避）
     */
    private long calculateDelay(int attemptNumber) {
        long delay = (long) (initialDelay * Math.pow(backoffMultiplier, attemptNumber - 1));
        return Math.min(delay, maxDelay);
    }
    
    /**
     * 判断是否为网络错误
     */
    public static boolean isNetworkError(Throwable error) {
        if (error == null) {
            return false;
        }
        
        String errorMessage = error.getMessage();
        if (errorMessage == null) {
            return false;
        }
        
        // 常见的网络错误关键词
        return errorMessage.contains("timeout") ||
               errorMessage.contains("network") ||
               errorMessage.contains("connection") ||
               errorMessage.contains("socket") ||
               errorMessage.contains("unreachable") ||
               error instanceof java.net.SocketTimeoutException ||
               error instanceof java.net.UnknownHostException ||
               error instanceof java.io.IOException;
    }
    
    /**
     * 判断是否为可重试的错误
     */
    public static boolean isRetryableError(Throwable error) {
        if (error == null) {
            return false;
        }
        
        // 网络错误通常可重试
        if (isNetworkError(error)) {
            return true;
        }
        
        // 服务器5xx错误可重试
        String errorMessage = error.getMessage();
        if (errorMessage != null && errorMessage.contains("500")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 取消所有待执行的重试
     */
    public void cancelAllRetries() {
        handler.removeCallbacksAndMessages(null);
    }
}

