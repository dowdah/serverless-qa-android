package com.dowdah.asknow;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.dowdah.asknow.constants.MemoryLevel;
import com.dowdah.asknow.data.repository.MessageRepository;
import com.dowdah.asknow.data.repository.QuestionRepository;
import com.dowdah.asknow.utils.SharedPreferencesManager;
import com.dowdah.asknow.utils.WebSocketManager;
import com.google.android.material.color.DynamicColors;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * AskNow应用程序类
 * 负责全局资源的初始化和清理
 * 
 * 主要职责：
 * - WebSocket连接管理
 * - Repository资源清理
 * - 全局配置初始化
 * - 应用前后台状态监听
 */
@HiltAndroidApp
public class AskNowApplication extends Application implements LifecycleEventObserver {
    private static final String TAG = "AskNowApplication";
    
    @Inject
    WebSocketManager webSocketManager;
    
    @Inject
    SharedPreferencesManager prefsManager;
    
    @Inject
    MessageRepository messageRepository;
    
    @Inject
    QuestionRepository questionRepository;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate");
        
        // 启用Material You动态颜色（从系统壁纸提取）
        // Android 12+ (API 31+) 支持，minSdk=33 已满足要求
        // 应用颜色会自动跟随系统壁纸主题变化，同时支持明暗主题切换
        DynamicColors.applyToActivitiesIfAvailable(this);
        Log.d(TAG, "Dynamic Colors enabled - colors will follow system wallpaper");
        
        // 注册应用生命周期监听器，自动处理前后台状态变化
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        Log.d(TAG, "Application lifecycle observer registered");
        
        // Connect WebSocket if user is logged in
        if (prefsManager.isLoggedIn()) {
            webSocketManager.connect();
            Log.d(TAG, "WebSocket connected for logged in user");
        }
    }
    
    /**
     * 应用生命周期事件监听
     * 自动处理应用前后台状态变化
     * 
     * @param source 生命周期拥有者
     * @param event 生命周期事件
     */
    @Override
    public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
        if (event == Lifecycle.Event.ON_START) {
            // 应用进入前台
            Log.d(TAG, "App moved to FOREGROUND");
            onAppForeground();
        } else if (event == Lifecycle.Event.ON_STOP) {
            // 应用进入后台
            Log.d(TAG, "App moved to BACKGROUND");
            onAppBackground();
        }
    }
    
    /**
     * 应用进入前台时调用
     * 重新建立必要的连接
     */
    private void onAppForeground() {
        try {
            if (prefsManager != null && prefsManager.isLoggedIn()) {
                if (webSocketManager != null) {
                    webSocketManager.onAppForeground();
                    Log.d(TAG, "WebSocket re-enabled for foreground");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during app foreground transition", e);
        }
    }
    
    /**
     * 应用进入后台时调用
     * 断开连接以节省资源
     */
    private void onAppBackground() {
        try {
            if (webSocketManager != null) {
                webSocketManager.onAppBackground();
                Log.d(TAG, "WebSocket disabled for background");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during app background transition", e);
        }
    }
    
    /**
     * 注意：onTerminate() 只在模拟器中被调用，真实设备不会调用
     * 因此我们需要在onLowMemory()和onTrimMemory()中也进行清理
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application onTerminate - cleaning up resources");
        cleanupResources();
    }
    
    /**
     * 当系统内存不足时调用
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Application onLowMemory - cleaning up resources");
        cleanupResources();
    }
    
    /**
     * 当系统需要清理内存时调用
     * 根据不同的内存级别采取相应的资源清理策略
     * 
     * 策略说明：
     * - RUNNING_MODERATE/RUNNING_LOW: 不清理资源，应用仍在前台运行
     * - RUNNING_CRITICAL: 完全清理资源（包括线程池）
     * - UI_HIDDEN/BACKGROUND/MODERATE: 由 ProcessLifecycleOwner 自动处理，这里不做额外操作
     * - COMPLETE: 完全清理资源
     * 
     * @param level 内存清理级别
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "Application onTrimMemory: level=" + level);
        
        // 根据内存压力级别采取不同的清理策略
        switch (level) {
            case MemoryLevel.RUNNING_MODERATE:
                // 设备内存开始不足，应用正在运行
                // 不采取任何清理动作，因为应用正在前台使用
                Log.d(TAG, "Memory running moderate - no action (app in foreground)");
                break;
                
            case MemoryLevel.RUNNING_LOW:
                // 设备内存较低，应用正在运行
                // 不采取任何清理动作，因为应用正在前台使用
                Log.d(TAG, "Memory running low - no action (app in foreground)");
                break;
                
            case MemoryLevel.RUNNING_CRITICAL:
                // 设备内存极低，应用正在运行但可能很快被终止
                // 完全清理资源以避免被系统杀死
                Log.d(TAG, "Memory running critical - aggressively releasing all resources");
                cleanupResources();
                break;
                
            case MemoryLevel.UI_HIDDEN:
            case MemoryLevel.BACKGROUND:
            case MemoryLevel.MODERATE:
                // 应用在后台的情况，已由 ProcessLifecycleOwner 的 ON_STOP 事件处理
                // 这里不需要额外操作，避免重复清理
                Log.d(TAG, "App in background - already handled by lifecycle observer");
                break;
                
            case MemoryLevel.COMPLETE:
                // 应用在后台，系统内存极度紧张
                // 完全清理资源，应用可能即将被终止
                Log.d(TAG, "App in background with critical memory pressure - cleaning up all resources");
                cleanupResources();
                break;
                
            default:
                Log.d(TAG, "Memory trim level: " + level);
                break;
        }
    }
    
    /**
     * 清理所有资源
     * 包括WebSocket连接、网络回调、线程池等
     * 
     * 使用场景：
     * - RUNNING_CRITICAL: 应用在前台但内存极度紧张
     * - COMPLETE: 应用在后台且内存极度紧张
     * - onLowMemory: 系统内存严重不足
     */
    private void cleanupResources() {
        try {
            // 清理WebSocket连接
            if (webSocketManager != null) {
                webSocketManager.cleanup();
                Log.d(TAG, "WebSocket cleaned up");
            }
            
            // 清理MessageRepository（注销网络回调）
            if (messageRepository != null) {
                messageRepository.cleanup();
                Log.d(TAG, "MessageRepository cleaned up");
            }
            
            // 清理QuestionRepository（关闭线程池）
            if (questionRepository != null) {
                questionRepository.cleanup();
                Log.d(TAG, "QuestionRepository cleaned up");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during resource cleanup", e);
        }
    }
}

