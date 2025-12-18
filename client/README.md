# 邮件系统 Android 客户端

## 项目简介

这是一个基于 Kotlin 和 Jetpack Compose 开发的邮件系统 Android 客户端，采用 MVVM 架构。

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构**: MVVM
- **网络请求**: Retrofit + OkHttp
- **异步处理**: Kotlin Coroutines
- **本地存储**: DataStore
- **导航**: Navigation Compose
- **依赖注入**: 无（简化实现）

## 功能特性

### 普通用户功能
- ✅ 用户注册
- ✅ 用户登录
- ✅ 收件箱列表
- ✅ 邮件详情查看
- ✅ 删除邮件
- ✅ 用户登出

### 管理员功能
- ✅ 查看所有用户列表
- ✅ 删除用户
- ✅ 群发邮件给所有用户

## 项目结构

```
app/src/main/java/com/mailsystem/
├── MainActivity.kt                 # 主活动
├── data/
│   ├── model/                      # 数据模型
│   │   ├── User.kt
│   │   └── Mail.kt
│   ├── api/                        # 网络接口
│   │   ├── ApiService.kt
│   │   └── RetrofitClient.kt
│   ├── local/                      # 本地存储
│   │   └── UserPreferences.kt
│   └── repository/                 # 数据仓库
│       └── MailRepository.kt
├── ui/
│   ├── screen/                     # 界面
│   │   ├── LoginScreen.kt
│   │   ├── RegisterScreen.kt
│   │   ├── InboxScreen.kt
│   │   ├── MailDetailScreen.kt
│   │   └── AdminScreen.kt
│   ├── viewmodel/                  # ViewModel
│   │   ├── AuthViewModel.kt
│   │   ├── MailViewModel.kt
│   │   └── AdminViewModel.kt
│   └── theme/                      # 主题
│       └── Theme.kt
```

## 配置说明

### 后端地址配置

在 `RetrofitClient.kt` 中配置后端服务器地址：

```kotlin
// Android 模拟器访问本机
private const val BASE_URL = "http://10.0.2.2:8000/"

// 真机访问局域网（替换为你的电脑 IP）
// private const val BASE_URL = "http://192.168.1.100:8000/"
```

### 网络权限

已在 `AndroidManifest.xml` 中配置：
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 编译和运行

### 前置条件

1. 安装 Android Studio (推荐最新版本)
2. JDK 17 或更高版本
3. Android SDK (compileSdk 34, minSdk 24)

### 步骤

1. 用 Android Studio 打开 `client` 目录

2. 等待 Gradle 同步完成

3. 确保后端服务器正在运行：
   ```bash
   cd ../
   uvicorn app.main:app --reload
   ```

4. 运行应用：
   - 点击 Android Studio 工具栏的 Run 按钮
   - 或使用快捷键 `Shift + F10`
   - 选择模拟器或真机设备

### 使用模拟器

- 推荐创建 API 30+ 的模拟器
- 确保模拟器可以访问 `10.0.2.2:8000`（这是模拟器访问主机的特殊地址）

### 使用真机

1. 启用开发者模式和 USB 调试
2. 确保手机和电脑在同一局域网
3. 修改 `RetrofitClient.kt` 中的 BASE_URL 为电脑的局域网 IP
4. 通过 USB 连接或无线调试连接设备

## 测试账号

可以通过应用内注册功能创建账号，或使用后端已创建的账号：

- 普通用户: `user1` / `123456`
- 管理员: `admin1` / `123` (需要后端已创建)

## 主要界面

### 1. 登录界面
- 输入用户名和密码
- 登录成功后根据角色跳转到收件箱

### 2. 注册界面
- 输入用户名和密码
- 可选择注册为管理员或普通用户

### 3. 收件箱界面
- 显示邮件列表
- 刷新邮件
- 点击邮件查看详情
- 管理员可进入管理面板

### 4. 邮件详情界面
- 显示邮件完整内容
- 删除邮件功能

### 5. 管理面板（仅管理员）
- 查看所有用户
- 删除用户
- 群发邮件

## 依赖库

主要依赖：
```kotlin
// Compose
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.7.6")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

// Retrofit
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## 常见问题

### 1. 无法连接到后端

- 检查后端是否正在运行
- 模拟器使用 `10.0.2.2`，真机使用局域网 IP
- 检查防火墙设置
- 确保 `usesCleartextTraffic="true"` 已在 AndroidManifest.xml 中设置

### 2. Gradle 同步失败

- 检查网络连接
- 更新 Gradle 版本
- 清除 Gradle 缓存：`./gradlew clean`

### 3. 编译错误

- 确保使用 JDK 17+
- File -> Invalidate Caches / Restart

## 开发说明

### 添加新功能

1. 在 `data/model` 添加数据模型
2. 在 `data/api/ApiService.kt` 添加 API 接口
3. 在 `data/repository` 添加仓库方法
4. 在 `ui/viewmodel` 添加或更新 ViewModel
5. 在 `ui/screen` 创建或更新界面

### 状态管理

使用 Kotlin Flow 和 StateFlow 进行状态管理：
```kotlin
val state: StateFlow<State> = _state
```

## 许可证

本项目仅用于教学目的。
