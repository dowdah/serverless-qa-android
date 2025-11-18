# AskNow 部署指南

本文档详细说明如何将 AskNow 应用部署到生产环境。

## 目录

- [生产环境准备](#生产环境准备)
- [后端部署](#后端部署)
- [Android 应用发布](#android-应用发布)
- [安全配置](#安全配置)
- [监控和维护](#监控和维护)
- [常见问题](#常见问题)

---

## 生产环境准备

### 服务器要求

#### 最低配置

| 资源 | 要求 |
|------|------|
| CPU | 2 核心 |
| 内存 | 2 GB RAM |
| 存储 | 20 GB SSD |
| 网络 | 10 Mbps |
| 操作系统 | Ubuntu 20.04+ / CentOS 8+ |

#### 推荐配置

| 资源 | 要求 |
|------|------|
| CPU | 4 核心 |
| 内存 | 4 GB RAM |
| 存储 | 50 GB SSD |
| 网络 | 100 Mbps |
| 操作系统 | Ubuntu 22.04 LTS |

### 数据库选择

#### SQLite（适用于小型应用）

**优点：**
- 无需单独安装数据库服务器
- 配置简单
- 适合小规模应用（< 100 并发用户）

**限制：**
- 不支持高并发写入
- 单文件存储
- 无法横向扩展

#### PostgreSQL（推荐生产环境）

**优点：**
- 支持高并发
- ACID 兼容
- 丰富的功能和扩展
- 可靠性高

**安装 PostgreSQL：**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql postgresql-contrib

# 启动服务
sudo systemctl start postgresql
sudo systemctl enable postgresql

# 创建数据库和用户
sudo -u postgres psql
CREATE DATABASE asknow;
CREATE USER asknow_user WITH PASSWORD 'your_strong_password';
GRANT ALL PRIVILEGES ON DATABASE asknow TO asknow_user;
\q
```

**配置连接字符串：**

```bash
# 修改 .env 文件
DATABASE_URL=postgresql+asyncpg://asknow_user:your_strong_password@localhost/asknow
```

---

## 后端部署

### 方式一：使用 Gunicorn + Uvicorn Workers（推荐）

#### 1. 安装依赖

```bash
cd backend

# 安装生产环境依赖
pip install gunicorn uvicorn[standard]

# 或添加到 requirements.txt
echo "gunicorn==21.2.0" >> requirements.txt
echo "uvicorn[standard]==0.27.0" >> requirements.txt
pip install -r requirements.txt
```

#### 2. 创建启动脚本

**start.sh**:
```bash
#!/bin/bash

# 激活虚拟环境（如果使用）
source venv/bin/activate

# 设置环境变量
export SECRET_KEY="$(openssl rand -hex 32)"
export DATABASE_URL="postgresql+asyncpg://asknow_user:password@localhost/asknow"
export HOST="0.0.0.0"
export PORT="8000"
export LOG_LEVEL="INFO"
export LOG_TO_FILE="True"
export DEBUG="False"

# 启动 Gunicorn
gunicorn main:app \
    --workers 4 \
    --worker-class uvicorn.workers.UvicornWorker \
    --bind 0.0.0.0:8000 \
    --access-logfile access.log \
    --error-logfile error.log \
    --log-level info \
    --timeout 120 \
    --keepalive 5 \
    --max-requests 1000 \
    --max-requests-jitter 100
```

**赋予执行权限：**
```bash
chmod +x start.sh
```

**启动服务：**
```bash
./start.sh
```

#### 3. Gunicorn 配置详解

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `--workers` | 工作进程数量 | `2-4 × CPU 核心数` |
| `--worker-class` | 工作进程类型 | `uvicorn.workers.UvicornWorker` |
| `--bind` | 绑定地址和端口 | `0.0.0.0:8000` |
| `--timeout` | 请求超时时间（秒）| `120` |
| `--keepalive` | Keep-Alive 超时（秒）| `5` |
| `--max-requests` | 工作进程重启阈值 | `1000` |
| `--max-requests-jitter` | 重启抖动 | `100` |

**计算 Workers 数量：**
```python
# 推荐公式
workers = (2 * CPU_CORES) + 1

# 示例
# 4 核 CPU: workers = 9
# 2 核 CPU: workers = 5
```

### 方式二：使用 systemd 管理进程

#### 1. 创建 systemd 服务文件

**/etc/systemd/system/asknow.service**:
```ini
[Unit]
Description=AskNow FastAPI Application
After=network.target postgresql.service

[Service]
Type=notify
User=www-data
Group=www-data
WorkingDirectory=/var/www/asknow/backend
Environment="PATH=/var/www/asknow/backend/venv/bin"
Environment="SECRET_KEY=your-generated-secret-key"
Environment="DATABASE_URL=postgresql+asyncpg://asknow_user:password@localhost/asknow"
Environment="HOST=0.0.0.0"
Environment="PORT=8000"
Environment="DEBUG=False"
Environment="LOG_LEVEL=INFO"
ExecStart=/var/www/asknow/backend/venv/bin/gunicorn main:app \
    --workers 4 \
    --worker-class uvicorn.workers.UvicornWorker \
    --bind 0.0.0.0:8000 \
    --timeout 120
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

#### 2. 启用和管理服务

```bash
# 重新加载 systemd 配置
sudo systemctl daemon-reload

# 启用服务（开机自启）
sudo systemctl enable asknow

# 启动服务
sudo systemctl start asknow

# 查看状态
sudo systemctl status asknow

# 查看日志
sudo journalctl -u asknow -f

# 停止服务
sudo systemctl stop asknow

# 重启服务
sudo systemctl restart asknow
```

### 方式三：使用 Supervisor

#### 1. 安装 Supervisor

```bash
sudo apt install supervisor
```

#### 2. 创建配置文件

**/etc/supervisor/conf.d/asknow.conf**:
```ini
[program:asknow]
command=/var/www/asknow/backend/venv/bin/gunicorn main:app \
    --workers 4 \
    --worker-class uvicorn.workers.UvicornWorker \
    --bind 0.0.0.0:8000
directory=/var/www/asknow/backend
user=www-data
autostart=true
autorestart=true
redirect_stderr=true
stdout_logfile=/var/log/asknow/app.log
environment=SECRET_KEY="your-secret-key",DATABASE_URL="postgresql+asyncpg://..."
```

#### 3. 管理进程

```bash
# 更新配置
sudo supervisorctl reread
sudo supervisorctl update

# 启动
sudo supervisorctl start asknow

# 停止
sudo supervisorctl stop asknow

# 重启
sudo supervisorctl restart asknow

# 查看状态
sudo supervisorctl status asknow

# 查看日志
sudo tail -f /var/log/asknow/app.log
```

### 配置 Nginx 反向代理

#### 1. 安装 Nginx

```bash
sudo apt update
sudo apt install nginx
```

#### 2. 创建配置文件

**/etc/nginx/sites-available/asknow**:
```nginx
# HTTP to HTTPS redirect
server {
    listen 80;
    listen [::]:80;
    server_name your-domain.com www.your-domain.com;
    
    # Let's Encrypt challenge
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    
    # Redirect all other traffic to HTTPS
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS server
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name your-domain.com www.your-domain.com;
    
    # SSL certificates
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # SSL configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256';
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    
    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";
    
    # Max upload size (for image uploads)
    client_max_body_size 10M;
    
    # Timeouts
    proxy_connect_timeout 120s;
    proxy_send_timeout 120s;
    proxy_read_timeout 120s;
    
    # API endpoints
    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # WebSocket endpoint
    location /ws/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket timeout
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
    
    # Uploaded files
    location /uploads/ {
        alias /var/www/asknow/backend/uploads/;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
    
    # Health check
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

#### 3. 启用站点

```bash
# 创建符号链接
sudo ln -s /etc/nginx/sites-available/asknow /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重新加载配置
sudo systemctl reload nginx
```

### 配置 HTTPS/TLS

#### 使用 Let's Encrypt（免费）

##### 1. 安装 Certbot

```bash
sudo apt install certbot python3-certbot-nginx
```

##### 2. 获取证书

```bash
# 自动配置 Nginx
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 或手动配置
sudo certbot certonly --webroot -w /var/www/certbot \
    -d your-domain.com -d www.your-domain.com
```

##### 3. 自动续期

```bash
# 测试续期
sudo certbot renew --dry-run

# 添加定时任务
sudo crontab -e

# 每天凌晨 2 点检查并续期
0 2 * * * certbot renew --quiet --deploy-hook "systemctl reload nginx"
```

---

## Android 应用发布

### 生成签名密钥

#### 1. 创建密钥库

```bash
keytool -genkey -v \
    -keystore asknow-release-key.jks \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -alias asknow-key
```

**填写信息：**
```
Enter keystore password: [输入密码]
Re-enter new password: [再次输入]
What is your first and last name? [你的名字]
What is the name of your organizational unit? [部门]
What is the name of your organization? [组织]
What is the name of your City or Locality? [城市]
What is the name of your State or Province? [省份]
What is the two-letter country code for this unit? [国家代码，如 CN]
Is CN=..., OU=..., O=..., L=..., ST=..., C=CN correct? [yes]
```

#### 2. 配置签名

**创建密钥属性文件**：`AskNow/keystore.properties`
```properties
storePassword=your_keystore_password
keyPassword=your_key_password
keyAlias=asknow-key
storeFile=../asknow-release-key.jks
```

**添加到 .gitignore**：
```bash
echo "keystore.properties" >> .gitignore
echo "*.jks" >> .gitignore
```

#### 3. 配置 build.gradle.kts

**AskNow/app/build.gradle.kts**:
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // ...
}

// 读取签名配置
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.dowdah.asknow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dowdah.asknow"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        release {
            // 生产环境服务器地址
            buildConfigField("String", "BASE_URL", "\"https://your-domain.com/\"")
            
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### 配置 ProGuard/R8 混淆

**AskNow/app/proguard-rules.pro**:
```proguard
# Keep default rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep data models
-keep class com.dowdah.asknow.data.model.** { *; }
-keep class com.dowdah.asknow.data.local.entity.** { *; }

# Keep WebSocket message classes
-keep class com.dowdah.asknow.data.model.WebSocketMessage { *; }
-keep class com.dowdah.asknow.data.model.WebSocketMessage$* { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
```

### 构建 Release APK

#### 方法 1：使用 Gradle

```bash
cd AskNow

# 构建 Release APK
./gradlew assembleRelease

# 输出位置
# AskNow/app/build/outputs/apk/release/app-release.apk
```

#### 方法 2：使用 Android Studio

1. Build → Generate Signed Bundle / APK
2. 选择 APK
3. 选择密钥库文件
4. 输入密码
5. 选择 release 构建变体
6. 点击 Finish

### 构建 Android App Bundle (AAB)

**推荐用于 Google Play 发布：**

```bash
./gradlew bundleRelease

# 输出位置
# AskNow/app/build/outputs/bundle/release/app-release.aab
```

### 版本管理

**更新版本号**：`AskNow/app/build.gradle.kts`
```kotlin
defaultConfig {
    versionCode = 2        // 每次发布递增（整数）
    versionName = "1.0.1"  // 用户可见版本号
}
```

**版本号规范：**
- `versionCode`: 内部版本号，必须递增
- `versionName`: 语义化版本号（major.minor.patch）
  - major: 重大更新（不兼容的 API 变更）
  - minor: 新功能（向后兼容）
  - patch: Bug 修复（向后兼容）

---

## 安全配置

### JWT 密钥管理

#### 生成强密钥

```bash
# 使用 OpenSSL 生成 32 字节随机密钥
openssl rand -hex 32

# 或使用 Python
python -c "import secrets; print(secrets.token_hex(32))"
```

#### 配置密钥

**方式 1：环境变量（推荐）**
```bash
export SECRET_KEY="your-generated-secret-key-here"
```

**方式 2：配置文件**
```python
# config.py
SECRET_KEY = os.getenv("SECRET_KEY", "fallback-key-for-dev-only")

# 生产环境必须检查
if not os.getenv("SECRET_KEY") and not DEBUG:
    raise ValueError("SECRET_KEY must be set in production")
```

### 数据库安全

#### PostgreSQL 安全配置

**1. 使用强密码**
```sql
ALTER USER asknow_user WITH PASSWORD 'Strong!Password123$';
```

**2. 限制网络访问**

编辑 `/etc/postgresql/14/main/pg_hba.conf`:
```
# 只允许本地连接
host    asknow    asknow_user    127.0.0.1/32    md5
```

**3. 启用 SSL**
```sql
ALTER SYSTEM SET ssl = on;
SELECT pg_reload_conf();
```

#### 数据备份

**设置自动备份脚本**：
```bash
#!/bin/bash
# /usr/local/bin/backup-asknow-db.sh

BACKUP_DIR="/var/backups/asknow"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/asknow_$DATE.sql.gz"

# 创建备份目录
mkdir -p $BACKUP_DIR

# 备份数据库
pg_dump -U asknow_user asknow | gzip > $BACKUP_FILE

# 删除 7 天前的备份
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE"
```

**添加定时任务**：
```bash
sudo crontab -e

# 每天凌晨 3 点备份
0 3 * * * /usr/local/bin/backup-asknow-db.sh
```

### 文件上传安全

#### 严格的文件验证

**后端已实现的安全措施：**

```python
def validate_image_file(file: UploadFile) -> None:
    # 1. 检查 MIME 类型
    if not file.content_type.startswith("image/"):
        raise HTTPException(400, "Only image files allowed")
    
    # 2. 检查文件扩展名
    file_ext = Path(file.filename).suffix.lower()
    if file_ext not in {".jpg", ".jpeg", ".png", ".webp"}:
        raise HTTPException(400, "Invalid file extension")
    
    # 3. 检查文件名安全性
    if ".." in file.filename or "/" in file.filename:
        raise HTTPException(400, "Invalid filename")
    
    # 4. 检查文件大小
    if file_size > 10 * 1024 * 1024:  # 10MB
        raise HTTPException(400, "File too large")
```

#### 存储隔离

```python
# 每个用户的文件存储在独立目录
user_upload_dir = Path("uploads") / str(user_id)
user_upload_dir.mkdir(parents=True, exist_ok=True)

# 使用时间戳命名，防止文件名冲突和目录遍历
safe_filename = f"{int(time.time() * 1000)}{file_ext}"
```

### API 访问限流

#### 使用 slowapi

**安装：**
```bash
pip install slowapi
```

**配置：**
```python
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# 应用到路由
@app.post("/api/register")
@limiter.limit("5/minute")  # 每分钟最多 5 次
async def register(request: Request, ...):
    pass

@app.post("/api/login")
@limiter.limit("10/minute")
async def login(request: Request, ...):
    pass

@app.post("/api/upload")
@limiter.limit("20/hour")  # 每小时最多 20 次
async def upload_image(request: Request, ...):
    pass
```

### CORS 配置

**生产环境应限制来源：**

```python
# config.py
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "*").split(",")

# 生产环境设置
export CORS_ORIGINS="https://your-domain.com,https://www.your-domain.com"
```

---

## 监控和维护

### 日志管理

#### 日志轮转

**使用 logrotate**：

**/etc/logrotate.d/asknow**:
```
/var/log/asknow/*.log {
    daily
    rotate 14
    compress
    delaycompress
    notifempty
    create 0640 www-data www-data
    sharedscripts
    postrotate
        systemctl reload asknow > /dev/null 2>&1 || true
    endscript
}
```

#### 日志分析

```bash
# 查看错误日志
grep "ERROR" /var/log/asknow/app.log

# 统计 API 调用次数
grep "/api/" /var/log/nginx/access.log | wc -l

# 查看最慢的请求
grep "took" /var/log/asknow/app.log | sort -k 8 -n | tail -20

# 实时监控
tail -f /var/log/asknow/app.log | grep "ERROR"
```

### 性能监控

#### 使用 Prometheus + Grafana

**安装 Prometheus**：
```bash
# 下载
wget https://github.com/prometheus/prometheus/releases/download/v2.45.0/prometheus-2.45.0.linux-amd64.tar.gz
tar xvfz prometheus-*.tar.gz
cd prometheus-*

# 配置
cat > prometheus.yml <<EOF
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'asknow'
    static_configs:
      - targets: ['localhost:8000']
EOF

# 启动
./prometheus --config.file=prometheus.yml
```

**添加 FastAPI 指标：**
```bash
pip install prometheus-fastapi-instrumentator
```

```python
# main.py
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(...)

# 添加 Prometheus 指标
Instrumentator().instrument(app).expose(app)
```

### 错误追踪

#### 使用 Sentry

**安装：**
```bash
pip install sentry-sdk[fastapi]
```

**配置：**
```python
# main.py
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration

sentry_sdk.init(
    dsn="your-sentry-dsn",
    integrations=[FastApiIntegration()],
    traces_sample_rate=1.0,
    environment="production"
)
```

### 健康检查

**添加健康检查端点：**

```python
@app.get("/health")
async def health_check(db: AsyncSession = Depends(get_db)) -> Dict[str, Any]:
    try:
        # 检查数据库连接
        await db.execute(select(1))
        db_status = "healthy"
    except Exception:
        db_status = "unhealthy"
    
    return {
        "status": "ok" if db_status == "healthy" else "error",
        "database": db_status,
        "timestamp": int(time.time() * 1000)
    }
```

**配置监控脚本：**
```bash
#!/bin/bash
# /usr/local/bin/check-asknow-health.sh

HEALTH_URL="http://localhost:8000/health"
RESPONSE=$(curl -s $HEALTH_URL)
STATUS=$(echo $RESPONSE | jq -r '.status')

if [ "$STATUS" != "ok" ]; then
    echo "Health check failed: $RESPONSE"
    # 发送告警（邮件、钉钉、Slack 等）
    systemctl restart asknow
fi
```

**添加定时检查：**
```bash
# 每 5 分钟检查一次
*/5 * * * * /usr/local/bin/check-asknow-health.sh
```

### 备份策略

#### 完整备份计划

```bash
#!/bin/bash
# /usr/local/bin/backup-asknow-full.sh

BACKUP_DIR="/var/backups/asknow"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="asknow_full_$DATE"

mkdir -p $BACKUP_DIR/$BACKUP_NAME

# 备份数据库
pg_dump -U asknow_user asknow | gzip > $BACKUP_DIR/$BACKUP_NAME/database.sql.gz

# 备份上传的文件
tar czf $BACKUP_DIR/$BACKUP_NAME/uploads.tar.gz -C /var/www/asknow/backend uploads/

# 备份配置文件
cp /etc/systemd/system/asknow.service $BACKUP_DIR/$BACKUP_NAME/
cp /etc/nginx/sites-available/asknow $BACKUP_DIR/$BACKUP_NAME/
cp /var/www/asknow/backend/.env $BACKUP_DIR/$BACKUP_NAME/

# 创建备份清单
cat > $BACKUP_DIR/$BACKUP_NAME/README.txt <<EOF
AskNow Full Backup
Date: $(date)
Database: database.sql.gz
Uploads: uploads.tar.gz
Config: asknow.service, asknow (nginx)
Environment: .env
EOF

# 打包
cd $BACKUP_DIR
tar czf $BACKUP_NAME.tar.gz $BACKUP_NAME
rm -rf $BACKUP_NAME

# 删除 30 天前的备份
find $BACKUP_DIR -name "asknow_full_*.tar.gz" -mtime +30 -delete

echo "Full backup completed: $BACKUP_DIR/$BACKUP_NAME.tar.gz"
```

**每周完整备份：**
```bash
# 每周日凌晨 2 点
0 2 * * 0 /usr/local/bin/backup-asknow-full.sh
```

---

## 常见问题

### 服务启动失败

**Q: Gunicorn 启动失败，提示 "ModuleNotFoundError"**
```
A: 1. 检查虚拟环境是否激活
   2. 检查依赖是否完整安装
   3. 检查 PYTHONPATH 环境变量
   4. 使用绝对路径启动
```

**Q: 端口被占用**
```
A: 1. 查找占用进程：sudo lsof -i :8000
   2. 结束进程：sudo kill -9 <PID>
   3. 或更改端口配置
```

### 数据库连接问题

**Q: PostgreSQL 连接被拒绝**
```
A: 1. 检查 PostgreSQL 服务状态：sudo systemctl status postgresql
   2. 检查监听地址：sudo netstat -nltp | grep 5432
   3. 检查 pg_hba.conf 配置
   4. 检查防火墙规则
```

**Q: 数据库连接数过多**
```
A: 1. 检查连接池配置
   2. 增加 PostgreSQL max_connections：
      # /etc/postgresql/14/main/postgresql.conf
      max_connections = 200
   3. 重启 PostgreSQL：sudo systemctl restart postgresql
```

### Nginx 问题

**Q: 502 Bad Gateway**
```
A: 1. 检查后端服务是否运行：sudo systemctl status asknow
   2. 检查端口配置是否匹配
   3. 查看 Nginx 错误日志：sudo tail -f /var/log/nginx/error.log
   4. 检查 SELinux 设置（CentOS）
```

**Q: WebSocket 连接失败**
```
A: 1. 检查 Nginx 配置中的 WebSocket 支持
   2. 确认 proxy_http_version 1.1
   3. 确认 Upgrade 和 Connection 头设置正确
   4. 检查超时设置
```

### SSL 证书问题

**Q: Let's Encrypt 证书续期失败**
```
A: 1. 检查域名 DNS 是否正确
   2. 确认 80 端口可访问
   3. 检查 certbot 日志：sudo cat /var/log/letsencrypt/letsencrypt.log
   4. 手动续期：sudo certbot renew
```

---

## 部署检查清单

### 部署前

- [ ] 生成强 SECRET_KEY
- [ ] 配置生产数据库（PostgreSQL）
- [ ] 设置 DEBUG=False
- [ ] 配置 CORS 来源限制
- [ ] 创建签名密钥
- [ ] 配置生产环境服务器地址
- [ ] 测试所有 API 端点
- [ ] 进行压力测试

### 部署时

- [ ] 配置系统服务（systemd/supervisor）
- [ ] 配置 Nginx 反向代理
- [ ] 配置 SSL 证书
- [ ] 设置文件上传目录权限
- [ ] 配置防火墙规则
- [ ] 设置日志轮转
- [ ] 配置备份脚本

### 部署后

- [ ] 验证健康检查端点
- [ ] 测试 WebSocket 连接
- [ ] 测试文件上传
- [ ] 检查日志输出
- [ ] 设置监控告警
- [ ] 测试备份恢复
- [ ] 文档更新

---

## 相关文档

- [技术架构详解](ARCHITECTURE.md)
- [API 参考文档](API_REFERENCE.md)
- [开发指南](DEVELOPMENT_GUIDE.md)

