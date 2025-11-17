package com.dowdah.asknow.ui.student;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.dowdah.asknow.BuildConfig;
import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.QuestionStatus;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.databinding.ActivityQuestionDetailBinding;
import com.dowdah.asknow.ui.adapter.MessageAdapter;
import com.dowdah.asknow.ui.chat.ChatViewModel;
import com.dowdah.asknow.utils.SharedPreferencesManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class QuestionDetailActivity extends AppCompatActivity {
    
    private ActivityQuestionDetailBinding binding;
    private ChatViewModel chatViewModel;
    private MessageAdapter messageAdapter;
    private long questionId;
    private long currentUserId;
    private ExecutorService executor;
    private boolean isActivityInForeground = false;
    private boolean shouldScrollToBottom = false;
    
    @Inject
    QuestionDao questionDao;
    
    @Inject
    MessageDao messageDao;
    
    @Inject
    SharedPreferencesManager prefsManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuestionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        questionId = getIntent().getLongExtra("question_id", -1);
        if (questionId == -1) {
            Toast.makeText(this, R.string.invalid_question, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentUserId = prefsManager.getUserId();
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        executor = Executors.newSingleThreadExecutor();
        
        setupToolbar();
        setupRecyclerView();
        loadQuestionDetails();
        observeMessages();
        observeViewModel();
        setupInputArea();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.question_detail);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.recyclerViewMessages.setLayoutManager(layoutManager);
        binding.recyclerViewMessages.setAdapter(messageAdapter);
    }
    
    private void loadQuestionDetails() {
        // 使用 LiveData 来观察问题状态的变化
        questionDao.getQuestionByIdLive(questionId).observe(this, question -> {
            if (question != null) {
                binding.tvContent.setText(question.getContent());
                binding.tvStatus.setText(getStatusText(question.getStatus()));
                
                if (question.getImagePath() != null && !question.getImagePath().isEmpty()) {
                    String imageUrl = BuildConfig.BASE_URL.replaceAll("/$", "") + question.getImagePath();
                    Glide.with(this)
                        .load(imageUrl)
                        .into(binding.ivQuestion);
                    binding.ivQuestion.setVisibility(View.VISIBLE);
                } else {
                    binding.ivQuestion.setVisibility(View.GONE);
                }
                
                // 根据问题状态启用/禁用输入
                boolean isActive = !QuestionStatus.CLOSED.equals(question.getStatus()) && 
                                 !QuestionStatus.PENDING.equals(question.getStatus());
                binding.etMessage.setEnabled(isActive);
                binding.btnSend.setEnabled(isActive);
            }
        });
    }
    
    private String getStatusText(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case QuestionStatus.PENDING:
                return getString(R.string.status_pending);
            case QuestionStatus.IN_PROGRESS:
                return getString(R.string.status_in_progress);
            case QuestionStatus.CLOSED:
                return getString(R.string.status_closed);
            default:
                return status;
        }
    }
    
    /**
     * 检查用户是否在列表底部
     * 用于智能滚动判断
     */
    private boolean isUserAtBottom() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerViewMessages.getLayoutManager();
        if (layoutManager != null) {
            int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
            int totalItems = messageAdapter.getItemCount();
            // 最后一项可见，或倒数第2项可见（容错）
            return lastVisiblePosition >= totalItems - 2;
        }
        return true; // 默认返回 true，确保首次加载时滚动到底部
    }
    
    private void observeMessages() {
        messageDao.getMessagesByQuestionId(questionId).observe(this, messages -> {
            if (messages != null) {
                int previousSize = messageAdapter.getItemCount();
                messageAdapter.setMessages(messages);
                
                // 智能滚动：仅在必要时滚动到底部
                if (messages.size() > 0) {
                    boolean shouldScroll = shouldScrollToBottom || 
                                          (messages.size() > previousSize && isUserAtBottom());
                    if (shouldScroll) {
                        binding.recyclerViewMessages.smoothScrollToPosition(messages.size() - 1);
                        shouldScrollToBottom = false; // 重置标志
                    }
                }
                
                // 如果界面在前台，自动标记未读消息为已读
                if (isActivityInForeground) {
                    markMessagesAsReadIfNeeded();
                }
            }
        });
    }
    
    private void observeViewModel() {
        chatViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
        
        chatViewModel.getMessageSent().observe(this, sent -> {
            if (sent != null && sent) {
                binding.etMessage.setText("");
            }
        });
    }
    
    private void setupInputArea() {
        binding.btnSend.setOnClickListener(v -> sendMessage());
    }
    
    private void sendMessage() {
        String content = binding.etMessage.getText().toString().trim();
        
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.please_enter_message, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 发送消息时设置标志，确保滚动到底部
        shouldScrollToBottom = true;
        chatViewModel.sendMessage(questionId, content);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isActivityInForeground = true;
        // 检查是否有未读消息，只在有未读消息时才标记
        checkAndMarkMessagesAsRead();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isActivityInForeground = false;
    }
    
    /**
     * 检查并标记消息为已读
     * 只有在有未读消息时才调用 API，避免不必要的网络请求
     */
    private void checkAndMarkMessagesAsRead() {
        markMessagesAsReadIfNeeded();
    }
    
    /**
     * 标记消息为已读（如果有未读消息）
     * 该方法可以在消息列表更新或界面恢复时调用
     */
    private void markMessagesAsReadIfNeeded() {
        if (executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                int unreadCount = messageDao.getUnreadMessageCount(questionId, currentUserId);
                if (unreadCount > 0) {
                    runOnUiThread(() -> {
                        chatViewModel.markMessagesAsRead(questionId);
                    });
                }
            });
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding = null;
    }
}
