# Aurora 开发指南

## 环境要求

### 必需软件

- **Android Studio**：Arctic Fox (2020.3.1) 或更高版本
- **JDK**：JDK 11 或更高版本
- **Android SDK**：
  - 最低 SDK：26 (Android 8.0)
  - 目标 SDK：36 (Android 16)
  - 编译 SDK：36
- **Kotlin**：1.9+ (项目已配置)

### 推荐配置

- **内存**：至少 8GB RAM（推荐 16GB）
- **存储**：至少 10GB 可用空间
- **操作系统**：Windows 10/11, macOS 10.14+, 或 Linux

## 项目设置

### 1. 克隆项目

```bash
git clone https://github.com/QuickerStudio/Aurora.git
cd Aurora
```

### 2. 打开项目

1. 启动 Android Studio
2. 选择 "Open an Existing Project"
3. 导航到 Aurora 项目目录
4. 点击 "OK"

### 3. Gradle 同步

项目使用国内镜像源加速构建：

**镜像配置**（已在 [settings.gradle.kts](../settings.gradle.kts) 中配置）：
1. 腾讯云 - 平均延迟 4ms
2. 阿里云 - 平均延迟 14ms
3. 华为云 - 平均延迟 27ms

首次同步可能需要几分钟，请耐心等待。

### 4. 运行项目

1. 连接 Android 设备或启动模拟器
2. 点击工具栏的 "Run" 按钮（绿色三角形）
3. 选择目标设备
4. 等待应用安装和启动

## 项目结构

```
Aurora/
├── app/                          # 主应用模块
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/ai/wallpaper/aurora/
│   │   │   │   ├── activity/     # Activity 组件
│   │   │   │   ├── broadcast/    # 广播接收器
│   │   │   │   ├── data/         # 数据模型和管理
│   │   │   │   ├── media/        # 媒体处理
│   │   │   │   ├── service/      # 服务组件
│   │   │   │   ├── ui/           # UI 组件和主题
│   │   │   │   ├── utils/        # 工具类
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/              # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                 # 单元测试
│   └── build.gradle.kts          # 应用构建配置
├── assets/                       # 项目资产
├── docs/                         # 文档
├── gradle/                       # Gradle 包装器
├── scripts/                      # 实用脚本
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts           # Gradle 设置
└── README.md                     # 项目说明
```

## 核心模块说明

### 1. Activity 层

**MainActivity.kt**
- 主界面和导航
- 壁纸历史记录展示
- 本地视频库浏览
- 主题切换

**SettingsActivity.kt**
- 设置界面
- 主题配置
- 功能开关

**FullscreenVideoActivity.kt**
- 全屏视频播放
- 解锁跳转目标

### 2. Service 层

**VideoLiveWallpaperService.kt**
- 动态壁纸服务
- 独立进程运行（`:wallpaper`）
- 视频播放和渲染
- 广播接收和响应

**UnlockWallpaperService.kt**
- 解锁监听服务
- 触发全屏播放

### 3. Data 层

**WallpaperHistory.kt**
- 历史记录管理
- JSON 序列化/反序列化
- 去重逻辑

### 4. Media 层

**VideoMediaProcessor.kt**
- 播放器池管理
- LRU 缓存策略
- Actor 模式并发控制

**MemoryPressureMonitor.kt**
- 内存压力监控
- 自动资源调整

### 5. Utils 层

**LocalVideoScanner.kt**
- 本地视频扫描
- MediaStore API 集成

**LRUPlayerPool.kt**
- 播放器资源池
- LRU 淘汰策略

**VideoThumbnailCache.kt**
- 视频缩略图缓存

## 开发工作流

### 1. 创建新功能

```bash
# 创建新分支
git checkout -b feature/your-feature-name

# 开发功能
# ...

# 提交更改
git add .
git commit -m "feat: add your feature description"

# 推送到远程
git push origin feature/your-feature-name
```

### 2. 代码规范

**Kotlin 编码规范**：
- 遵循 [Kotlin 官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- 使用 4 空格缩进
- 类名使用 PascalCase
- 函数和变量使用 camelCase
- 常量使用 UPPER_SNAKE_CASE

**Compose 规范**：
- Composable 函数名使用 PascalCase
- 参数使用 camelCase
- 使用 `remember` 和 `rememberSaveable` 管理状态
- 避免在 Composable 中执行耗时操作

### 3. 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
feat: 新功能
fix: 修复 bug
docs: 文档更新
style: 代码格式调整
refactor: 重构
test: 测试相关
chore: 构建/工具相关
```

**示例**：
```bash
git commit -m "feat: add video hot-switching feature"
git commit -m "fix: resolve memory leak in player pool"
git commit -m "docs: update architecture documentation"
```

## 调试技巧

### 1. 日志调试

**使用 Logcat**：
```kotlin
import android.util.Log

private const val TAG = "YourClassName"

Log.d(TAG, "Debug message")
Log.i(TAG, "Info message")
Log.w(TAG, "Warning message")
Log.e(TAG, "Error message", exception)
```

**过滤日志**：
```
# 按标签过滤
adb logcat -s YourClassName

# 按包名过滤
adb logcat | grep "ai.wallpaper.aurora"
```

### 2. 断点调试

1. 在代码行号左侧点击设置断点
2. 点击 "Debug" 按钮（虫子图标）
3. 应用会在断点处暂停
4. 使用调试工具栏控制执行流程

### 3. 布局检查器

1. 运行应用
2. 选择 "Tools" > "Layout Inspector"
3. 选择目标进程
4. 查看 UI 层次结构和属性

### 4. 性能分析

**CPU Profiler**：
1. 选择 "View" > "Tool Windows" > "Profiler"
2. 点击 "CPU" 选项卡
3. 开始录制
4. 执行操作
5. 停止录制并分析

**Memory Profiler**：
1. 选择 "View" > "Tool Windows" > "Profiler"
2. 点击 "Memory" 选项卡
3. 监控内存使用情况
4. 捕获堆转储分析内存泄漏

## 常见问题

### 1. Gradle 同步失败

**问题**：依赖下载失败或超时

**解决方案**：
- 检查网络连接
- 确认镜像源配置正确
- 清理 Gradle 缓存：`./gradlew clean`
- 重新同步项目

### 2. 应用无法安装

**问题**：安装失败或签名错误

**解决方案**：
- 卸载旧版本应用
- 检查设备存储空间
- 确认 USB 调试已启用
- 重启 ADB：`adb kill-server && adb start-server`

### 3. 壁纸服务未启动

**问题**：设置壁纸后服务未运行

**解决方案**：
- 检查权限是否授予
- 查看 Logcat 日志
- 确认 AndroidManifest.xml 配置正确
- 重启设备

### 4. 内存泄漏

**问题**：应用内存持续增长

**解决方案**：
- 使用 Memory Profiler 分析
- 检查播放器是否正确释放
- 确认 Composable 生命周期管理正确
- 查看 `onRelease` 回调是否执行

## 构建和发布

### 1. 构建 Debug 版本

```bash
./gradlew assembleDebug
```

输出位置：`app/build/outputs/apk/debug/app-debug.apk`

### 2. 构建 Release 版本

```bash
./gradlew assembleRelease
```

输出位置：`app/build/outputs/apk/release/app-release.apk`

### 3. 签名配置

在 `app/build.gradle.kts` 中配置签名：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("path/to/keystore.jks")
            storePassword = "your-store-password"
            keyAlias = "your-key-alias"
            keyPassword = "your-key-password"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...
        }
    }
}
```

### 4. 混淆配置

Release 版本已启用 ProGuard/R8 混淆：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

## 测试

### 运行单元测试

```bash
./gradlew test
```

### 运行仪器测试

```bash
./gradlew connectedAndroidTest
```

### 测试覆盖率

```bash
./gradlew jacocoTestReport
```

详细测试指南请参考 [TESTING.md](TESTING.md)。

## 依赖管理

### 更新依赖

1. 检查可用更新：
   ```bash
   ./gradlew dependencyUpdates
   ```

2. 更新 `build.gradle.kts` 中的版本号

3. 同步 Gradle

4. 运行测试确保兼容性

### 主要依赖

- **Jetpack Compose**：UI 框架
- **Material3**：Material Design 组件
- **ExoPlayer**：视频播放
- **Kotlin Coroutines**：异步编程
- **Gson**：JSON 序列化

## 贡献指南

### 1. Fork 项目

在 GitHub 上 Fork 项目到你的账户。

### 2. 创建分支

```bash
git checkout -b feature/your-feature
```

### 3. 提交更改

```bash
git add .
git commit -m "feat: your feature description"
```

### 4. 推送分支

```bash
git push origin feature/your-feature
```

### 5. 创建 Pull Request

在 GitHub 上创建 Pull Request，描述你的更改。

## 资源链接

### 官方文档

- [Android 开发者文档](https://developer.android.com/)
- [Kotlin 官方文档](https://kotlinlang.org/docs/home.html)
- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [ExoPlayer 文档](https://exoplayer.dev/)

### 社区资源

- [Stack Overflow](https://stackoverflow.com/questions/tagged/android)
- [Android Developers Reddit](https://www.reddit.com/r/androiddev/)
- [Kotlin Slack](https://kotlinlang.slack.com/)

## 许可证

**个人使用，禁止商业用途**

Copyright © 2026 QuickerStudio. All rights reserved.

---

**文档版本**：v1.0
**更新日期**：2026-03-08
**维护者**：QuickerStudio 开发团队
