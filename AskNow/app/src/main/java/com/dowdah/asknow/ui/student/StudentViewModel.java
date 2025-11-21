package com.dowdah.asknow.ui.student;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.AppConstants;
import com.dowdah.asknow.constants.enums.WebSocketMessageType;
import com.dowdah.asknow.data.api.ApiService;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.data.model.QuestionRequest;
import com.dowdah.asknow.data.model.QuestionResponse;
import com.dowdah.asknow.data.model.UploadProgress;
import com.dowdah.asknow.data.model.UploadResponse;
import com.dowdah.asknow.data.repository.QuestionRepository;
import com.dowdah.asknow.ui.question.BaseQuestionListViewModel;
import com.dowdah.asknow.utils.SharedPreferencesManager;
import com.dowdah.asknow.utils.WebSocketManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * StudentViewModel - 学生端问题列表ViewModel
 * 
 * 继承自 BaseQuestionListViewModel，复用了：
 * - 分页加载逻辑
 * - 下拉刷新逻辑
 * - WebSocket 消息监听
 * 
 * 学生端特有功能：
 * - 创建新问题
 * - 查看自己提出的问题
 */
@HiltViewModel
public class StudentViewModel extends BaseQuestionListViewModel {
    private static final String TAG = "StudentViewModel";
    
    private final ApiService apiService;
    
    // 上传进度和问题创建结果的 LiveData
    private final MutableLiveData<UploadProgress> uploadProgress = new MutableLiveData<>();
    private final MutableLiveData<Boolean> questionCreated = new MutableLiveData<>();
    
    @Inject
    public StudentViewModel(
        @NonNull Application application,
        ApiService apiService,
        QuestionDao questionDao,
        SharedPreferencesManager prefsManager,
        QuestionRepository questionRepository,
        WebSocketManager webSocketManager
    ) {
        super(
            application,
            questionDao,
            prefsManager,
            questionRepository,
            webSocketManager,
            AppConstants.ROLE_STUDENT
        );
        this.apiService = apiService;
    }
    
    public LiveData<UploadProgress> getUploadProgress() {
        return uploadProgress;
    }
    
    public LiveData<Boolean> getQuestionCreated() {
        return questionCreated;
    }
    
    @Override
    protected String getWebSocketMessageType() {
        // 学生端监听新回答消息（向后兼容）
        return WebSocketMessageType.NEW_ANSWER;
    }
    
    @Override
    public LiveData<List<QuestionEntity>> getQuestions() {
        long userId = prefsManager.getUserId();
        return questionDao.getQuestionsByUserId(userId);
    }
    
    /**
     * 创建新问题（学生端特有功能）
     * 
     * @param content 问题内容
     * @param imagePaths 图片路径列表
     */
    public void createQuestion(String content, List<String> imagePaths) {
        String token = "Bearer " + prefsManager.getToken();
        QuestionRequest request = new QuestionRequest(content, imagePaths);
        
        apiService.createQuestion(token, request).enqueue(new Callback<QuestionResponse>() {
            @Override
            public void onResponse(Call<QuestionResponse> call, Response<QuestionResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QuestionResponse.QuestionData data = response.body().getQuestion();
                    
                    // Save to local database
                    executeInBackground(() -> {
                        // 将图片路径列表转换为 JSON 字符串
                        String imagePathsJson = null;
                        if (data.getImagePaths() != null && !data.getImagePaths().isEmpty()) {
                            imagePathsJson = new com.google.gson.Gson().toJson(data.getImagePaths());
                        }
                        
                        QuestionEntity entity = new QuestionEntity(
                            data.getUserId(),
                            null, // tutorId
                            data.getContent(),
                            imagePathsJson,
                            data.getStatus(),
                            data.getCreatedAt(),
                            data.getCreatedAt() // updatedAt
                        );
                        entity.setId(data.getId());
                        questionDao.insert(entity);
                    });
                    
                    // 注意：不需要再通过 WebSocket 发送
                    // HTTP API 后端已经处理了 WebSocket 广播
                    
                    Log.d(TAG, "Question created successfully");
                    questionCreated.postValue(true);
                } else {
                    setError(getApplication().getString(R.string.failed_to_create_question));
                    questionCreated.postValue(false);
                }
            }
            
            @Override
            public void onFailure(Call<QuestionResponse> call, Throwable t) {
                Log.e(TAG, "Error creating question", t);
                setError(getApplication().getString(R.string.network_error, t.getMessage()));
                questionCreated.postValue(false);
            }
        });
    }
    
    /**
     * 创建问题并上传图片（新版本，符合 MVVM 架构）
     * 先上传所有图片，然后创建问题
     * 
     * @param content 问题内容
     * @param imageUris 图片 URI 列表
     */
    public void createQuestionWithImages(String content, List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) {
            // 没有图片，直接创建问题
            createQuestion(content, null);
            return;
        }
        
        // 上传图片
        uploadProgress.postValue(UploadProgress.inProgress(0, imageUris.size()));
        uploadImages(imageUris, new UploadCallback() {
            @Override
            public void onSuccess(List<String> imagePaths) {
                uploadProgress.postValue(UploadProgress.complete(imageUris.size()));
                // 所有图片上传成功，创建问题
                createQuestion(content, imagePaths);
            }
            
            @Override
            public void onProgress(int current, int total) {
                uploadProgress.postValue(UploadProgress.inProgress(current, total));
            }
            
            @Override
            public void onError(int current, int total, String error) {
                uploadProgress.postValue(UploadProgress.error(current, total, error));
                setError(error);
                questionCreated.postValue(false);
            }
        });
    }
    
    /**
     * 批量上传图片
     * 
     * @param imageUris 图片 URI 列表
     * @param callback 上传回调
     */
    private void uploadImages(List<Uri> imageUris, UploadCallback callback) {
        List<String> uploadedPaths = new ArrayList<>();
        uploadNextImage(imageUris, 0, uploadedPaths, callback);
    }
    
    /**
     * 递归上传下一张图片
     * 
     * @param imageUris 所有图片的 URI 列表
     * @param index 当前索引
     * @param uploadedPaths 已上传的图片路径列表
     * @param callback 上传回调
     */
    private void uploadNextImage(List<Uri> imageUris, int index, List<String> uploadedPaths, UploadCallback callback) {
        if (index >= imageUris.size()) {
            // 所有图片上传完成
            callback.onSuccess(uploadedPaths);
            return;
        }
        
        Uri imageUri = imageUris.get(index);
        
        // 在后台线程处理文件 I/O
        executeInBackground(() -> {
            File file = new File(getApplication().getCacheDir(), "temp_image_" + index + ".jpg");
            
            try (InputStream inputStream = getApplication().getContentResolver().openInputStream(imageUri);
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                
                if (inputStream == null) {
                    callback.onError(index, imageUris.size(), getApplication().getString(R.string.error_reading_image));
                    return;
                }
                
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                
                // 文件准备好了，在主线程发起网络请求
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    uploadImageFile(file, index, imageUris.size(), uploadedPaths, imageUris, callback);
                });
                
            } catch (Exception e) {
                // 清理临时文件
                if (file.exists()) {
                    file.delete();
                }
                
                String errorMsg = e.getMessage() != null ? 
                    getApplication().getString(R.string.error_message, e.getMessage()) : 
                    getApplication().getString(R.string.error_reading_image);
                callback.onError(index, imageUris.size(), errorMsg);
            }
        });
    }
    
    /**
     * 上传单个图片文件
     * 
     * @param file 要上传的文件
     * @param index 当前索引
     * @param total 总数
     * @param uploadedPaths 已上传的路径列表
     * @param imageUris 所有图片 URI
     * @param callback 回调
     */
    private void uploadImageFile(File file, int index, int total, List<String> uploadedPaths, List<Uri> imageUris, UploadCallback callback) {
        RequestBody requestBody = RequestBody.create(file, MediaType.parse("image/*"));
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData(AppConstants.FORM_FIELD_IMAGE, file.getName(), requestBody);
        
        String token = "Bearer " + prefsManager.getToken();
        apiService.uploadImage(token, imagePart).enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                // 清理临时文件
                if (file.exists()) {
                    file.delete();
                }
                
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    uploadedPaths.add(response.body().getImagePath());
                    callback.onProgress(index + 1, total);
                    // 上传下一张图片
                    uploadNextImage(imageUris, index + 1, uploadedPaths, callback);
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ?
                        response.body().getMessage() : getApplication().getString(R.string.failed_to_upload_image);
                    callback.onError(index, total, errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<UploadResponse> call, Throwable t) {
                // 清理临时文件
                if (file.exists()) {
                    file.delete();
                }
                
                String errorMsg = t.getMessage() != null ? 
                    getApplication().getString(R.string.upload_error, t.getMessage()) : 
                    getApplication().getString(R.string.failed_to_upload_image);
                callback.onError(index, total, errorMsg);
            }
        });
    }
    
    /**
     * 上传回调接口
     */
    private interface UploadCallback {
        void onSuccess(List<String> imagePaths);
        void onProgress(int current, int total);
        void onError(int current, int total, String error);
    }
}

