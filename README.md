# AskNow - 实时问答辅导系统

<div align="center">

**AskNow**

一个基于 Android 客户端和 Python 后端的实时问答辅导平台

[技术架构详解](docs/ARCHITECTURE.md)• [API 参考文档](docs/API_REFERENCE.md) • [开发指南](docs/DEVELOPMENT_GUIDE.md) • [部署指南](docs/DEPLOYMENT.md)

</div>

---

## 📖 项目简介

AskNow 是一个实时问答辅导应用系统，支持学生提问和老师在线解答。

系统采用 **Android 客户端 + Python 后端** 的架构，通过 HTTP + WebSocket 混合模式实现实时通信，为学生和老师提供流畅的在线辅导体验。

## ✨ 功能特性

### 👨‍🎓 学生端功能
- **提问功能**：支持文字描述和图片上传（多图支持）
- **问题管理**：查看我的问题列表（待解答、进行中、已关闭）
- **实时聊天**：与老师进行实时文字和图片交流
- **消息提醒**：实时接收老师的回复通知
- **图片预览**：支持图片点击放大、缩放查看

### 👨‍🏫 老师端功能
- **问题广场**：实时查看待解答问题列表
- **接单功能**：选择并接受感兴趣的问题
- **辅导管理**：管理我的辅导列表（进行中、已完成）
- **实时解答**：与学生进行实时沟通和答疑
- **问题关闭**：完成辅导后关闭问题

### 🚀 技术亮点
- **实时通信**：HTTP + WebSocket 混合模式，服务器实时推送
- **离线缓存**：本地数据库缓存，支持离线浏览
- **图片上传**：支持拍照和相册选择，自动压缩优化
- **JWT 认证**：安全的用户身份验证机制
- **Material Design**：现代化 UI 设计，支持动态主题
- **MVVM 架构**：清晰的代码架构，易于维护和扩展

## 🏗️ 技术栈

### Android 客户端
- **开发语言**：Java
- **架构模式**：MVVM + Hilt (依赖注入)
- **网络层**：Retrofit (HTTP) + OkHttp (WebSocket)
- **数据库**：Room (SQLite)
- **UI 框架**：Material Design 3 (动态主题)

### Python 后端
- **Web 框架**：FastAPI + uvicorn (异步)
- **数据库**：SQLite / PostgreSQL + SQLAlchemy (async)
- **实时推送**：WebSocket (FastAPI)
- **身份验证**：JWT + bcrypt

## 🚀 快速开始

### 环境要求

**Android 客户端：**
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 33+
- Gradle 8.13+

**Python 后端：**
- Python 3.8 或更高版本
- pip 或 pipenv

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/dowdah/realtime-qa-android.git
cd realtime-qa-android
```

#### 2. 启动后端服务

```bash
cd backend

pipenv install

pipenv run python main.py
```

后端服务将在 `http://0.0.0.0:8000` 启动

#### 3. 配置并运行 Android 应用

**使用 Android 模拟器：**

后端会自动在模拟器的 `http://10.0.2.2:8000` 上可用（Debug 构建）

**使用真实设备：**

1. 确保设备和电脑在同一局域网
2. 查找电脑的局域网 IP 地址：
   ```bash
   # macOS/Linux
   ifconfig | grep "inet "
   
   # Windows
   ipconfig
   ```
3. 修改 `AskNow/app/build.gradle.kts` 中的 release 配置：
   ```kotlin
   release {
       buildConfigField("String", "BASE_URL", "\"http://YOUR_LOCAL_IP:8000/\"")
   }
   ```
   将 `YOUR_LOCAL_IP` 替换为你电脑的局域网IP地址

**编译运行：**

```bash
cd AskNow
./gradlew assembleDebug  # 编译 Debug 版本
./gradlew installDebug   # 安装到设备
```

或直接在 Android Studio 中点击 Run 按钮运行。

## 📚 文档

详细的技术文档和指南：

- **[📐 技术架构详解](docs/ARCHITECTURE.md)**
  - MVVM 架构模式和数据流
  - Android 端组件设计
  - Python 后端架构
  - 数据库设计和 WebSocket 通信
  - 系统交互流程图

- **[📡 API 参考文档](docs/API_REFERENCE.md)**
  - 完整的 REST API 接口说明
  - WebSocket 消息格式
  - 请求/响应示例
  - 错误处理和状态码

- **[🛠️ 开发指南](docs/DEVELOPMENT_GUIDE.md)**
  - 环境配置和项目结构
  - Android 开发最佳实践
  - Python 后端开发指南
  - 代码规范和测试指南
  - 调试技巧和常见问题

- **[🚀 部署指南](docs/DEPLOYMENT.md)**
  - 生产环境配置
  - 服务器部署步骤
  - Nginx 反向代理配置
  - SSL 证书和安全配置
  - 监控和维护

## 🧪 测试

### Android 单元测试

```bash
cd AskNow
./gradlew test
```

### 后端测试

```bash
cd backend
pip install -r test_requirements.txt
./run_tests.sh
```

或手动运行：
```bash
python test_api.py
```

## 📦 构建发布

### Android Release 构建

```bash
cd AskNow
./gradlew assembleRelease
```

生成的 APK 位于：`AskNow/app/build/outputs/apk/release/app-release.apk`

详细的发布和部署说明请参考 **[部署指南](docs/DEPLOYMENT.md)**。

## 🐛 常见问题

### Android 连接后端失败
1. 检查后端服务是否正常运行
2. 模拟器使用 `10.0.2.2` 而非 `localhost`
3. 真机确保与电脑在同一网络
4. 检查防火墙是否阻止了 8000 端口

### WebSocket 连接问题
1. 检查网络状态
2. 应用已实现自动重连机制
3. 查看后端日志排查问题

### 图片上传失败
1. 检查图片大小（最大 10MB）
2. 检查文件格式（支持 jpg, jpeg, png, webp）
3. 检查 `backend/uploads/` 目录权限

更多问题和解决方案请参考 **[开发指南](docs/DEVELOPMENT_GUIDE.md#常见问题)**。

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 GitHub Issue

---
