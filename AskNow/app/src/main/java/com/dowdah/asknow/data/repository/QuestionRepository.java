package com.dowdah.asknow.data.repository;

import android.util.Log;

import com.dowdah.asknow.data.api.ApiService;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.MessageEntity;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.data.model.MessagesListResponse;
import com.dowdah.asknow.data.model.QuestionsListResponse;
import com.dowdah.asknow.utils.RetryHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 问题同步Repository
 * 负责从服务器同步问题数据到本地数据库
 */
@Singleton
public class QuestionRepository {
    private static final String TAG = "QuestionRepository";
    
    private final ApiService apiService;
    private final QuestionDao questionDao;
    private final MessageDao messageDao;
    private final ExecutorService executor;
    
    @Inject
    public QuestionRepository(ApiService apiService, QuestionDao questionDao, MessageDao messageDao) {
        this.apiService = apiService;
        this.questionDao = questionDao;
        this.messageDao = messageDao;
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 同步问题从服务器到本地
     * 对于学生：同步自己提出的问题
     * 对于老师：同步自己接取或已完结的问题
     * 
     * @param token 认证token
     * @param userId 当前用户ID
     * @param role 用户角色 ("student" 或 "tutor")
     * @param callback 同步回调
     */
    public void syncQuestionsFromServer(String token, long userId, String role, SyncCallback callback) {
        syncQuestionsFromServer(
            token, 
            userId, 
            role, 
            com.dowdah.asknow.constants.AppConstants.DEFAULT_START_PAGE,
            com.dowdah.asknow.constants.AppConstants.DEFAULT_QUESTIONS_PAGE_SIZE,
            false, 
            callback
        );
    }
    
    /**
     * 同步问题从服务器到本地（支持分页）
     * 
     * @param token 认证token
     * @param userId 当前用户ID
     * @param role 用户角色
     * @param page 页码
     * @param pageSize 每页大小
     * @param isAppendMode 是否为追加模式（true=追加，false=刷新）
     * @param callback 同步回调
     */
    public void syncQuestionsFromServer(String token, long userId, String role, int page, int pageSize, boolean isAppendMode, SyncCallback callback) {
        Log.d(TAG, "Starting sync for user " + userId + " with role " + role + " page=" + page);
        
        String authHeader = "Bearer " + token;
        apiService.getQuestions(authHeader, null, page, pageSize).enqueue(new Callback<QuestionsListResponse>() {
            @Override
            public void onResponse(Call<QuestionsListResponse> call, Response<QuestionsListResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<QuestionsListResponse.QuestionData> serverQuestions = response.body().getQuestions();
                    com.dowdah.asknow.data.model.Pagination pagination = response.body().getPagination();
                    boolean hasMore = pagination != null && pagination.hasMore();
                    
                    executor.execute(() -> {
                        try {
                            // 获取服务器返回的问题ID集合
                            Set<Long> serverQuestionIds = new HashSet<>();
                            for (QuestionsListResponse.QuestionData serverQuestion : serverQuestions) {
                                serverQuestionIds.add(serverQuestion.getId());
                                
                                // 插入或更新本地数据库
                                QuestionEntity entity = new QuestionEntity();
                                entity.setId(serverQuestion.getId());
                                entity.setUserId(serverQuestion.getUserId());
                                entity.setTutorId(serverQuestion.getTutorId());
                                entity.setContent(serverQuestion.getContent());
                                entity.setImagePath(serverQuestion.getImagePath());
                                entity.setStatus(serverQuestion.getStatus());
                                entity.setCreatedAt(serverQuestion.getCreatedAt());
                                entity.setUpdatedAt(serverQuestion.getUpdatedAt());
                                
                                questionDao.insert(entity);
                            }
                            
                            // 只在非追加模式（刷新模式）且是第一页时清理本地不存在于服务器的数据
                            if (!isAppendMode && page == 1) {
                                // 删除本地存在但服务器不存在的问题
                                List<QuestionEntity> localQuestions;
                                if ("student".equals(role)) {
                                    // 学生端：获取本地所有该学生创建的问题
                                    localQuestions = questionDao.getQuestionsByUserIdSync(userId);
                                } else {
                                    // 老师端：获取本地所有该老师接取的问题
                                    localQuestions = questionDao.getQuestionsByTutorId(userId);
                                }
                                
                                if (localQuestions != null) {
                                    for (QuestionEntity localQuestion : localQuestions) {
                                        if (!serverQuestionIds.contains(localQuestion.getId())) {
                                            // 服务器不存在该问题，从本地删除
                                            questionDao.deleteQuestion(localQuestion.getId());
                                            Log.d(TAG, "Deleted question " + localQuestion.getId() + " (not on server)");
                                        }
                                    }
                                }
                            }
                            
                            // 同步每个问题的消息
                            if (!serverQuestions.isEmpty()) {
                                Log.d(TAG, "Starting messages sync for " + serverQuestions.size() + " questions");
                                syncMessagesForQuestions(token, serverQuestions, hasMore, callback);
                            } else {
                                Log.d(TAG, "Sync completed successfully. Synced " + serverQuestions.size() + " questions");
                                if (callback != null) {
                                    callback.onSuccess(serverQuestions.size());
                                    callback.onPageLoaded(hasMore);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error during sync", e);
                            if (callback != null) {
                                callback.onError("同步数据时发生错误: " + e.getMessage());
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "Sync failed: " + response.code());
                    if (callback != null) {
                        callback.onError("同步失败: 服务器返回错误");
                    }
                }
            }
            
            @Override
            public void onFailure(Call<QuestionsListResponse> call, Throwable t) {
                Log.e(TAG, "Sync network error", t);
                if (callback != null) {
                    String errorMessage = getDetailedErrorMessage(t);
                    callback.onError(errorMessage);
                }
            }
        });
    }
    
    /**
     * 单个问题消息同步完成的回调接口
     */
    private interface OnSingleQuestionMessagesSyncedCallback {
        void onSuccess(int messageCount, int pageCount);
        void onError(String errorMessage);
    }
    
    /**
     * 递归获取单个问题的所有分页消息
     * 
     * @param authHeader 认证头
     * @param questionId 问题ID
     * @param allMessages 累积所有消息的列表
     * @param currentPage 当前页码
     * @param callback 同步完成回调
     */
    private void syncAllMessagesForQuestion(
        String authHeader,
        long questionId,
        List<MessagesListResponse.MessageData> allMessages,
        int currentPage,
        OnSingleQuestionMessagesSyncedCallback callback
    ) {
        apiService.getMessages(
            authHeader,
            questionId,
            currentPage,
            com.dowdah.asknow.constants.AppConstants.DEFAULT_MESSAGES_PAGE_SIZE
        ).enqueue(new Callback<MessagesListResponse>() {
            @Override
            public void onResponse(Call<MessagesListResponse> call, Response<MessagesListResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<MessagesListResponse.MessageData> pageMessages = response.body().getMessages();
                    com.dowdah.asknow.data.model.Pagination pagination = response.body().getPagination();
                    
                    // 将当前页消息添加到累积列表
                    if (pageMessages != null && !pageMessages.isEmpty()) {
                        allMessages.addAll(pageMessages);
                    }
                    
                    // 检查是否还有更多页
                    if (pagination != null && pagination.hasMore()) {
                        Log.d(TAG, "Question " + questionId + " page " + currentPage + " fetched, has more pages");
                        // 递归获取下一页
                        syncAllMessagesForQuestion(authHeader, questionId, allMessages, currentPage + 1, callback);
                    } else {
                        // 所有页都已获取完成
                        int totalPages = pagination != null ? pagination.getTotalPages() : currentPage;
                        Log.d(TAG, "Question " + questionId + " all pages fetched: " + allMessages.size() + 
                              " messages across " + totalPages + " pages");
                        
                        if (callback != null) {
                            callback.onSuccess(allMessages.size(), totalPages);
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to fetch messages for question " + questionId + " page " + currentPage + 
                          ": " + response.code());
                    if (callback != null) {
                        callback.onError("API response failed: " + response.code());
                    }
                }
            }
            
            @Override
            public void onFailure(Call<MessagesListResponse> call, Throwable t) {
                Log.e(TAG, "Network error fetching messages for question " + questionId + " page " + currentPage, t);
                if (callback != null) {
                    callback.onError("Network error: " + t.getMessage());
                }
            }
        });
    }
    
    /**
     * 同步每个问题的消息（使用完整分页同步）
     */
    private void syncMessagesForQuestions(String token, List<QuestionsListResponse.QuestionData> questions, boolean hasMore, SyncCallback callback) {
        if (questions.isEmpty()) {
            if (callback != null) {
                callback.onSuccess(0);
                callback.onPageLoaded(hasMore);
            }
            return;
        }
        
        String authHeader = "Bearer " + token;
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(questions.size());
        
        Log.d(TAG, "Starting messages sync for " + questions.size() + " questions");
        
        for (QuestionsListResponse.QuestionData question : questions) {
            long questionId = question.getId();
            
            // 使用新的递归方法获取所有分页消息
            List<MessagesListResponse.MessageData> allMessages = new ArrayList<>();
            syncAllMessagesForQuestion(
                authHeader,
                questionId,
                allMessages,
                com.dowdah.asknow.constants.AppConstants.DEFAULT_START_PAGE,
                new OnSingleQuestionMessagesSyncedCallback() {
                    @Override
                    public void onSuccess(int messageCount, int pageCount) {
                        // 所有分页消息都已获取完成，现在执行数据库操作
                        executor.execute(() -> {
                            try {
                                // 获取服务器消息ID集合
                                Set<Long> serverMessageIds = new HashSet<>();
                                for (MessagesListResponse.MessageData serverMessage : allMessages) {
                                    serverMessageIds.add(serverMessage.getId());
                                    
                                    // 插入或更新消息到本地数据库
                                    MessageEntity entity = new MessageEntity();
                                    entity.setId(serverMessage.getId());
                                    entity.setQuestionId(serverMessage.getQuestionId());
                                    entity.setSenderId(serverMessage.getSenderId());
                                    entity.setContent(serverMessage.getContent());
                                    entity.setMessageType(serverMessage.getMessageType());
                                    entity.setCreatedAt(serverMessage.getCreatedAt());
                                    entity.setRead(serverMessage.isRead()); // 保留已读状态
                                    entity.setSendStatus("sent"); // 从服务器同步的消息都是已发送状态
                                    
                                    messageDao.insert(entity);
                                }
                                
                                // 删除本地存在但服务器不存在的消息
                                List<MessageEntity> localMessages = messageDao.getMessagesByQuestionIdSync(questionId);
                                if (localMessages != null) {
                                    for (MessageEntity localMessage : localMessages) {
                                        if (!serverMessageIds.contains(localMessage.getId())) {
                                            messageDao.deleteMessage(localMessage.getId());
                                            Log.d(TAG, "Deleted message " + localMessage.getId() + " (not on server)");
                                        }
                                    }
                                }
                                
                                Log.d(TAG, "Question " + questionId + " sync completed: " + messageCount + 
                                      " messages from " + pageCount + " pages saved to database");
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving messages to database for question " + questionId, e);
                            } finally {
                                // 数据库操作完成后，检查是否所有问题都已完成
                                int completed = completedCount.incrementAndGet();
                                if (completed >= totalCount.get()) {
                                    Log.d(TAG, "All questions' messages synced successfully");
                                    if (callback != null) {
                                        callback.onSuccess(questions.size());
                                        callback.onPageLoaded(hasMore);
                                    }
                                }
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "Failed to sync messages for question " + questionId + ": " + errorMessage);
                        
                        // 即使某个问题的消息同步失败，继续处理其他问题
                        int completed = completedCount.incrementAndGet();
                        if (completed >= totalCount.get()) {
                            Log.d(TAG, "All questions processed (some may have failed)");
                            if (callback != null) {
                                callback.onSuccess(questions.size());
                                callback.onPageLoaded(hasMore);
                            }
                        }
                    }
                }
            );
        }
    }
    
    /**
     * 同步回调接口
     */
    public interface SyncCallback {
        void onSuccess(int syncedCount);
        void onError(String errorMessage);
        void onPageLoaded(boolean hasMore);
    }
    
    /**
     * 获取详细的错误信息
     * 
     * @param error 异常对象
     * @return 用户友好的错误信息
     */
    private String getDetailedErrorMessage(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        
        // 网络超时
        if (error instanceof java.net.SocketTimeoutException) {
            return "网络连接超时，请检查网络后重试";
        }
        
        // 无网络连接
        if (error instanceof java.net.UnknownHostException) {
            return "无法连接到服务器，请检查网络设置";
        }
        
        // 连接被拒绝
        if (error instanceof java.net.ConnectException) {
            return "服务器拒绝连接，请稍后重试";
        }
        
        // 通用IO错误
        if (error instanceof java.io.IOException) {
            return "网络错误: " + error.getMessage();
        }
        
        // 其他错误
        String message = error.getMessage();
        return message != null && !message.isEmpty() ? 
            "同步失败: " + message : "同步失败，请重试";
    }
    
    /**
     * Clean up resources to prevent memory leaks
     * Should be called when the repository is no longer needed
     */
    public void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            Log.d(TAG, "Executor shutdown");
        }
    }
}

