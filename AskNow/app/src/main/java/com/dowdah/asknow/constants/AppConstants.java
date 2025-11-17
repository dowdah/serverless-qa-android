package com.dowdah.asknow.constants;

/**
 * 应用全局常量
 * 集中管理所有硬编码值，便于维护和配置
 */
public final class AppConstants {
    
    // ==================== 分页相关 ====================
    
    /**
     * 默认每页问题数量
     */
    public static final int DEFAULT_QUESTIONS_PAGE_SIZE = 20;
    
    /**
     * 默认每页消息数量
     */
    public static final int DEFAULT_MESSAGES_PAGE_SIZE = 50;
    
    /**
     * 最大每页问题数量
     */
    public static final int MAX_QUESTIONS_PAGE_SIZE = 100;
    
    /**
     * 最大每页消息数量
     */
    public static final int MAX_MESSAGES_PAGE_SIZE = 200;
    
    /**
     * 默认起始页码
     */
    public static final int DEFAULT_START_PAGE = 1;
    
    // ==================== 重试相关 ====================
    
    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT = 3;
    
    /**
     * 初始重试延迟（毫秒）
     */
    public static final long INITIAL_RETRY_DELAY_MS = 1000;
    
    /**
     * 重试退避倍数
     */
    public static final int RETRY_BACKOFF_MULTIPLIER = 2;
    
    // ==================== WebSocket相关 ====================
    
    /**
     * WebSocket重连延迟（毫秒）
     */
    public static final long WEBSOCKET_RECONNECT_DELAY_MS = 3000;
    
    /**
     * WebSocket心跳间隔（毫秒）
     */
    public static final long WEBSOCKET_HEARTBEAT_INTERVAL_MS = 30000;
    
    /**
     * WebSocket连接超时（毫秒）
     */
    public static final long WEBSOCKET_CONNECT_TIMEOUT_MS = 10000;
    
    // ==================== 文件上传相关 ====================
    
    /**
     * 最大文件大小（字节）10MB
     */
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    
    /**
     * 图片质量压缩比例
     */
    public static final int IMAGE_COMPRESS_QUALITY = 85;
    
    /**
     * 图片最大宽度
     */
    public static final int IMAGE_MAX_WIDTH = 1920;
    
    /**
     * 图片最大高度
     */
    public static final int IMAGE_MAX_HEIGHT = 1920;
    
    // ==================== 缓存相关 ====================
    
    /**
     * 数据同步间隔（毫秒）5分钟
     */
    public static final long SYNC_INTERVAL_MS = 5 * 60 * 1000;
    
    /**
     * 缓存过期时间（毫秒）24小时
     */
    public static final long CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000;
    
    // ==================== UI相关 ====================
    
    /**
     * 列表滚动加载阈值（距离底部的item数量）
     */
    public static final int SCROLL_LOAD_THRESHOLD = 5;
    
    /**
     * Toast显示时长（毫秒）
     */
    public static final int TOAST_DURATION_MS = 2000;
    
    /**
     * 防抖延迟（毫秒）防止重复点击
     */
    public static final long DEBOUNCE_DELAY_MS = 500;
    
    // ==================== 数据库相关 ====================
    
    /**
     * 数据库版本号
     */
    public static final int DATABASE_VERSION = 5;
    
    /**
     * 数据库名称
     */
    public static final String DATABASE_NAME = "asknow_database";
    
    // ==================== 日志相关 ====================
    
    /**
     * 是否启用详细日志（根据BuildConfig动态设置）
     */
    public static final boolean ENABLE_VERBOSE_LOGGING = true; // 在发布版本中设置为false
    
    /**
     * 日志TAG前缀
     */
    public static final String LOG_TAG_PREFIX = "AskNow_";
    
    // Private constructor to prevent instantiation
    private AppConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}

