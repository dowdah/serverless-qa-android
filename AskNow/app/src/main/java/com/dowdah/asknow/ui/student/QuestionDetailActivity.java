package com.dowdah.asknow.ui.student;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.dowdah.asknow.BuildConfig;
import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.enums.QuestionStatus;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.dao.QuestionDao;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.databinding.ActivityQuestionDetailBinding;
import com.dowdah.asknow.ui.adapter.ImageDisplayAdapter;
import com.dowdah.asknow.ui.adapter.MessageAdapter;
import com.dowdah.asknow.ui.chat.ChatViewModel;
import com.dowdah.asknow.ui.image.ImagePreviewActivity;
import com.dowdah.asknow.utils.SharedPreferencesManager;

import java.util.ArrayList;
import java.util.List;
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
    
    /**
     * 图片选择结果处理
     */
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    // 直接调用 ViewModel 上传图片（符合 MVVM 架构）
                    chatViewModel.uploadAndSendImage(imageUri, questionId);
                }
            }
        }
    );
    
    /**
     * 权限请求处理
     */
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
        new ActivityResultContracts.RequestPermission(),
        isGranted -> {
            if (isGranted) {
                openImagePicker();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    );
    
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
        
        // 设置图片消息点击监听器
        messageAdapter.setImageClickListener(imagePath -> {
            ArrayList<String> imagePaths = new ArrayList<>();
            imagePaths.add(imagePath);
            openImagePreview(imagePaths, 0);
        });
        
        // 设置消息重试监听器
        messageAdapter.setMessageRetryListener((messageId, content, messageType) -> {
            chatViewModel.retryMessage(messageId, content, messageType);
        });
    }
    
    private void loadQuestionDetails() {
        // 使用 LiveData 来观察问题状态的变化
        questionDao.getQuestionByIdLive(questionId).observe(this, question -> {
            if (question != null) {
                binding.tvContent.setText(question.getContent());
                binding.tvStatus.setText(getStatusText(question.getStatus()));
                
                // 显示多张图片 - 异步解析JSON
                if (question.getImagePaths() != null && !question.getImagePaths().isEmpty()) {
                    final String imagePathsJson = question.getImagePaths();
                    executor.execute(() -> {
                        try {
                            List<String> imagePaths = new com.google.gson.Gson().fromJson(
                                imagePathsJson, 
                                new com.google.gson.reflect.TypeToken<List<String>>(){}.getType()
                            );
                            
                            runOnUiThread(() -> {
                                // 防止Activity销毁后访问binding
                                if (binding == null || isFinishing() || isDestroyed()) {
                                    return;
                                }
                                
                                if (imagePaths != null && !imagePaths.isEmpty()) {
                                    ImageDisplayAdapter imageAdapter = new ImageDisplayAdapter();
                                    LinearLayoutManager layoutManager = new LinearLayoutManager(
                                        this, LinearLayoutManager.HORIZONTAL, false
                                    );
                                    binding.rvQuestionImages.setLayoutManager(layoutManager);
                                    
                                    // 优化RecyclerView配置
                                    binding.rvQuestionImages.setHasFixedSize(true);
                                    binding.rvQuestionImages.setItemViewCacheSize(10);
                                    
                                    binding.rvQuestionImages.setAdapter(imageAdapter);
                                    imageAdapter.setImages(imagePaths);
                                    binding.rvQuestionImages.setVisibility(View.VISIBLE);
                                    
                                    // 设置图片点击监听器
                                    imageAdapter.setClickListener((position, imagePath) -> {
                                        openImagePreview(new ArrayList<>(imagePaths), position);
                                    });
                                } else {
                                    binding.rvQuestionImages.setVisibility(View.GONE);
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                // 防止Activity销毁后访问binding
                                if (binding != null && !isFinishing() && !isDestroyed()) {
                                    binding.rvQuestionImages.setVisibility(View.GONE);
                                }
                            });
                        }
                    });
                } else {
                    binding.rvQuestionImages.setVisibility(View.GONE);
                }
                
                // 根据问题状态启用/禁用输入
                // 只有在 in_progress 状态下才允许发送消息和图片
                boolean isActive = !QuestionStatus.CLOSED.equals(question.getStatus()) && 
                                 !QuestionStatus.PENDING.equals(question.getStatus());
                binding.etMessage.setEnabled(isActive);
                binding.btnSend.setEnabled(isActive);
                binding.btnSelectImage.setEnabled(isActive);
                
                // 视觉反馈：禁用时降低透明度
                binding.btnSend.setAlpha(isActive ? 1.0f : 0.5f);
                binding.btnSelectImage.setAlpha(isActive ? 1.0f : 0.5f);
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
        
        chatViewModel.getUploadProgress().observe(this, progress -> {
            if (progress != null) {
                if (progress.isComplete()) {
                    // 图片上传完成，消息将自动发送
                } else if (progress.hasError()) {
                    // 错误已在 errorMessage 中处理
                }
            }
        });
    }
    
    private void setupInputArea() {
        binding.btnSend.setOnClickListener(v -> sendMessage());
        
        // 图片选择按钮点击事件
        binding.btnSelectImage.setOnClickListener(v -> checkPermissionAndPickImage());
    }
    
    /**
     * 检查权限并打开图片选择器
     */
    private void checkPermissionAndPickImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        }
    }
    
    /**
     * 打开图片选择器
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }
    
    /**
     * 打开图片预览
     */
    private void openImagePreview(ArrayList<String> imagePaths, int position) {
        Intent intent = new Intent(this, ImagePreviewActivity.class);
        intent.putStringArrayListExtra("image_paths", imagePaths);
        intent.putExtra("position", position);
        startActivity(intent);
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
        // LiveData 观察者会自动处理标记已读消息
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isActivityInForeground = false;
    }
    
    /**
     * 标记消息为已读（如果有未读消息）
     * 该方法由 LiveData 观察者在界面前台时自动调用
     */
    private void markMessagesAsReadIfNeeded() {
        if (executor != null && !executor.isShutdown()) {
            executor.execute(() -> {
                int unreadCount = messageDao.getUnreadMessageCount(questionId, currentUserId);
                if (unreadCount > 0) {
                    runOnUiThread(() -> {
                        // 防止Activity销毁后执行操作
                        if (!isFinishing() && !isDestroyed()) {
                            chatViewModel.markMessagesAsRead(questionId);
                        }
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
