package com.dowdah.asknow.ui.tutor;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dowdah.asknow.constants.QuestionStatus;
import com.dowdah.asknow.constants.WebSocketMessageType;
import com.dowdah.asknow.data.api.ApiService;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.data.model.WebSocketMessage;
import com.dowdah.asknow.data.repository.MessageRepository;
import com.dowdah.asknow.data.repository.QuestionRepository;
import com.dowdah.asknow.utils.SharedPreferencesManager;
import com.dowdah.asknow.utils.WebSocketManager;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class TutorViewModel extends ViewModel {
    private static final String TAG = "TutorViewModel";
    
    private final ApiService apiService;
    private final QuestionDao questionDao;
    private final SharedPreferencesManager prefsManager;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final WebSocketManager webSocketManager;
    private final ExecutorService executor;
    
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<WebSocketMessage> newQuestion = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>(false);
    
    // 分页状态
    private int currentPage = 1;
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMoreData = new MutableLiveData<>(true);
    
    // WebSocket message observer for cleanup
    private final androidx.lifecycle.Observer<WebSocketMessage> webSocketMessageObserver;
    
    @Inject
    public TutorViewModel(
        ApiService apiService,
        QuestionDao questionDao,
        SharedPreferencesManager prefsManager,
        MessageRepository messageRepository,
        QuestionRepository questionRepository,
        WebSocketManager webSocketManager
    ) {
        this.apiService = apiService;
        this.questionDao = questionDao;
        this.prefsManager = prefsManager;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.webSocketManager = webSocketManager;
        this.executor = Executors.newSingleThreadExecutor();
        
        // Create observer instance for proper cleanup
        this.webSocketMessageObserver = message -> {
            if (message != null) {
                String type = message.getType();
                if (WebSocketMessageType.NEW_QUESTION.equals(type)) {
                    newQuestion.postValue(message);
                }
            }
        };
        
        observeWebSocketMessages();
    }
    
    private void observeWebSocketMessages() {
        // Observe WebSocket messages for tutor-specific handling
        webSocketManager.getIncomingMessage().observeForever(webSocketMessageObserver);
    }
    
    public LiveData<java.util.List<QuestionEntity>> getPendingQuestions() {
        return questionDao.getQuestionsByStatus(QuestionStatus.PENDING);
    }
    
    public LiveData<java.util.List<QuestionEntity>> getInProgressQuestions() {
        // 只显示当前老师正在回答的问题
        long tutorId = prefsManager.getUserId();
        return questionDao.getQuestionsByTutorAndStatus(tutorId, QuestionStatus.IN_PROGRESS);
    }
    
    public LiveData<java.util.List<QuestionEntity>> getClosedQuestions() {
        // 只显示当前老师已完成的问题
        long tutorId = prefsManager.getUserId();
        return questionDao.getQuestionsByTutorAndStatus(tutorId, QuestionStatus.CLOSED);
    }
    
    public LiveData<Boolean> isConnected() {
        return webSocketManager.isConnected();
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<WebSocketMessage> getNewQuestion() {
        return newQuestion;
    }
    
    public LiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }
    
    public LiveData<Boolean> getIsLoadingMore() {
        return isLoadingMore;
    }
    
    public LiveData<Boolean> getHasMoreData() {
        return hasMoreData;
    }
    
    /**
     * 从服务器同步问题到本地（下拉刷新，重置为第一页）
     */
    public void syncQuestionsFromServer() {
        if (Boolean.TRUE.equals(isSyncing.getValue())) {
            Log.d(TAG, "Sync already in progress, skipping");
            return;
        }
        
        currentPage = 1;
        hasMoreData.postValue(true);
        isSyncing.postValue(true);
        String token = prefsManager.getToken();
        long userId = prefsManager.getUserId();
        
        questionRepository.syncQuestionsFromServer(
            token, 
            userId, 
            "tutor", 
            com.dowdah.asknow.constants.AppConstants.DEFAULT_START_PAGE,
            com.dowdah.asknow.constants.AppConstants.DEFAULT_QUESTIONS_PAGE_SIZE,
            false, 
            new QuestionRepository.SyncCallback() {
            @Override
            public void onSuccess(int syncedCount) {
                isSyncing.postValue(false);
                Log.d(TAG, "Sync completed: " + syncedCount + " questions");
            }
            
            @Override
            public void onError(String errorMessage) {
                isSyncing.postValue(false);
                TutorViewModel.this.errorMessage.postValue(errorMessage);
                Log.e(TAG, "Sync failed: " + errorMessage);
            }
            
            @Override
            public void onPageLoaded(boolean hasMore) {
                hasMoreData.postValue(hasMore);
            }
        });
    }
    
    /**
     * 加载更多问题（滚动加载）
     */
    public void loadMoreQuestions() {
        if (Boolean.TRUE.equals(isLoadingMore.getValue()) || 
            Boolean.FALSE.equals(hasMoreData.getValue())) {
            Log.d(TAG, "Already loading or no more data");
            return;
        }
        
        currentPage++;
        isLoadingMore.postValue(true);
        String token = prefsManager.getToken();
        long userId = prefsManager.getUserId();
        
        questionRepository.syncQuestionsFromServer(
            token, 
            userId, 
            "tutor", 
            currentPage,
            com.dowdah.asknow.constants.AppConstants.DEFAULT_QUESTIONS_PAGE_SIZE,
            true, 
            new QuestionRepository.SyncCallback() {
            @Override
            public void onSuccess(int syncedCount) {
                isLoadingMore.postValue(false);
                Log.d(TAG, "Load more completed: " + syncedCount + " questions");
            }
            
            @Override
            public void onError(String errorMessage) {
                isLoadingMore.postValue(false);
                currentPage--; // 恢复页码
                TutorViewModel.this.errorMessage.postValue(errorMessage);
                Log.e(TAG, "Load more failed: " + errorMessage);
            }
            
            @Override
            public void onPageLoaded(boolean hasMore) {
                hasMoreData.postValue(hasMore);
            }
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        
        // Remove WebSocket observer to prevent memory leak
        if (webSocketMessageObserver != null) {
            webSocketManager.getIncomingMessage().removeObserver(webSocketMessageObserver);
        }
        
        // Don't disconnect WebSocket - it should stay connected at app level
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

