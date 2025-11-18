package com.dowdah.asknow.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.MessageStatus;
import com.dowdah.asknow.constants.MessageType;
import com.dowdah.asknow.data.local.entity.MessageEntity;
import com.dowdah.asknow.databinding.ItemMessageReceivedBinding;
import com.dowdah.asknow.databinding.ItemMessageSentBinding;
import com.dowdah.asknow.utils.ImageBindingHelper;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final int TYPE_LOADING = 3;
    
    private List<MessageEntity> messages = new ArrayList<>();
    private long currentUserId;
    private boolean showLoadingFooter = false;
    private boolean showRetryFooter = false;
    private BaseLoadingFooterViewHolder.OnRetryClickListener retryListener;
    private OnImageClickListener imageClickListener;
    
    /**
     * 图片点击监听器接口
     */
    public interface OnImageClickListener {
        void onImageClick(@NonNull String imagePath);
    }
    
    /**
     * 消息重试监听器接口
     */
    public interface OnMessageRetryListener {
        /**
         * 当用户点击重试按钮时调用
         * 
         * @param messageId 消息ID
         * @param content 消息内容
         * @param messageType 消息类型
         */
        void onRetryMessage(long messageId, @NonNull String content, @NonNull String messageType);
    }
    
    private OnMessageRetryListener messageRetryListener;
    
    public MessageAdapter(long currentUserId) {
        this.currentUserId = currentUserId;
    }
    
    /**
     * 设置图片点击监听器
     * 
     * @param listener 图片点击监听器
     */
    public void setImageClickListener(@Nullable OnImageClickListener listener) {
        this.imageClickListener = listener;
    }
    
    /**
     * 设置消息重试监听器
     * 
     * @param listener 消息重试监听器
     */
    public void setMessageRetryListener(@Nullable OnMessageRetryListener listener) {
        this.messageRetryListener = listener;
    }
    
    /**
     * 设置重试点击监听器
     * 
     * @param retryListener 重试点击监听器
     */
    public void setRetryListener(@Nullable BaseLoadingFooterViewHolder.OnRetryClickListener retryListener) {
        this.retryListener = retryListener;
    }
    
    /**
     * 设置消息列表（使用DiffUtil优化）
     * 
     * @param newMessages 新的消息列表
     */
    public void setMessages(@Nullable List<MessageEntity> newMessages) {
        if (newMessages == null) {
            newMessages = new ArrayList<>();
        }
        
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new MessageDiffCallback(this.messages, newMessages));
        this.messages = new ArrayList<>(newMessages);
        diffResult.dispatchUpdatesTo(this);
    }
    
    /**
     * DiffUtil回调，用于高效的列表更新
     */
    private static class MessageDiffCallback extends DiffUtil.Callback {
        private final List<MessageEntity> oldList;
        private final List<MessageEntity> newList;
        
        public MessageDiffCallback(List<MessageEntity> oldList, List<MessageEntity> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override
        public int getOldListSize() {
            return oldList != null ? oldList.size() : 0;
        }
        
        @Override
        public int getNewListSize() {
            return newList != null ? newList.size() : 0;
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            MessageEntity oldMessage = oldList.get(oldItemPosition);
            MessageEntity newMessage = newList.get(newItemPosition);
            return oldMessage.getId() == newMessage.getId();
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            MessageEntity oldMessage = oldList.get(oldItemPosition);
            MessageEntity newMessage = newList.get(newItemPosition);
            
            // 比较所有相关字段
            return oldMessage.getId() == newMessage.getId() &&
                   oldMessage.getQuestionId() == newMessage.getQuestionId() &&
                   oldMessage.getSenderId() == newMessage.getSenderId() &&
                   oldMessage.getContent().equals(newMessage.getContent()) &&
                   oldMessage.getMessageType().equals(newMessage.getMessageType()) &&
                   oldMessage.isRead() == newMessage.isRead() &&
                   (oldMessage.getSendStatus() == null ? newMessage.getSendStatus() == null : 
                    oldMessage.getSendStatus().equals(newMessage.getSendStatus()));
        }
    }
    
    public void showLoadingFooter() {
        if (!showLoadingFooter) {
            showLoadingFooter = true;
            showRetryFooter = false;
            notifyItemInserted(messages.size());
        }
    }
    
    public void hideLoadingFooter() {
        if (showLoadingFooter) {
            showLoadingFooter = false;
            notifyItemRemoved(messages.size());
        }
    }
    
    public void showRetryFooter() {
        if (!showRetryFooter) {
            hideLoadingFooter();
            showRetryFooter = true;
            notifyItemInserted(messages.size());
        }
    }
    
    public void hideRetryFooter() {
        if (showRetryFooter) {
            showRetryFooter = false;
            notifyItemRemoved(messages.size());
        }
    }
    
    @Override
    public int getItemViewType(int position) {
        if (position == messages.size() && (showLoadingFooter || showRetryFooter)) {
            return TYPE_LOADING;
        }
        MessageEntity message = messages.get(position);
        if (message.getSenderId() == currentUserId) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            ItemMessageSentBinding binding = ItemMessageSentBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
            );
            return new SentMessageViewHolder(binding);
        } else if (viewType == TYPE_RECEIVED) {
            ItemMessageReceivedBinding binding = ItemMessageReceivedBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ReceivedMessageViewHolder(binding);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_loading_footer, parent, false);
            return new BaseLoadingFooterViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof BaseLoadingFooterViewHolder) {
            ((BaseLoadingFooterViewHolder) holder).bind(showRetryFooter, retryListener);
        } else {
            MessageEntity message = messages.get(position);
            if (holder instanceof SentMessageViewHolder) {
                ((SentMessageViewHolder) holder).bind(message, imageClickListener, messageRetryListener);
            } else if (holder instanceof ReceivedMessageViewHolder) {
                ((ReceivedMessageViewHolder) holder).bind(message, imageClickListener);
            }
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size() + (showLoadingFooter || showRetryFooter ? 1 : 0);
    }
    
    /**
     * 绑定消息内容到视图（图片或文本）
     * 
     * @param message 消息实体
     * @param imageView 图片视图
     * @param textView 文本视图
     * @param imageClickListener 图片点击监听器
     */
    private static void bindMessageContent(
        @NonNull MessageEntity message,
        @NonNull ImageView imageView,
        @NonNull TextView textView,
        @Nullable OnImageClickListener imageClickListener
    ) {
        String messageType = message.getMessageType();
        String content = message.getContent();
        
        if (MessageType.IMAGE.equals(messageType)) {
            // 显示图片消息
            textView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            
            ImageBindingHelper.loadMessageImage(imageView.getContext(), content, imageView);
            
            // 添加图片点击事件
            imageView.setOnClickListener(v -> {
                if (imageClickListener != null) {
                    imageClickListener.onImageClick(content);
                }
            });
        } else {
            // 显示文本消息
            imageView.setVisibility(View.GONE);
            textView.setVisibility(View.VISIBLE);
            textView.setText(content != null ? content : "");
        }
    }
    
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageSentBinding binding;
        
        SentMessageViewHolder(ItemMessageSentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        /**
         * 绑定消息数据到视图
         * 
         * @param message 消息实体
         * @param imageClickListener 图片点击监听器
         * @param retryListener 重试监听器
         */
        void bind(@Nullable MessageEntity message, 
                  @Nullable OnImageClickListener imageClickListener,
                  @Nullable OnMessageRetryListener retryListener) {
            if (message == null) {
                return;
            }
            
            // 绑定消息内容
            bindMessageContent(message, binding.ivMessageImage, binding.tvMessage, imageClickListener);
            
            // 处理发送状态
            String status = message.getSendStatus();
            Context context = binding.getRoot().getContext();
            
            if (MessageStatus.PENDING.equals(status)) {
                // 显示加载指示器
                binding.pbSending.setVisibility(View.VISIBLE);
                binding.btnRetry.setVisibility(View.GONE);
                // 正常边框
                binding.cardMessage.setStrokeWidth(0);
            } else if (MessageStatus.FAILED.equals(status)) {
                // 显示重试按钮
                binding.pbSending.setVisibility(View.GONE);
                binding.btnRetry.setVisibility(View.VISIBLE);
                
                // 设置重试按钮点击事件
                binding.btnRetry.setOnClickListener(v -> {
                    if (retryListener != null) {
                        retryListener.onRetryMessage(
                            message.getId(),
                            message.getContent(),
                            message.getMessageType()
                        );
                    }
                });
                
                // 设置红色边框
                int strokeWidth = dpToPx(context, 2);
                // 使用 TypedValue 获取主题的 colorError 属性
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(
                    android.R.attr.colorError, 
                    typedValue, 
                    true
                );
                int errorColor = typedValue.data;
                binding.cardMessage.setStrokeWidth(strokeWidth);
                binding.cardMessage.setStrokeColor(errorColor);
            } else {
                // SENT 状态：隐藏所有指示器
                binding.pbSending.setVisibility(View.GONE);
                binding.btnRetry.setVisibility(View.GONE);
                // 正常边框
                binding.cardMessage.setStrokeWidth(0);
            }
        }
        
        /**
         * 将dp转换为px
         * 
         * @param context 上下文
         * @param dp dp值
         * @return px值
         */
        private static int dpToPx(@NonNull Context context, int dp) {
            Resources resources = context.getResources();
            return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                resources.getDisplayMetrics()
            );
        }
    }
    
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemMessageReceivedBinding binding;
        
        ReceivedMessageViewHolder(ItemMessageReceivedBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        /**
         * 绑定消息数据到视图
         * 
         * @param message 消息实体
         * @param imageClickListener 图片点击监听器
         */
        void bind(@Nullable MessageEntity message, @Nullable OnImageClickListener imageClickListener) {
            if (message == null) {
                return;
            }
            
            bindMessageContent(message, binding.ivMessageImage, binding.tvMessage, imageClickListener);
            
            // 显示未读标记
            View unreadIndicator = binding.getRoot().findViewById(R.id.unread_indicator);
            if (unreadIndicator != null) {
                if (message.isRead()) {
                    unreadIndicator.setVisibility(View.GONE);
                } else {
                    unreadIndicator.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}

