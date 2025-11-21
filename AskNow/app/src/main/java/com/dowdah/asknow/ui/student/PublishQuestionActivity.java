package com.dowdah.asknow.ui.student;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.AppConstants;
import com.dowdah.asknow.databinding.ActivityPublishQuestionBinding;
import com.dowdah.asknow.ui.adapter.ImageSelectionAdapter;
import com.dowdah.asknow.utils.ValidationUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PublishQuestionActivity extends AppCompatActivity {
    
    private ActivityPublishQuestionBinding binding;
    private StudentViewModel viewModel;
    
    private static final int MAX_IMAGES = 9;
    private List<Uri> selectedImageUris = new ArrayList<>();
    private ImageSelectionAdapter imagePreviewAdapter;
    
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                
                // 处理多图片选择
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count && selectedImageUris.size() < MAX_IMAGES; i++) {
                        Uri imageUri = data.getClipData().getItemAt(i).getUri();
                        selectedImageUris.add(imageUri);
                    }
                } else if (data.getData() != null) {
                    // 单张图片
                    if (selectedImageUris.size() < MAX_IMAGES) {
                        selectedImageUris.add(data.getData());
                    }
                }
                
                updateImagePreview();
                
                if (selectedImageUris.size() >= MAX_IMAGES) {
                    Toast.makeText(this, getString(R.string.max_images_reached, MAX_IMAGES), 
                        Toast.LENGTH_SHORT).show();
                }
            }
        }
    );
    
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
        binding = ActivityPublishQuestionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(StudentViewModel.class);
        
        setupToolbar();
        setupViews();
        observeViewModel();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.ask_question);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupViews() {
        // 设置图片预览适配器
        imagePreviewAdapter = new ImageSelectionAdapter();
        binding.rvImagePreview.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvImagePreview.setAdapter(imagePreviewAdapter);
        
        imagePreviewAdapter.setRemoveListener(position -> {
            // 移除选中的图片
            selectedImageUris.remove(position);
            updateImagePreview();
        });
        
        binding.btnAddImage.setOnClickListener(v -> {
            if (selectedImageUris.size() >= MAX_IMAGES) {
                Toast.makeText(this, getString(R.string.max_images_reached, MAX_IMAGES), 
                    Toast.LENGTH_SHORT).show();
            } else {
                checkPermissionAndPickImage();
            }
        });
        
        binding.btnSubmit.setOnClickListener(v -> submitQuestion());
    }
    
    private void observeViewModel() {
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                resetSubmitState();
            }
        });
        
        viewModel.getUploadProgress().observe(this, progress -> {
            if (progress != null) {
                if (progress.hasError()) {
                    resetSubmitState();
                } else if (progress.isComplete()) {
                    // 上传完成，等待问题创建结果
                }
            }
        });
        
        viewModel.getQuestionCreated().observe(this, created -> {
            if (created != null && created) {
                Toast.makeText(this, R.string.question_submitted, Toast.LENGTH_SHORT).show();
                finish();
            } else if (created != null) {
                // 创建失败
                resetSubmitState();
            }
        });
    }
    
    private void checkPermissionAndPickImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        }
    }
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // 支持多选
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(intent);
    }
    
    private void updateImagePreview() {
        if (selectedImageUris.isEmpty()) {
            binding.rvImagePreview.setVisibility(android.view.View.GONE);
        } else {
            binding.rvImagePreview.setVisibility(android.view.View.VISIBLE);
            imagePreviewAdapter.setImages(selectedImageUris);
        }
    }
    
    private void submitQuestion() {
        String content = binding.etContent.getText().toString().trim();
        
        // 清除之前的错误
        binding.tilContent.setError(null);
        
        // 验证内容
        if (!ValidationUtils.isNotNullOrEmpty(content)) {
            binding.tilContent.setError(getString(R.string.please_enter_question_content));
            binding.etContent.requestFocus();
            return;
        }
        
        // 验证内容长度（至少10个字符，最多1000个字符）
        if (content.length() < 10) {
            binding.tilContent.setError(getString(R.string.error_question_too_short));
            binding.etContent.requestFocus();
            return;
        }
        
        if (content.length() > 1000) {
            binding.tilContent.setError(getString(R.string.error_question_too_long));
            binding.etContent.requestFocus();
            return;
        }
        
        // 禁用提交按钮，显示进度条
        binding.btnSubmit.setEnabled(false);
        binding.progressBar.setVisibility(android.view.View.VISIBLE);
        
        // 调用 ViewModel 处理上传和创建问题（符合 MVVM 架构）
        viewModel.createQuestionWithImages(content, selectedImageUris);
    }
    
    /**
     * 重置提交状态（启用按钮，隐藏进度条）
     */
    private void resetSubmitState() {
        if (binding != null) {
            binding.btnSubmit.setEnabled(true);
            binding.progressBar.setVisibility(android.view.View.GONE);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

