package com.dowdah.asknow.ui.chat;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.dowdah.asknow.R;
import com.dowdah.asknow.base.BaseViewModel;
import com.dowdah.asknow.constants.AppConstants;
import com.dowdah.asknow.constants.enums.MessageStatus;
import com.dowdah.asknow.constants.enums.MessageType;
import com.dowdah.asknow.constants.enums.QuestionStatus;
import com.dowdah.asknow.data.api.ApiService;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.MessageEntity;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.data.model.MessageRequest;
import com.dowdah.asknow.data.model.MessageResponse;
import com.dowdah.asknow.data.model.UploadProgress;
import com.dowdah.asknow.data.model.UploadResponse;
import com.dowdah.asknow.data.repository.MessageRepository;
import com.dowdah.asknow.utils.SharedPreferencesManager;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ChatViewModel - 聊天界面的ViewModel
 * 负责处理消息的发送、接收和问题状态的管理
 * 
 * 主要功能：
 * - 乐观更新：发送消息时立即显示，提升用户体验
 * - 消息状态管理：pending（发送中）、sent（已发送）、failed（失败）
 * - 问题状态管理：接受问题、关闭问题
 * - 已读未读管理：标记消息为已读
 * 
 * 优化改进：
 * - 继承 BaseViewModel，复用线程池和错误处理
 * - 提取统一的消息发送逻辑（sendMessageInternal）
 * - 提取统一的乐观更新模式（updateQuestionStatusWithOptimisticUpdate）
 */
@HiltViewModel
public class ChatViewModel extends BaseViewModel {
    private static final String TAG = "ChatViewModel";
    
    private final ApiService apiService;
    private final QuestionDao questionDao;
    private final MessageDao messageDao;
    private final MessageRepository messageRepository;
    private final SharedPreferencesManager prefsManager;
    
    // 使用AtomicLong生成唯一的临时消息ID
    private final AtomicLong tempIdGenerator = new AtomicLong(-System.currentTimeMillis());
    
    // 锁对象，用于保护临时消息的删除和插入操作
    private final Object messageLock = new Object();
    
    // 锁对象，用于保护问题状态操作
    private final Object questionLock = new Object();
    
    // 防抖：记录正在进行的操作
    private volatile boolean isAcceptingQuestion = false;
    private volatile boolean isClosingQuestion = false;
    private volatile boolean isSendingMessage = false;
    private volatile boolean isUploadingImage = false;
    
    private final MutableLiveData<Boolean> messageSent = new MutableLiveData<>();
    private final MutableLiveData<UploadProgress> uploadProgress = new MutableLiveData<>();
    
    @Inject
    public ChatViewModel(
        @NonNull Application application,
        ApiService apiService,
        QuestionDao questionDao,
        MessageDao messageDao,
        MessageRepository messageRepository,
        SharedPreferencesManager prefsManager
    ) {
        super(application);
        this.apiService = apiService;
        this.questionDao = questionDao;
        this.messageDao = messageDao;
        this.messageRepository = messageRepository;
        this.prefsManager = prefsManager;
    }
    
    public LiveData<List<MessageEntity>> getMessagesByQuestionId(long questionId) {
        return messageDao.getMessagesByQuestionId(questionId);
    }
    
    /**
     * 获取未读消息数量
     */
    public LiveData<Integer> getUnreadMessageCount(long questionId) {
        long currentUserId = prefsManager.getUserId();
        return messageRepository.getUnreadMessageCount(questionId, currentUserId);
    }
    
    /**
     * 标记消息为已读
     */
    public void markMessagesAsRead(long questionId) {
        String token = prefsManager.getToken();
        long currentUserId = prefsManager.getUserId();
        
        messageRepository.markMessagesAsRead(token, questionId, currentUserId, new MessageRepository.MarkReadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Messages marked as read successfully");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Error marking messages as read: " + error);
            }
        });
    }
    
    /**
     * 发送文本消息
     * 
     * @param questionId 问题ID
     * @param content 消息内容
     */
    public void sendMessage(long questionId, String content) {
        sendMessageInternal(questionId, content, MessageType.TEXT);
    }
    
    /**
     * 发送图片消息
     * 
     * @param questionId 问题ID
     * @param imagePath 图片路径（服务器路径，已上传）
     */
    public void sendImageMessage(long questionId, String imagePath) {
        sendMessageInternal(questionId, imagePath, MessageType.IMAGE);
    }
    
    /**
     * 统一的消息发送逻辑（内部方法）
     * 实现乐观更新模式：先插入临时消息，发送成功后替换为真实消息
     * 
     * @param questionId 问题ID
     * @param content 消息内容
     * @param messageType 消息类型（text/image）
     */
    private void sendMessageInternal(long questionId, String content, String messageType) {
        // 防抖：避免重复发送
        if (isSendingMessage) {
            Log.w(TAG, "Message is already being sent, ignoring duplicate request");
            return;
        }
        isSendingMessage = true;
        
        // 使用AtomicLong生成唯一的临时ID（负数避免与真实ID冲突）
        final long tempId = tempIdGenerator.decrementAndGet();
        long currentUserId = prefsManager.getUserId();
        long currentTime = System.currentTimeMillis();
        
        // 1. 乐观更新：立即插入本地数据库，状态为 pending
        executeInBackground(() -> {
            synchronized (messageLock) {
                MessageEntity tempEntity = new MessageEntity(
                    questionId,
                    currentUserId,
                    content,
                    messageType,
                    currentTime
                );
                tempEntity.setId(tempId);
                tempEntity.setSendStatus(MessageStatus.PENDING);
                tempEntity.setRead(true); // 自己发送的消息标记为已读
                messageDao.insert(tempEntity);
                Log.d(TAG, "Optimistic update: inserted temp " + messageType + " message with id=" + tempId);
            }
        });
        
        // 2. 发送 HTTP API
        String token = "Bearer " + prefsManager.getToken();
        MessageRequest request = new MessageRequest(questionId, content, messageType);
        
        apiService.sendMessage(token, request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    MessageResponse.MessageData data = response.body().getData();
                    
                    // 3. 替换临时消息为真实消息（使用锁保护，防止竞态条件）
                    executeInBackground(() -> {
                        synchronized (messageLock) {
                            // 删除临时消息
                            messageDao.deleteById(tempId);
                            
                            // 插入真实消息
                            MessageEntity realEntity = new MessageEntity(
                                data.getQuestionId(),
                                data.getSenderId(),
                                data.getContent(),
                                data.getMessageType(),
                                data.getCreatedAt()
                            );
                            realEntity.setId(data.getId());
                            realEntity.setSendStatus(MessageStatus.SENT);
                            realEntity.setRead(true); // 自己发送的消息标记为已读
                            messageDao.insert(realEntity);
                            
                            Log.d(TAG, messageType + " message sent successfully: replaced temp id=" + tempId + " with real id=" + data.getId());
                        }
                        
                        // 更新问题的 updatedAt
                        questionDao.updateUpdatedAt(questionId, data.getCreatedAt());
                    });
                    
                    // 注意：不需要再通过 WebSocket 发送消息
                    // HTTP API 后端已经处理了消息的保存和 WebSocket 推送
                    
                    isSendingMessage = false;
                    messageSent.postValue(true);
                } else {
                    // 4. 标记失败（使用锁保护）
                    handleMessageSendFailure(tempId, "Server error: " + response.code(), true);
                }
            }
            
            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                // 5. 标记失败（使用锁保护）
                handleMessageSendFailure(tempId, "Network error: " + t.getMessage(), true);
            }
        });
    }
    
    /**
     * 处理消息发送失败
     * 
     * @param tempId 临时消息ID
     * @param errorMsg 错误信息
     * @param clearInput 是否清空输入框
     */
    private void handleMessageSendFailure(long tempId, String errorMsg, boolean clearInput) {
        executeInBackground(() -> {
            synchronized (messageLock) {
                messageDao.updateSendStatus(tempId, MessageStatus.FAILED);
                Log.e(TAG, "Message send failed: marked temp id=" + tempId + " as failed. " + errorMsg);
            }
        });
        isSendingMessage = false;
        
        // 只有新消息发送失败时才清空文本框，重试失败不清空
        if (clearInput) {
            messageSent.postValue(true);
        }
        
        setError(getApplication().getString(R.string.failed_to_send_message));
    }
    
    /**
     * 重试发送失败的消息
     * 
     * @param failedMessageId 失败消息的ID
     * @param content 消息内容
     * @param messageType 消息类型
     */
    public void retryMessage(long failedMessageId, @NonNull String content, @NonNull String messageType) {
        Log.d(TAG, "Retrying message id=" + failedMessageId);
        
        // 1. 首先获取消息所属的 questionId
        executeInBackground(() -> {
            MessageEntity message = messageDao.getMessageById(failedMessageId);
            if (message == null) {
                Log.e(TAG, "Cannot retry: message not found with id=" + failedMessageId);
                return;
            }
            
            long questionId = message.getQuestionId();
            
            // 2. 更新消息状态为 PENDING
            synchronized (messageLock) {
                messageDao.updateSendStatus(failedMessageId, MessageStatus.PENDING);
                Log.d(TAG, "Retrying message: updated status to PENDING");
            }
            
            // 3. 在主线程重新发送 HTTP 请求
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                String token = "Bearer " + prefsManager.getToken();
                MessageRequest request = new MessageRequest(questionId, content, messageType);
                
                apiService.sendMessage(token, request).enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            // 成功：删除旧消息，插入新消息
                            MessageResponse.MessageData data = response.body().getData();
                            executeInBackground(() -> {
                                synchronized (messageLock) {
                                    messageDao.deleteById(failedMessageId);
                                    
                                    MessageEntity realEntity = new MessageEntity(
                                        data.getQuestionId(),
                                        data.getSenderId(),
                                        data.getContent(),
                                        data.getMessageType(),
                                        data.getCreatedAt()
                                    );
                                    realEntity.setId(data.getId());
                                    realEntity.setSendStatus(MessageStatus.SENT);
                                    realEntity.setRead(true);
                                    messageDao.insert(realEntity);
                                    
                                    Log.d(TAG, "Message retry successful: replaced id=" + failedMessageId + " with real id=" + data.getId());
                                }
                                
                                // 更新问题的 updatedAt
                                questionDao.updateUpdatedAt(questionId, data.getCreatedAt());
                            });
                            
                            // 注意：重试成功不需要清空文本框，因为重试的是列表中已有的消息
                            Log.d(TAG, "Message retry completed successfully");
                        } else {
                            // 失败：保持 FAILED 状态（不清空文本框）
                            handleMessageSendFailure(failedMessageId, "Server error: " + response.code(), false);
                        }
                    }
                    
                    @Override
                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                        // 失败：保持 FAILED 状态（不清空文本框）
                        handleMessageSendFailure(failedMessageId, "Network error: " + t.getMessage(), false);
                    }
                });
            });
        });
    }
    
    /**
     * 接受问题（教师端功能）
     * 
     * @param questionId 问题ID
     */
    public void acceptQuestion(long questionId) {
        updateQuestionStatusWithOptimisticUpdate(
            questionId,
            QuestionStatus.IN_PROGRESS,
            () -> isAcceptingQuestion,
            (accepting) -> isAcceptingQuestion = accepting,
            (token, request) -> apiService.acceptQuestion(token, request),
            R.string.failed_to_accept_question,
            true // 需要设置 tutorId
        );
    }
    
    /**
     * 关闭问题（学生端功能）
     * 
     * @param questionId 问题ID
     */
    public void closeQuestion(long questionId) {
        updateQuestionStatusWithOptimisticUpdate(
            questionId,
            QuestionStatus.CLOSED,
            () -> isClosingQuestion,
            (closing) -> isClosingQuestion = closing,
            (token, request) -> apiService.closeQuestion(token, request),
            R.string.failed_to_close_question,
            false // 不需要设置 tutorId
        );
    }
    
    /**
     * 统一的问题状态乐观更新方法
     * 
     * @param questionId 问题ID
     * @param newStatus 新状态
     * @param isProcessingCheck 检查是否正在处理的函数
     * @param setProcessing 设置处理状态的函数
     * @param apiCall API调用函数
     * @param errorMessageResId 错误消息资源ID
     * @param updateTutorId 是否需要更新tutorId
     */
    private void updateQuestionStatusWithOptimisticUpdate(
        long questionId,
        String newStatus,
        BooleanSupplier isProcessingCheck,
        BooleanConsumer setProcessing,
        ApiCallFunction apiCall,
        int errorMessageResId,
        boolean updateTutorId
    ) {
        // 防抖：避免重复操作
        if (isProcessingCheck.getAsBoolean()) {
            Log.w(TAG, "Question status update already in progress, ignoring duplicate request");
            return;
        }
        setProcessing.accept(true);
        
        final long tutorId = updateTutorId ? prefsManager.getUserId() : 0;
        // 保存原始状态用于回滚
        final Long[] originalTutorId = new Long[1];
        final String[] originalStatus = new String[1];
        
        // 1. 乐观更新：立即更新本地数据库（使用锁保护）
        executeInBackground(() -> {
            synchronized (questionLock) {
                QuestionEntity question = questionDao.getQuestionById(questionId);
                if (question != null) {
                    originalTutorId[0] = question.getTutorId();
                    originalStatus[0] = question.getStatus();
                    question.setStatus(newStatus);
                    if (updateTutorId) {
                        question.setTutorId(tutorId);
                    }
                    question.setUpdatedAt(System.currentTimeMillis());
                    questionDao.update(question);
                    Log.d(TAG, "Optimistic update: changed question " + questionId + " status to " + newStatus);
                }
            }
        });
        
        // 2. 发送API请求
        String token = "Bearer " + prefsManager.getToken();
        JsonObject request = new JsonObject();
        request.addProperty("questionId", questionId);
        
        apiCall.call(token, request).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    // 注意：不需要再通过 WebSocket 发送通知
                    // HTTP API 后端已经处理了 WebSocket 广播
                    setProcessing.accept(false);
                    Log.d(TAG, "Question status updated successfully to " + newStatus);
                } else {
                    // 3. API失败，回滚本地更新
                    rollbackQuestionUpdate(questionId, originalStatus[0], originalTutorId[0], "API error: " + response.code());
                    setProcessing.accept(false);
                    setError(getApplication().getString(errorMessageResId));
                }
            }
            
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // 4. 网络失败，回滚本地更新
                rollbackQuestionUpdate(questionId, originalStatus[0], originalTutorId[0], "Network error: " + t.getMessage());
                setProcessing.accept(false);
                setError(getApplication().getString(errorMessageResId));
            }
        });
    }
    
    /**
     * 回滚问题状态更新
     * 
     * @param questionId 问题ID
     * @param originalStatus 原始状态
     * @param originalTutorId 原始教师ID
     * @param errorMsg 错误信息
     */
    private void rollbackQuestionUpdate(long questionId, String originalStatus, Long originalTutorId, String errorMsg) {
        executeInBackground(() -> {
            synchronized (questionLock) {
                QuestionEntity question = questionDao.getQuestionById(questionId);
                if (question != null && originalStatus != null) {
                    question.setStatus(originalStatus);
                    question.setTutorId(originalTutorId);
                    questionDao.update(question);
                    Log.e(TAG, "Rolled back question " + questionId + " update due to: " + errorMsg);
                }
            }
        });
    }
    
    public LiveData<Boolean> getMessageSent() {
        return messageSent;
    }
    
    public LiveData<UploadProgress> getUploadProgress() {
        return uploadProgress;
    }
    
    /**
     * 上传图片并发送图片消息（符合 MVVM 架构）
     * 
     * @param imageUri 图片 URI
     * @param questionId 问题 ID
     */
    public void uploadAndSendImage(Uri imageUri, long questionId) {
        // 防抖：避免重复上传
        if (isUploadingImage) {
            Log.w(TAG, "Image upload already in progress, ignoring duplicate request");
            return;
        }
        isUploadingImage = true;
        
        // 通知开始上传
        uploadProgress.postValue(UploadProgress.inProgress(0, 1));
        
        // 在后台线程处理文件 I/O
        executeInBackground(() -> {
            File file = new File(getApplication().getCacheDir(), "temp_message_image_" + System.currentTimeMillis() + ".jpg");
            
            try (InputStream inputStream = getApplication().getContentResolver().openInputStream(imageUri);
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                
                if (inputStream == null) {
                    handleUploadError(getApplication().getString(R.string.error_reading_image));
                    return;
                }
                
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                
                // 文件准备好了，在主线程发起网络请求
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    uploadImageAndSend(file, questionId);
                });
                
            } catch (Exception e) {
                // 清理临时文件
                if (file.exists()) {
                    file.delete();
                }
                
                String errorMsg = e.getMessage() != null ? 
                    getApplication().getString(R.string.error_message, e.getMessage()) : 
                    getApplication().getString(R.string.error_reading_image);
                handleUploadError(errorMsg);
            }
        });
    }
    
    /**
     * 上传图片文件并发送消息
     * 
     * @param file 要上传的文件
     * @param questionId 问题 ID
     */
    private void uploadImageAndSend(File file, long questionId) {
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
                    String imagePath = response.body().getImagePath();
                    // 上传成功，发送图片消息
                    uploadProgress.postValue(UploadProgress.complete(1));
                    isUploadingImage = false;
                    sendImageMessage(questionId, imagePath);
                } else {
                    String errorMsg = response.body() != null && response.body().getMessage() != null ?
                        response.body().getMessage() : getApplication().getString(R.string.failed_to_upload_image);
                    handleUploadError(errorMsg);
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
                handleUploadError(errorMsg);
            }
        });
    }
    
    /**
     * 处理上传错误
     * 
     * @param errorMsg 错误信息
     */
    private void handleUploadError(String errorMsg) {
        uploadProgress.postValue(UploadProgress.error(0, 1, errorMsg));
        isUploadingImage = false;
        setError(errorMsg);
    }
    
    /**
     * 函数式接口：布尔值提供者
     */
    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
    
    /**
     * 函数式接口：布尔值消费者
     */
    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }
    
    /**
     * 函数式接口：API调用函数
     */
    @FunctionalInterface
    private interface ApiCallFunction {
        Call<JsonObject> call(String token, JsonObject request);
    }
}

