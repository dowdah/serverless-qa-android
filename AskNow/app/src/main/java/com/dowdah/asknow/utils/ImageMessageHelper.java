package com.dowdah.asknow.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.ApiConstants;
import com.dowdah.asknow.data.api.ApiService;
import com.dowdah.asknow.data.model.UploadResponse;
import com.dowdah.asknow.ui.chat.ChatViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 图片消息发送辅助类
 */
public class ImageMessageHelper {
    
    private final Activity activity;
    private final ApiService apiService;
    private final SharedPreferencesManager prefsManager;
    private final ChatViewModel chatViewModel;
    private final long questionId;
    
    /**
     * 检查Activity是否处于有效状态
     */
    private boolean isActivityValid() {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
    
    public ImageMessageHelper(
        Activity activity,
        ApiService apiService,
        SharedPreferencesManager prefsManager,
        ChatViewModel chatViewModel,
        long questionId
    ) {
        this.activity = activity;
        this.apiService = apiService;
        this.prefsManager = prefsManager;
        this.chatViewModel = chatViewModel;
        this.questionId = questionId;
    }
    
    /**
     * 打开图片选择器
     */
    public void openImagePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        launcher.launch(intent);
    }
    
    /**
     * 上传图片并发送消息
     */
    public void uploadAndSendImage(Uri imageUri) {
        // 检查Activity状态
        if (!isActivityValid()) {
            return;
        }
        
        File file = new File(activity.getCacheDir(), "temp_message_image.jpg");
        
        try (InputStream inputStream = activity.getContentResolver().openInputStream(imageUri);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            
            if (inputStream == null) {
                if (isActivityValid()) {
                    Toast.makeText(activity, R.string.error_reading_image, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            // 创建上传请求
            RequestBody requestBody = RequestBody.create(file, MediaType.parse("image/*"));
            MultipartBody.Part imagePart = MultipartBody.Part.createFormData(ApiConstants.FORM_FIELD_IMAGE, file.getName(), requestBody);
            
            String token = "Bearer " + prefsManager.getToken();
            apiService.uploadImage(token, imagePart).enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    // 清理临时文件
                    if (file.exists()) {
                        file.delete();
                    }
                    
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        String imagePath = response.body().getImagePath();
                        // 发送图片消息
                        chatViewModel.sendImageMessage(questionId, imagePath);
                    } else {
                        // 检查Activity是否有效后再显示Toast
                        if (isActivityValid()) {
                            String errorMsg = response.body() != null && response.body().getMessage() != null ?
                                response.body().getMessage() : activity.getString(R.string.failed_to_upload_image);
                            Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                }
                
                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    // 清理临时文件
                    if (file.exists()) {
                        file.delete();
                    }
                    
                    // 检查Activity是否有效后再显示Toast
                    if (isActivityValid()) {
                        String errorMsg = t.getMessage() != null ? 
                            activity.getString(R.string.upload_error, t.getMessage()) : 
                            activity.getString(R.string.failed_to_upload_image);
                        Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show();
                    }
                }
            });
            
        } catch (Exception e) {
            // 检查Activity是否有效后再显示Toast
            if (isActivityValid()) {
                String errorMsg = e.getMessage() != null ? 
                    activity.getString(R.string.error_message, e.getMessage()) : 
                    activity.getString(R.string.error_reading_image);
                Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show();
            }
            
            // 清理临时文件
            if (file.exists()) {
                file.delete();
            }
        }
    }
}


