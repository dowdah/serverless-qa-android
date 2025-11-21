package com.dowdah.asknow.data.model;

/**
 * 图片上传进度数据类
 * 用于在上传过程中向 UI 层传递进度信息
 */
public class UploadProgress {
    private final int current;
    private final int total;
    private final boolean isComplete;
    private final String error;
    
    /**
     * 创建上传进度对象
     * 
     * @param current 当前已完成的数量
     * @param total 总数量
     * @param isComplete 是否完成
     * @param error 错误信息（如果有）
     */
    public UploadProgress(int current, int total, boolean isComplete, String error) {
        this.current = current;
        this.total = total;
        this.isComplete = isComplete;
        this.error = error;
    }
    
    /**
     * 创建进行中的进度对象
     */
    public static UploadProgress inProgress(int current, int total) {
        return new UploadProgress(current, total, false, null);
    }
    
    /**
     * 创建完成的进度对象
     */
    public static UploadProgress complete(int total) {
        return new UploadProgress(total, total, true, null);
    }
    
    /**
     * 创建失败的进度对象
     */
    public static UploadProgress error(int current, int total, String error) {
        return new UploadProgress(current, total, false, error);
    }
    
    public int getCurrent() {
        return current;
    }
    
    public int getTotal() {
        return total;
    }
    
    public boolean isComplete() {
        return isComplete;
    }
    
    public boolean hasError() {
        return error != null;
    }
    
    public String getError() {
        return error;
    }
    
    /**
     * 获取进度百分比
     */
    public int getPercentage() {
        if (total == 0) {
            return 0;
        }
        return (int) ((current * 100.0) / total);
    }
}

