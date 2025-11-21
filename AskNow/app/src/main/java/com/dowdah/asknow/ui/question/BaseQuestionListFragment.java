package com.dowdah.asknow.ui.question;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.dowdah.asknow.R;
import com.dowdah.asknow.constants.AppConstants;
import com.dowdah.asknow.data.local.dao.MessageDao;
import com.dowdah.asknow.data.local.entity.QuestionEntity;
import com.dowdah.asknow.ui.adapter.QuestionAdapter;
import com.dowdah.asknow.utils.SharedPreferencesManager;
import com.dowdah.asknow.utils.WebSocketManager;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * BaseQuestionListFragment - 问题列表 Fragment 的基类
 * 
 * 提供问题列表的公共逻辑，被以下 Fragment 继承：
 * - QuestionListFragment (学生端)
 * - QuestionListByStatusFragment (教师端)
 * 
 * 公共功能：
 * - RecyclerView 设置和优化
 * - 下拉刷新
 * - 滚动加载更多
 * - 未读消息数量更新
 * - 空数据提示
 * 
 * 子类需要实现：
 * - getViewModel(): 返回具体的 ViewModel
 * - getDetailActivityClass(): 返回详情页 Activity
 */
public abstract class BaseQuestionListFragment extends Fragment {
    
    protected SwipeRefreshLayout swipeRefreshLayout;
    protected RecyclerView recyclerView;
    protected View tvNoData;
    
    protected QuestionAdapter adapter;
    protected LinearLayoutManager layoutManager;
    
    @Inject
    protected MessageDao messageDao;
    
    @Inject
    protected SharedPreferencesManager prefsManager;
    
    @Inject
    protected WebSocketManager webSocketManager;
    
    @Inject
    @Named("io")
    protected ExecutorService ioExecutor;
    
    /**
     * 获取 ViewModel（子类实现）
     * 
     * @return BaseQuestionListViewModel 实例
     */
    protected abstract BaseQuestionListViewModel getViewModel();
    
    /**
     * 获取详情页 Activity Class（子类实现）
     * 
     * @return 详情页 Activity 的 Class
     */
    protected abstract Class<? extends AppCompatActivity> getDetailActivityClass();
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResId(), container, false);
        initViews(view);
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        observeViewModel();
    }
    
    /**
     * 获取布局资源 ID（子类可重写以使用不同布局）
     * 
     * @return 布局资源 ID
     */
    protected int getLayoutResId() {
        return R.layout.fragment_question_list;
    }
    
    /**
     * 初始化视图
     * 
     * @param view 根视图
     */
    protected void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);
        tvNoData = view.findViewById(R.id.tvNoData);
    }
    
    /**
     * 设置 RecyclerView
     */
    protected void setupRecyclerView() {
        long currentUserId = prefsManager.getUserId();
        
        // 创建适配器
        adapter = new QuestionAdapter(question -> {
            Intent intent = new Intent(requireContext(), getDetailActivityClass());
            intent.putExtra("question_id", question.getId());
            startActivity(intent);
        }, messageDao, currentUserId, ioExecutor);
        
        // 设置 LayoutManager
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        
        // 性能优化：RecyclerView大小固定，避免重新计算
        recyclerView.setHasFixedSize(true);
        
        // 性能优化：减少item变化动画，提高流畅度
        if (recyclerView.getItemAnimator() != null) {
            recyclerView.getItemAnimator().setChangeDuration(0);
        }
        
        // 设置重试监听
        adapter.setRetryListener(() -> {
            getViewModel().loadMoreQuestions();
        });
        
        // 设置下拉刷新
        swipeRefreshLayout.setOnRefreshListener(() -> {
            getViewModel().syncQuestionsFromServer();
        });
        
        // 设置滚动监听，加载更多
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // 向下滚动且接近底部时触发加载更多
                if (dy > 0) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - AppConstants.LOAD_MORE_THRESHOLD
                        && firstVisibleItemPosition >= 0) {
                        getViewModel().loadMoreQuestions();
                    }
                }
            }
        });
    }
    
    /**
     * 观察 ViewModel 数据变化
     */
    protected void observeViewModel() {
        BaseQuestionListViewModel viewModel = getViewModel();
        
        // 观察问题列表
        viewModel.getQuestions().observe(getViewLifecycleOwner(), questions -> {
            if (questions != null && !questions.isEmpty()) {
                adapter.setQuestions(questions);
                recyclerView.setVisibility(View.VISIBLE);
                tvNoData.setVisibility(View.GONE);
            } else {
                recyclerView.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
            }
        });
        
        // 监听同步状态
        viewModel.getIsSyncing().observe(getViewLifecycleOwner(), isSyncing -> {
            swipeRefreshLayout.setRefreshing(isSyncing != null && isSyncing);
            
            // 同步完成后，刷新未读数量
            if (isSyncing != null && !isSyncing) {
                adapter.refreshUnreadCounts();
            }
        });
        
        // 监听加载更多状态
        viewModel.getIsLoadingMore().observe(getViewLifecycleOwner(), isLoadingMore -> {
            if (isLoadingMore != null && isLoadingMore) {
                adapter.showLoadingFooter();
            } else {
                adapter.hideLoadingFooter();
            }
        });
        
        // 监听是否还有更多数据
        viewModel.getHasMoreData().observe(getViewLifecycleOwner(), hasMoreData -> {
            if (hasMoreData != null && !hasMoreData) {
                adapter.hideLoadingFooter();
            }
        });
        
        // 监听新消息到达，刷新未读数量
        webSocketManager.getNewMessageReceived().observe(getViewLifecycleOwner(), questionId -> {
            if (questionId != null) {
                // 收到新消息，刷新适配器以更新未读数量
                adapter.refreshUnreadCounts();
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清理引用，防止内存泄漏
        swipeRefreshLayout = null;
        recyclerView = null;
        tvNoData = null;
        adapter = null;
        layoutManager = null;
    }
}

