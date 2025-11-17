package com.dowdah.asknow;

import android.app.Application;
import android.util.Log;

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
 */
@HiltAndroidApp
public class AskNowApplication extends Application {
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
        
        // Connect WebSocket if user is logged in
        if (prefsManager.isLoggedIn()) {
            webSocketManager.connect();
            Log.d(TAG, "WebSocket connected for logged in user");
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
                Log.d(TAG, "Memory running moderate - consider releasing caches");
                break;
                
            case MemoryLevel.RUNNING_LOW:
                // 设备内存较低，应用正在运行
                Log.d(TAG, "Memory running low - releasing non-critical resources");
                cleanupResources();
                break;
                
            case MemoryLevel.RUNNING_CRITICAL:
                // 设备内存极低，应用正在运行但可能很快被终止
                Log.d(TAG, "Memory running critical - aggressively releasing resources");
                cleanupResources();
                break;
                
            case MemoryLevel.UI_HIDDEN:
                // UI不再可见，可以释放UI相关资源
                Log.d(TAG, "UI hidden - releasing UI resources");
                break;
                
            case MemoryLevel.BACKGROUND:
            case MemoryLevel.MODERATE:
            case MemoryLevel.COMPLETE:
                // 应用在后台，系统内存不足
                Log.d(TAG, "App in background with low memory - cleaning up resources");
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

