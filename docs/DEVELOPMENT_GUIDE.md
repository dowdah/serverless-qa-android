# AskNow 开发指南

本文档提供 AskNow 项目的完整开发指南，包括环境配置、代码规范、开发流程和最佳实践。

## 目录

- [开发环境配置](#开发环境配置)
- [Android 开发指南](#android-开发指南)
- [后端开发指南](#后端开发指南)
- [代码规范](#代码规范)
- [测试指南](#测试指南)
- [调试技巧](#调试技巧)
- [常见问题](#常见问题)

---

## 开发环境配置

### Android 开发环境

#### 必需软件

| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| Android Studio | Hedgehog (2023.1.1)+ | https://developer.android.com/studio |
| JDK | 17+ | 内置于 Android Studio |
| Android SDK | 33+ (编译 SDK: 36) | 通过 Android Studio 安装 |
| Gradle | 8.13+ | 自动管理 |

#### 配置步骤

1. **安装 Android Studio**
   ```bash
   # macOS
   下载 DMG 并拖入 Applications 文件夹
   
   # Windows
   下载 exe 安装程序并运行
   ```

2. **安装 SDK 和工具**
   
   打开 Android Studio → Settings → Appearance & Behavior → System Settings → Android SDK
   
   **SDK Platforms** 标签：
   - ✅ Android 14.0 (API 34)
   - ✅ Android 13.0 (API 33)
   
   **SDK Tools** 标签：
   - ✅ Android SDK Build-Tools 34
   - ✅ Android SDK Platform-Tools
   - ✅ Android SDK Tools
   - ✅ Android Emulator

3. **配置 Gradle**
   
   项目使用 Gradle 8.13，无需手动配置，Gradle Wrapper 会自动下载。
   
   **加速 Gradle 构建（可选）**：
   
   编辑 `~/.gradle/gradle.properties`：
   ```properties
   org.gradle.daemon=true
   org.gradle.parallel=true
   org.gradle.caching=true
   org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
   ```

4. **导入项目**
   ```bash
   cd /path/to/serverless-qa-android
   
   # 在 Android Studio 中打开
   Open -> 选择 AskNow 文件夹 -> OK
   
   # 等待 Gradle 同步完成
   ```

5. **配置模拟器或真机**
   
   **使用模拟器**：
   - Tools → Device Manager → Create Device
   - 选择 Pixel 6 或类似设备
   - 系统镜像选择 API 33 或 34
   - 启动模拟器
   
   **使用真机**：
   - 开启开发者选项和 USB 调试
   - 连接手机到电脑
   - 授权 USB 调试
   - 确保手机和电脑在同一局域网

### Python 后端环境

#### 必需软件

| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| Python | 3.8+ | https://www.python.org/downloads/ |
| pip | 最新版 | 内置于 Python |
| pipenv | 最新版 | `pip install pipenv` |

#### 配置步骤

1. **安装 Python**
   ```bash
   # macOS (使用 Homebrew)
   brew install python@3.11
   
   # Windows
   下载安装程序并勾选 "Add Python to PATH"
   
   # 验证安装
   python --version  # 应显示 3.8 或更高
   pip --version
   ```

2. **安装 pipenv**
   ```bash
   pip install pipenv
   
   # 验证安装
   pipenv --version
   ```

3. **安装依赖**
   ```bash
   cd backend
   
   # 使用 pipenv 安装
   pipenv install
   
   # 或使用 pip 安装
   pip install -r requirements.txt
   ```

4. **验证环境**
   ```bash
   pipenv run python --version
   pipenv run python -c "import fastapi; print(fastapi.__version__)"
   ```

---

## Android 开发指南

### 项目结构详解

```
AskNow/app/src/main/java/com/dowdah/asknow/
├── base/                       # 基础类
│   ├── BaseActivity.java      # Activity 基类
│   ├── BaseFragment.java      # Fragment 基类
│   └── BaseViewModel.java     # ViewModel 基类（线程池、错误处理）
│
├── constants/                  # 常量定义
│   ├── AppConstants.java      # 应用常量（角色、URL 等）
│   ├── MessageStatus.java     # 消息状态（pending, sent, failed）
│   ├── MessageType.java       # 消息类型（text, image）
│   ├── QuestionStatus.java    # 问题状态（pending, in_progress, closed）
│   └── WebSocketMessageType.java  # WebSocket 消息类型
│
├── data/                       # 数据层
│   ├── api/                    # 网络 API
│   │   ├── ApiService.java    # Retrofit 接口定义
│   │   └── WebSocketClient.java  # WebSocket 客户端
│   │
│   ├── local/                  # 本地数据库
│   │   ├── AppDatabase.java   # Room 数据库
│   │   ├── dao/                # 数据访问对象
│   │   │   ├── UserDao.java
│   │   │   ├── QuestionDao.java
│   │   │   └── MessageDao.java
│   │   └── entity/             # 数据库实体
│   │       ├── UserEntity.java
│   │       ├── QuestionEntity.java
│   │       └── MessageEntity.java
│   │
│   ├── model/                  # 数据模型
│   │   ├── *Request.java      # 请求模型
│   │   ├── *Response.java     # 响应模型
│   │   └── WebSocketMessage.java  # WebSocket 消息模型
│   │
│   └── repository/             # 数据仓库
│       ├── QuestionRepository.java  # 问题数据仓库
│       └── MessageRepository.java   # 消息数据仓库
│
├── di/                         # 依赖注入（Hilt 模块）
│   ├── DatabaseModule.java    # 数据库依赖
│   ├── NetworkModule.java     # 网络依赖
│   └── ExecutorModule.java    # 线程池依赖
│
├── ui/                         # UI 层
│   ├── adapter/                # 列表适配器
│   │   ├── QuestionAdapter.java
│   │   ├── MessageAdapter.java
│   │   └── ImageAdapter.java
│   │
│   ├── auth/                   # 认证界面
│   │   ├── LoginActivity.java
│   │   ├── RegisterActivity.java
│   │   └── AuthViewModel.java
│   │
│   ├── chat/                   # 聊天 ViewModel
│   │   └── ChatViewModel.java
│   │
│   ├── image/                  # 图片预览
│   │   └── ImagePreviewActivity.java
│   │
│   ├── question/               # 问题列表基类
│   │   ├── BaseQuestionListViewModel.java
│   │   └── BaseQuestionListFragment.java
│   │
│   ├── student/                # 学生端界面
│   │   ├── StudentMainActivity.java
│   │   ├── PublishQuestionActivity.java
│   │   ├── QuestionDetailActivity.java
│   │   └── StudentViewModel.java
│   │
│   └── tutor/                  # 教师端界面
│       ├── TutorMainActivity.java
│       ├── AnswerActivity.java
│       └── TutorViewModel.java
│
├── utils/                      # 工具类
│   ├── DateUtils.java          # 日期格式化
│   ├── ErrorHandler.java       # 错误处理
│   ├── ImageMessageHelper.java # 图片消息处理
│   ├── RetryHelper.java        # 重试逻辑
│   ├── SharedPreferencesManager.java  # 本地存储
│   └── WebSocketManager.java   # WebSocket 管理
│
└── AskNowApplication.java      # Application 类
```

### 添加新功能的完整流程

以"添加点赞功能"为例：

#### 1. 定义数据模型

**创建请求模型**：`data/model/LikeRequest.java`
```java
public class LikeRequest {
    private long questionId;
    
    public LikeRequest(long questionId) {
        this.questionId = questionId;
    }
    
    // Getters and setters
}
```

**创建响应模型**：`data/model/LikeResponse.java`
```java
public class LikeResponse {
    private boolean success;
    private String message;
    private LikeData data;
    
    public static class LikeData {
        private long questionId;
        private int likeCount;
        
        // Getters and setters
    }
    
    // Getters and setters
}
```

#### 2. 更新数据库实体

**修改 QuestionEntity**：`data/local/entity/QuestionEntity.java`
```java
@Entity(tableName = "questions")
public class QuestionEntity {
    // 现有字段...
    
    private int likeCount;  // 新增：点赞数
    private boolean isLiked;  // 新增：当前用户是否点赞
    
    // Getters and setters
}
```

**创建数据库迁移**：`di/DatabaseModule.java`
```java
static final Migration MIGRATION_6_7 = new Migration(6, 7) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE questions ADD COLUMN likeCount INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE questions ADD COLUMN isLiked INTEGER NOT NULL DEFAULT 0");
    }
};

// 在 provideAppDatabase 中添加
.addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

#### 3. 添加 API 接口

**更新 ApiService**：`data/api/ApiService.java`
```java
public interface ApiService {
    // 现有接口...
    
    @POST("api/questions/like")
    Call<LikeResponse> likeQuestion(
        @Header("Authorization") String token,
        @Body LikeRequest request
    );
    
    @POST("api/questions/unlike")
    Call<LikeResponse> unlikeQuestion(
        @Header("Authorization") String token,
        @Body LikeRequest request
    );
}
```

#### 4. 更新 ViewModel

**添加点赞方法**：`ui/student/StudentViewModel.java`
```java
@HiltViewModel
public class StudentViewModel extends BaseQuestionListViewModel {
    // 现有代码...
    
    public void toggleLike(long questionId, boolean currentLikeStatus) {
        ioExecutor.execute(() -> {
            try {
                String token = "Bearer " + prefsManager.getToken();
                
                // 乐观更新本地数据库
                QuestionEntity question = questionDao.getQuestionByIdSync(questionId);
                question.setLiked(!currentLikeStatus);
                question.setLikeCount(question.getLikeCount() + (currentLikeStatus ? -1 : 1));
                questionDao.update(question);
                
                // 调用 API
                Call<LikeResponse> call;
                if (currentLikeStatus) {
                    call = apiService.unlikeQuestion(token, new LikeRequest(questionId));
                } else {
                    call = apiService.likeQuestion(token, new LikeRequest(questionId));
                }
                
                Response<LikeResponse> response = call.execute();
                
                if (response.isSuccessful() && response.body() != null) {
                    LikeResponse.LikeData data = response.body().getData();
                    
                    // 更新为服务器返回的真实数据
                    question.setLikeCount(data.getLikeCount());
                    questionDao.update(question);
                    
                    Log.d(TAG, "Like toggled successfully");
                } else {
                    // 回滚乐观更新
                    question.setLiked(currentLikeStatus);
                    question.setLikeCount(question.getLikeCount() + (currentLikeStatus ? 1 : -1));
                    questionDao.update(question);
                    
                    errorMessage.postValue("点赞失败，请重试");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error toggling like", e);
                errorMessage.postValue("网络错误：" + e.getMessage());
            }
        });
    }
}
```

#### 5. 更新 UI

**修改 Activity**：`ui/student/QuestionDetailActivity.java`
```java
@AndroidEntryPoint
public class QuestionDetailActivity extends BaseActivity {
    private ActivityQuestionDetailBinding binding;
    private StudentViewModel viewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuestionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        viewModel = new ViewModelProvider(this).get(StudentViewModel.class);
        
        // 观察问题数据
        viewModel.getQuestionById(questionId).observe(this, question -> {
            if (question != null) {
                binding.likeCount.setText(String.valueOf(question.getLikeCount()));
                binding.likeButton.setSelected(question.isLiked());
            }
        });
        
        // 点赞按钮点击事件
        binding.likeButton.setOnClickListener(v -> {
            QuestionEntity question = viewModel.getQuestionById(questionId).getValue();
            if (question != null) {
                viewModel.toggleLike(questionId, question.isLiked());
            }
        });
    }
}
```

**更新布局文件**：`res/layout/activity_question_detail.xml`
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">
    
    <ImageButton
        android:id="@+id/likeButton"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:src="@drawable/ic_like"
        android:background="?attr/selectableItemBackgroundBorderless" />
    
    <TextView
        android:id="@+id/likeCount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:text="0" />
</LinearLayout>
```

#### 6. 后端实现（配合）

需要后端添加相应的 API 端点：
```python
@app.post("/api/questions/like")
async def like_question(
    request: LikeRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, Any]:
    # 实现点赞逻辑
    pass
```

### ViewModel 使用规范

#### 继承关系

```
AndroidViewModel (Android 框架)
    ↓
BaseViewModel (项目基类)
    ↓
BaseQuestionListViewModel (问题列表基类)
    ↓
StudentViewModel / TutorViewModel (具体实现)
```

#### BaseViewModel 提供的功能

```java
public class BaseViewModel extends AndroidViewModel {
    // 线程池
    protected final ExecutorService ioExecutor;        // IO 操作
    protected final ExecutorService computeExecutor;   // 计算密集
    
    // LiveData
    protected final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    protected final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    
    // 错误处理
    protected void handleError(Throwable throwable) {
        String message = ErrorHandler.getErrorMessage(throwable);
        errorMessage.postValue(message);
    }
    
    // 清理资源
    @Override
    protected void onCleared() {
        super.onCleared();
        // 自动清理线程池和资源
    }
}
```

#### 使用示例

```java
@HiltViewModel
public class MyViewModel extends BaseViewModel {
    private final ApiService apiService;
    private final MyDao myDao;
    
    @Inject
    public MyViewModel(
        @NonNull Application application,
        ApiService apiService,
        MyDao myDao
    ) {
        super(application);
        this.apiService = apiService;
        this.myDao = myDao;
    }
    
    public void loadData() {
        isLoading.postValue(true);
        
        ioExecutor.execute(() -> {
            try {
                // 执行 IO 操作
                Response<MyResponse> response = apiService.getData().execute();
                
                if (response.isSuccessful()) {
                    // 处理成功
                } else {
                    errorMessage.postValue("加载失败");
                }
            } catch (Exception e) {
                handleError(e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
}
```

### Repository 层最佳实践

#### 职责划分

```java
@Singleton
public class QuestionRepository {
    private final ApiService apiService;      // 网络数据源
    private final QuestionDao questionDao;    // 本地数据源
    private final ExecutorService executor;    // 线程池
    
    // 职责 1：同步数据（网络 → 本地）
    public void syncQuestionsFromServer(String token, SyncCallback callback) {
        // 从服务器获取数据
        // 更新本地数据库
        // 清理不存在的数据
    }
    
    // 职责 2：提供数据访问接口
    public LiveData<List<QuestionEntity>> getQuestions() {
        return questionDao.getAllQuestions();
    }
    
    // 职责 3：数据转换
    private QuestionEntity toEntity(QuestionsListResponse.QuestionData data) {
        // 将 API 响应转换为本地实体
    }
}
```

#### 数据同步策略

**下拉刷新（全量同步）**：
```java
public void refreshQuestions(String token, RefreshCallback callback) {
    apiService.getQuestions(token, null, 1, 20)
        .enqueue(new Callback<QuestionsListResponse>() {
            @Override
            public void onResponse(Call<QuestionsListResponse> call, Response<QuestionsListResponse> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> {
                        // 1. 获取服务器数据的 ID 集合
                        Set<Long> serverIds = new HashSet<>();
                        for (QuestionData data : response.body().getQuestions()) {
                            serverIds.add(data.getId());
                            questionDao.insert(toEntity(data));  // 插入或更新
                        }
                        
                        // 2. 删除本地存在但服务器不存在的数据
                        List<QuestionEntity> localQuestions = questionDao.getAllQuestionsSync();
                        for (QuestionEntity local : localQuestions) {
                            if (!serverIds.contains(local.getId())) {
                                questionDao.delete(local);
                            }
                        }
                        
                        callback.onSuccess();
                    });
                }
            }
        });
}
```

**加载更多（增量同步）**：
```java
public void loadMoreQuestions(String token, int page, LoadMoreCallback callback) {
    apiService.getQuestions(token, null, page, 20)
        .enqueue(new Callback<QuestionsListResponse>() {
            @Override
            public void onResponse(Call<QuestionsListResponse> call, Response<QuestionsListResponse> response) {
                if (response.isSuccessful()) {
                    executor.execute(() -> {
                        // 只插入新数据，不删除现有数据
                        for (QuestionData data : response.body().getQuestions()) {
                            questionDao.insert(toEntity(data));
                        }
                        
                        callback.onSuccess();
                    });
                }
            }
        });
}
```

### WebSocket 消息处理

#### 连接管理

```java
@Singleton
public class WebSocketManager {
    public void connect() {
        // 检查是否已连接
        if (webSocketClient != null) return;
        
        // 创建连接
        webSocketClient = new WebSocketClient(okHttpClient, url, callback);
        webSocketClient.connect();
    }
    
    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }
    }
    
    public void reconnect() {
        disconnect();
        connect();
    }
}
```

#### 消息处理

```java
private void handleMessage(WebSocketMessage message, String role) {
    String type = message.getType();
    
    switch (type) {
        case WebSocketMessageType.NEW_QUESTION:
            if ("tutor".equals(role)) {
                insertQuestionFromWebSocket(message.getData());
            }
            break;
            
        case WebSocketMessageType.CHAT_MESSAGE:
            insertMessageFromWebSocket(message.getData());
            break;
            
        case WebSocketMessageType.QUESTION_UPDATED:
            updateQuestionFromWebSocket(message.getData());
            break;
            
        case WebSocketMessageType.ACK:
            handleAck(message.getMessageId());
            break;
    }
}
```

### 图片上传和显示

#### 上传图片

```java
public void uploadImage(String imagePath, UploadCallback callback) {
    File file = new File(imagePath);
    
    // 1. 创建 RequestBody
    RequestBody requestFile = RequestBody.create(
        MediaType.parse("image/jpeg"),
        file
    );
    
    // 2. 创建 MultipartBody.Part
    MultipartBody.Part body = MultipartBody.Part.createFormData(
        "image",
        file.getName(),
        requestFile
    );
    
    // 3. 调用 API
    String token = "Bearer " + prefsManager.getToken();
    apiService.uploadImage(token, body)
        .enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String imagePath = response.body().getImagePath();
                    callback.onSuccess(imagePath);
                } else {
                    callback.onError("上传失败");
                }
            }
            
            @Override
            public void onFailure(Call<UploadResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
}
```

#### 显示图片（Glide）

```java
// 普通图片加载
Glide.with(context)
    .load(BuildConfig.BASE_URL.replace("/", "") + imagePath)
    .placeholder(R.drawable.placeholder)
    .error(R.drawable.error)
    .into(imageView);

// 圆形头像
Glide.with(context)
    .load(imageUrl)
    .circleCrop()
    .into(avatarImageView);

// 缩略图
Glide.with(context)
    .load(imageUrl)
    .thumbnail(0.1f)  // 加载 10% 大小的缩略图
    .into(thumbnailImageView);
```

#### 图片选择

```java
// 从相册选择
Intent intent = new Intent(Intent.ACTION_PICK);
intent.setType("image/*");
startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);

// 拍照
Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
if (intent.resolveActivity(getPackageManager()) != null) {
    File photoFile = createImageFile();
    Uri photoURI = FileProvider.getUriForFile(
        this,
        "com.dowdah.asknow.fileprovider",
        photoFile
    );
    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
    startActivityForResult(intent, REQUEST_CODE_TAKE_PHOTO);
}

// 处理结果
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (resultCode == RESULT_OK) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE) {
            Uri selectedImageUri = data.getData();
            String imagePath = getRealPathFromURI(selectedImageUri);
            uploadImage(imagePath);
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO) {
            uploadImage(currentPhotoPath);
        }
    }
}
```

---

## 后端开发指南

### 环境配置

#### 使用 pipenv（推荐）

```bash
cd backend

# 安装依赖
pipenv install

# 激活虚拟环境
pipenv shell

# 运行服务器
python main.py

# 或使用 pipenv run
pipenv run python main.py
```

#### 使用 pip

```bash
cd backend

# 创建虚拟环境
python -m venv venv

# 激活虚拟环境
# macOS/Linux:
source venv/bin/activate
# Windows:
venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 运行服务器
python main.py
```

### 添加新 API 端点

#### 1. 定义 Pydantic 模型

```python
# main.py
from pydantic import BaseModel

class MyRequest(BaseModel):
    field1: str
    field2: int
    optional_field: Optional[str] = None
```

#### 2. 创建路由

```python
@app.post("/api/my-endpoint")
async def my_endpoint(
    request: MyRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, Any]:
    """
    API 端点说明
    
    Args:
        request: 请求数据
        current_user: 当前登录用户（自动注入）
        db: 数据库会话（自动注入）
        
    Returns:
        Dict[str, Any]: 响应数据
        
    Raises:
        HTTPException: 如果操作失败
    """
    try:
        # 业务逻辑
        logger.info(f"User {current_user.id} called my_endpoint")
        
        # 数据库操作
        result = await db.execute(
            select(MyModel).where(MyModel.id == request.field2)
        )
        item = result.scalar_one_or_none()
        
        if not item:
            raise HTTPException(status_code=404, detail="Item not found")
        
        # 返回响应
        return {
            "success": True,
            "message": "Operation successful",
            "data": item.to_dict()
        }
    except HTTPException:
        raise
    except SQLAlchemyError as e:
        logger.error(f"Database error: {str(e)}", exc_info=True)
        await db.rollback()
        raise HTTPException(status_code=500, detail="Database error")
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail="Internal server error")
```

#### 3. 测试 API

```bash
# 使用 curl
curl -X POST http://localhost:8000/api/my-endpoint \
  -H "Authorization: Bearer your_token" \
  -H "Content-Type: application/json" \
  -d '{"field1": "value", "field2": 123}'

# 使用 Python requests
import requests

response = requests.post(
    "http://localhost:8000/api/my-endpoint",
    headers={"Authorization": f"Bearer {token}"},
    json={"field1": "value", "field2": 123}
)
print(response.json())
```

### 数据库模型修改

#### 添加新字段

```python
# models.py
class Question(Base):
    __tablename__ = "questions"
    
    # 现有字段...
    
    # 新增字段
    like_count = Column(Integer, default=0, nullable=False)
```

#### 数据库迁移

SQLite 不支持完整的数据库迁移，需要手动处理：

**方案 1：重置数据库（开发环境）**
```bash
cd backend
python reset_db.py
```

**方案 2：手动迁移（生产环境）**
```python
# migration_script.py
import asyncio
from sqlalchemy import text
from database import engine

async def migrate():
    async with engine.begin() as conn:
        # 添加新列
        await conn.execute(text(
            "ALTER TABLE questions ADD COLUMN like_count INTEGER NOT NULL DEFAULT 0"
        ))
        print("Migration completed successfully")

if __name__ == "__main__":
    asyncio.run(migrate())
```

**方案 3：使用 Alembic（推荐生产环境）**
```bash
# 安装 Alembic
pip install alembic

# 初始化
alembic init alembic

# 创建迁移
alembic revision --autogenerate -m "Add like_count to questions"

# 执行迁移
alembic upgrade head
```

### WebSocket 消息处理

#### 发送消息给指定用户

```python
from websocket_manager import manager

# 在 API 端点中
@app.post("/api/some-action")
async def some_action(
    request: SomeRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, Any]:
    # 执行操作...
    
    # 通过 WebSocket 通知对方
    await manager.send_personal_message(
        message={
            "type": "SOME_EVENT",
            "data": {"key": "value"},
            "timestamp": str(int(time.time() * 1000))
        },
        user_id=target_user_id
    )
    
    return {"success": True}
```

#### 广播给所有教师

```python
# 创建问题后，通知所有在线教师
@app.post("/api/questions")
async def create_question(...):
    # 创建问题...
    
    # 广播给所有教师
    await manager.broadcast_to_tutors(
        new_question.to_ws_message(config.WS_TYPE_NEW_QUESTION)
    )
    
    return {"success": True, "question": new_question.to_dict()}
```

### 配置管理

#### 使用环境变量

```bash
# .env 文件（不要提交到 Git）
DEBUG=True
SECRET_KEY=my-super-secret-key
DATABASE_URL=postgresql+asyncpg://user:pass@localhost/asknow
HOST=0.0.0.0
PORT=8000
LOG_LEVEL=DEBUG
```

#### 读取配置

```python
# config.py
import os

SECRET_KEY = os.getenv("SECRET_KEY", "default-secret-key")
DEBUG = os.getenv("DEBUG", "False").lower() == "true"
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite+aiosqlite:///./asknow.db")
```

#### 配置的使用

```python
# main.py
import config

app = FastAPI(
    title=config.APP_NAME,
    version=config.APP_VERSION,
    debug=config.DEBUG
)
```

### 日志记录和调试

#### 配置日志

```python
import logging

# 在 main.py 顶部配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('app.log'),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)
```

#### 使用日志

```python
# 不同级别的日志
logger.debug("Debug message")
logger.info("Info message")
logger.warning("Warning message")
logger.error("Error message")
logger.critical("Critical message")

# 记录异常
try:
    # 一些操作
    pass
except Exception as e:
    logger.error(f"Error occurred: {str(e)}", exc_info=True)
```

#### 查看日志

```bash
# 实时查看日志
tail -f backend/app.log

# 过滤错误日志
grep "ERROR" backend/app.log

# 查看最近 100 行
tail -n 100 backend/app.log
```

### API 测试

#### 使用测试脚本

```bash
cd backend
python test_api.py
```

测试脚本会自动：
1. 注册测试用户
2. 测试所有 API 端点
3. 测试 WebSocket 连接
4. 输出彩色测试报告

#### 编写自定义测试

```python
import asyncio
import aiohttp

async def test_create_question():
    async with aiohttp.ClientSession() as session:
        # 登录获取 token
        async with session.post(
            "http://localhost:8000/api/login",
            json={"username": "student001", "password": "123456"}
        ) as response:
            data = await response.json()
            token = data["token"]
        
        # 创建问题
        async with session.post(
            "http://localhost:8000/api/questions",
            headers={"Authorization": f"Bearer {token}"},
            json={"content": "测试问题", "imagePaths": []}
        ) as response:
            data = await response.json()
            assert data["success"] == True
            print("✓ Create question test passed")

if __name__ == "__main__":
    asyncio.run(test_create_question())
```

---

## 代码规范

### 命名规范

#### Android (Java)

| 类型 | 规范 | 示例 |
|------|------|------|
| Activity | `XxxActivity` | `LoginActivity`, `StudentMainActivity` |
| Fragment | `XxxFragment` | `QuestionListFragment` |
| ViewModel | `XxxViewModel` | `ChatViewModel`, `StudentViewModel` |
| Repository | `XxxRepository` | `QuestionRepository` |
| Adapter | `XxxAdapter` | `QuestionAdapter`, `MessageAdapter` |
| Entity | `XxxEntity` | `QuestionEntity`, `MessageEntity` |
| DAO | `XxxDao` | `QuestionDao`, `MessageDao` |
| Util | `XxxUtils` 或 `XxxHelper` | `DateUtils`, `ImageMessageHelper` |
| Constants | `大写_下划线` | `MAX_RETRY_COUNT`, `BASE_URL` |

**变量命名**：
```java
// 成员变量：驼峰命名
private String userName;
private List<Question> questionList;

// 常量：大写下划线
private static final int MAX_COUNT = 100;
private static final String TAG = "MyClass";

// 局部变量：驼峰命名
String userId = "123";
int itemCount = list.size();
```

#### Python

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | `PascalCase` | `User`, `Question`, `WebSocketManager` |
| 函数名 | `snake_case` | `get_user`, `create_question` |
| 变量名 | `snake_case` | `user_id`, `question_list` |
| 常量 | `大写_下划线` | `MAX_FILE_SIZE`, `SECRET_KEY` |
| 私有成员 | `_前缀` | `_internal_method` |

### 注释规范

#### Java 注释

**类注释**：
```java
/**
 * 聊天 ViewModel
 * 
 * 负责处理消息的发送、接收和问题状态的管理。
 * 
 * 主要功能：
 * - 乐观更新：发送消息时立即显示
 * - 消息状态管理：pending、sent、failed
 * - 问题状态管理：接受问题、关闭问题
 * - 已读未读管理：标记消息为已读
 * 
 * @author Your Name
 * @since 1.0.0
 */
@HiltViewModel
public class ChatViewModel extends BaseViewModel {
    // ...
}
```

**方法注释**：
```java
/**
 * 发送文本消息
 * 
 * 使用乐观更新策略：
 * 1. 立即插入本地数据库（status = pending）
 * 2. 发送 HTTP 请求
 * 3. 成功后更新状态为 sent，失败则标记 failed
 * 
 * @param questionId 问题 ID
 * @param content 消息内容
 */
public void sendTextMessage(long questionId, String content) {
    // ...
}
```

**复杂逻辑注释**：
```java
// 乐观更新：立即插入本地数据库，提升用户体验
MessageEntity tempMessage = new MessageEntity();
tempMessage.setId(tempIdGenerator.getAndDecrement());  // 使用负数临时 ID
tempMessage.setSendStatus(MessageStatus.PENDING);
long tempMessageId = messageDao.insert(tempMessage);

// 发送 HTTP 请求到服务器
apiService.sendMessage(token, request)
    .enqueue(new Callback<MessageResponse>() {
        // ...
    });
```

#### Python 注释

**函数注释（Google 风格）**：
```python
def create_question(
    request: QuestionRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
) -> Dict[str, Any]:
    """
    创建新问题
    
    学生创建新问题，状态为 pending。创建成功后会通过 WebSocket 广播给所有在线教师。
    
    Args:
        request: 问题请求数据，包含 content 和可选的 imagePaths
        current_user: 当前登录用户（通过 JWT 认证）
        db: 数据库会话
        
    Returns:
        Dict[str, Any]: 包含成功状态和问题数据的字典
        {
            "success": True,
            "message": "Question created successfully",
            "question": {...}
        }
        
    Raises:
        HTTPException: 如果创建失败（500 错误）
    """
    try:
        # 实现...
        pass
    except Exception as e:
        logger.error(f"Error creating question: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail="Failed to create question")
```

### Git 提交规范

#### 提交消息格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型（type）**：
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档修改
- `style`: 代码格式调整（不影响功能）
- `refactor`: 重构（不是新功能，也不是修复 bug）
- `perf`: 性能优化
- `test`: 添加或修改测试
- `chore`: 构建过程或辅助工具的变动

**示例**：
```bash
# 新功能
git commit -m "feat(chat): 添加消息已读功能"

# 修复 bug
git commit -m "fix(websocket): 修复重连失败的问题"

# 文档
git commit -m "docs(readme): 更新安装说明"

# 重构
git commit -m "refactor(repository): 优化数据同步逻辑"
```

---

## 测试指南

### Android 单元测试

#### 编写测试

```java
// DateUtilsTest.java
public class DateUtilsTest {
    @Test
    public void testFormatTimestamp() {
        long timestamp = 1234567890000L;
        String formatted = DateUtils.formatTimestamp(timestamp);
        assertNotNull(formatted);
        assertTrue(formatted.length() > 0);
    }
    
    @Test
    public void testGetRelativeTime() {
        long now = System.currentTimeMillis();
        long oneMinuteAgo = now - 60 * 1000;
        
        String relativeTime = DateUtils.getRelativeTime(oneMinuteAgo);
        assertEquals("1分钟前", relativeTime);
    }
}
```

#### 运行测试

```bash
cd AskNow
./gradlew test

# 查看测试报告
open app/build/reports/tests/testDebugUnitTest/index.html
```

### 后端测试

#### 运行测试脚本

```bash
cd backend
python test_api.py
```

#### API 测试覆盖

测试脚本覆盖：
- ✅ 用户注册和登录
- ✅ 问题 CRUD 操作
- ✅ 消息发送和接收
- ✅ 文件上传
- ✅ WebSocket 连接
- ✅ 权限验证

---

## 调试技巧

### Android Studio 调试

#### 断点调试

1. 在代码行号处点击设置断点
2. 点击 Debug 按钮运行应用
3. 当代码执行到断点时会暂停
4. 使用调试工具栏：
   - Step Over (F8): 单步执行
   - Step Into (F7): 进入方法
   - Step Out (Shift+F8): 跳出方法
   - Resume (F9): 继续执行

#### 条件断点

右键点击断点 → Condition → 输入条件：
```java
questionId == 123
```

#### 日志输出

```java
// 使用 Android Log
Log.d(TAG, "Debug message");
Log.i(TAG, "Info message");
Log.w(TAG, "Warning message");
Log.e(TAG, "Error message", exception);

// 在 Logcat 中过滤
// 选择设备 → 输入 TAG 或包名 → 选择日志级别
```

### WebSocket 调试

#### 使用 Chrome DevTools

1. 打开 Chrome 浏览器
2. F12 打开 DevTools
3. 进入 Console 标签
4. 执行 WebSocket 连接：

```javascript
// 连接
const ws = new WebSocket('ws://localhost:8000/ws/123');

ws.onopen = () => {
    console.log('Connected');
};

ws.onmessage = (event) => {
    console.log('Received:', JSON.parse(event.data));
};

ws.send(JSON.stringify({
    type: "TEST",
    data: {},
    timestamp: Date.now().toString()
}));
```

#### 使用 Postman

Postman 支持 WebSocket 测试：
1. 新建 WebSocket Request
2. 输入 URL: `ws://localhost:8000/ws/123`
3. 点击 Connect
4. 发送和接收消息

### 网络请求抓包

#### 使用 Charles Proxy

1. 下载安装 Charles
2. 配置手机代理：
   - WiFi 设置 → 代理 → 手动
   - 服务器：电脑 IP
   - 端口：8888
3. 安装 Charles 证书（HTTPS 抓包）
4. 在 Charles 中查看请求和响应

#### 使用 OkHttp 日志拦截器

已在项目中配置：
```java
HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(interceptor)
    .build();
```

查看 Logcat 过滤 `OkHttp` 查看所有请求。

### 数据库查看

#### Android (Room)

**使用 Database Inspector**（Android Studio）：
1. 运行应用（调试模式）
2. View → Tool Windows → App Inspection
3. 选择 Database Inspector
4. 查看表结构和数据

**使用 adb**：
```bash
# 导出数据库
adb pull /data/data/com.dowdah.asknow/databases/asknow_database .

# 使用 SQLite 工具查看
sqlite3 asknow_database
.tables
SELECT * FROM questions;
```

#### Python (SQLite)

```bash
cd backend

# 使用 sqlite3
sqlite3 asknow.db

# 查看表
.tables

# 查询数据
SELECT * FROM users;
SELECT * FROM questions WHERE status = 'pending';

# 退出
.quit
```

---

## 常见问题

### Android 端

**Q: Gradle 同步失败**
```
A: 1. 检查网络连接
   2. 配置镜像源（阿里云 Maven）
   3. 清除 Gradle 缓存：./gradlew clean
   4. 删除 .gradle 文件夹重新同步
```

**Q: 无法连接到后端**
```
A: 1. 检查后端服务是否运行
   2. 模拟器使用 10.0.2.2，真机使用局域网 IP
   3. 检查防火墙设置
   4. 查看 Logcat 日志中的网络错误
```

**Q: WebSocket 连接失败**
```
A: 1. 确认 URL 格式正确（ws:// 而非 http://）
   2. 检查用户 ID 是否正确
   3. 查看后端日志是否有错误
   4. 检查网络权限和网络状态
```

**Q: 图片上传失败**
```
A: 1. 检查文件大小（不超过 10MB）
   2. 检查文件类型（仅支持图片）
   3. 检查存储权限
   4. 查看后端日志中的错误信息
```

### 后端

**Q: 启动服务器失败**
```
A: 1. 检查 Python 版本（需要 3.8+）
   2. 检查依赖是否完整安装
   3. 检查端口 8000 是否被占用
   4. 查看错误日志
```

**Q: 数据库错误**
```
A: 1. 检查数据库文件权限
   2. 尝试重置数据库：python reset_db.py
   3. 检查 SQLAlchemy 版本
   4. 查看详细错误日志
```

**Q: JWT Token 验证失败**
```
A: 1. 检查 SECRET_KEY 配置
   2. 确认 Token 格式正确（Bearer <token>）
   3. 检查 Token 是否过期
   4. 验证用户是否存在且未删除
```

---

## 相关文档

- [技术架构详解](ARCHITECTURE.md)
- [API 参考文档](API_REFERENCE.md)
- [部署指南](DEPLOYMENT.md)

