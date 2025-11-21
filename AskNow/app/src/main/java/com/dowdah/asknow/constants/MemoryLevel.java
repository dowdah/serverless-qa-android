package com.dowdah.asknow.constants;

/**
 * 内存级别常量
 * 
 * 用于 Application.onTrimMemory() 回调中判断内存压力级别
 * 这些值对应 Android ComponentCallbacks2 接口中的常量定义
 * 由于这些常量在 API 36 中已被标记为弃用，因此在此处重新定义以避免使用已弃用的 API
 */
public final class MemoryLevel {
    
    /**
     * 应用正在运行，设备内存开始不足
     * 对应值：5
     */
    public static final int RUNNING_MODERATE = 5;
    
    /**
     * 应用正在运行，设备内存较低
     * 应考虑释放非关键资源
     * 对应值：10
     */
    public static final int RUNNING_LOW = 10;
    
    /**
     * 应用正在运行，设备内存极低
     * 应立即释放所有可释放的资源
     * 对应值：15
     */
    public static final int RUNNING_CRITICAL = 15;
    
    /**
     * UI 已隐藏，用户切换到其他应用
     * 可以释放 UI 相关的资源
     * 对应值：20
     */
    public static final int UI_HIDDEN = 20;
    
    /**
     * 应用已进入后台，设备内存不足
     * 对应值：40
     */
    public static final int BACKGROUND = 40;
    
    /**
     * 应用在后台，设备内存较低
     * 应用可能很快被终止
     * 对应值：60
     */
    public static final int MODERATE = 60;
    
    /**
     * 应用在后台，设备内存极低
     * 应用很可能马上被终止
     * 应释放所有可以释放的资源
     * 对应值：80
     */
    public static final int COMPLETE = 80;
    
    // 私有构造函数防止实例化
    private MemoryLevel() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}









