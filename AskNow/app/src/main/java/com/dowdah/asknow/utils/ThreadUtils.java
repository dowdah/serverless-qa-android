package com.dowdah.asknow.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 线程工具类，统一管理应用中的线程池
 */
public final class ThreadUtils {
    
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(CORE_POOL_SIZE);
    private static final ExecutorService SINGLE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper(), null);
    
    // Private constructor to prevent instantiation
    private ThreadUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    /**
     * 在IO线程池中执行任务（适用于网络请求、文件读写等）
     * 
     * @param runnable 要执行的任务
     */
    public static void executeOnIO(Runnable runnable) {
        if (runnable != null) {
            IO_EXECUTOR.execute(runnable);
        }
    }
    
    /**
     * 在单线程中执行任务（适用于需要顺序执行的任务）
     * 
     * @param runnable 要执行的任务
     */
    public static void executeOnSingle(Runnable runnable) {
        if (runnable != null) {
            SINGLE_EXECUTOR.execute(runnable);
        }
    }
    
    /**
     * 在主线程中执行任务
     * 
     * @param runnable 要执行的任务
     */
    public static void executeOnMain(Runnable runnable) {
        if (runnable != null) {
            if (isMainThread()) {
                runnable.run();
            } else {
                MAIN_HANDLER.post(runnable);
            }
        }
    }
    
    /**
     * 延迟在主线程中执行任务
     * 
     * @param runnable 要执行的任务
     * @param delayMillis 延迟时间（毫秒）
     */
    public static void executeOnMainDelayed(Runnable runnable, long delayMillis) {
        if (runnable != null) {
            MAIN_HANDLER.postDelayed(runnable, delayMillis);
        }
    }
    
    /**
     * 判断当前是否在主线程
     * 
     * @return true if current thread is main thread
     */
    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
    
    /**
     * 移除主线程中的待执行任务
     * 
     * @param runnable 要移除的任务
     */
    public static void removeCallbacks(Runnable runnable) {
        if (runnable != null) {
            MAIN_HANDLER.removeCallbacks(runnable);
        }
    }
    
    /**
     * 获取IO线程池（供特殊情况使用）
     * 
     * @return IO线程池
     */
    public static ExecutorService getIOExecutor() {
        return IO_EXECUTOR;
    }
    
    /**
     * 获取单线程线程池（供特殊情况使用）
     * 
     * @return 单线程线程池
     */
    public static ExecutorService getSingleExecutor() {
        return SINGLE_EXECUTOR;
    }
}

