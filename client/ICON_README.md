# Android 图标资源说明

由于项目是代码生成的，缺少应用图标资源。

## 临时解决方案

有两个选择：

### 方案1：使用 Android Studio 生成图标（推荐）

1. 在 Android Studio 中右键点击 `app/src/main/res` 目录
2. 选择 `New` -> `Image Asset`
3. 选择 `Launcher Icons (Adaptive and Legacy)`
4. 使用默认图标或上传自定义图片
5. 点击 `Next` -> `Finish`

### 方案2：注释掉图标引用

修改 `AndroidManifest.xml`，将：
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

改为：
```xml
android:icon="@android:drawable/sym_def_app_icon"
```

## 其他可能的问题

1. **Gradle 同步失败**：点击 Android Studio 顶部的 "Sync Project with Gradle Files"
2. **JDK 版本**：确保使用 JDK 17 或更高版本
3. **网络问题**：首次构建需要下载依赖，确保网络连接
4. **清理缓存**：`Build` -> `Clean Project`，然后 `Build` -> `Rebuild Project`

## 具体错误信息

如果仍有问题，请提供：
- Android Studio 的 Build 窗口错误信息
- Logcat 中的错误日志
- Problems 窗口的具体错误
