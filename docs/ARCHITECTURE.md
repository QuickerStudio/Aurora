# Aurora 架构设计文档

## 概述

Aurora 是一款基于 Kotlin 和 Jetpack Compose 开发的 Android 视频动态壁纸应用，采用现代化的架构设计，实现了高性能、低资源消耗的视频壁纸播放。

## 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      主进程                                   │
│              (ai.wallpaper.aurora)                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  MainActivity │  │  Settings    │  │  Fullscreen  │      │
│  │              │  │  Activity    │  │  Activity    │      │
│  └──────┬───────┘  └──────────────┘  └──────────────┘      │
│         │                                                    │
│         ├─ UI Layer (Jetpack Compose)                       │
│         ├─ Data Layer (WallpaperHistory)                    │
│         ├─ Media Layer (VideoMediaProcessor)                │
│         └─ Utils (LocalVideoScanner, LRUPlayerPool)         │
│                                                              │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ 跨进程通信
                       │ (文件 + 广播)
                       │
┌──────────────────────┴───────────────────────────────────────┐
│                    壁纸进程                                    │
│            (ai.wallpaper.aurora:wallpaper)                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │      VideoLiveWallpaperService                       │   │
│  │                                                      │   │
│  │  ┌────────────────────────────────────────────┐     │   │
│  │  │          VideoEngine                       │     │   │
│  │  │  - MediaPlayer 管理                        │     │   │
│  │  │  - Surface 渲染                            │     │   │
│  │  │  - 广播接收器                              │     │   │
│  │  └────────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. 主界面层 (MainActivity)

**职责**：
- 用户界面展示和交互
- 壁纸历史记录管理
- 本地视频库浏览
- 主题切换和设置

**关键特性**：
- 使用 Jetpack Compose 构建响应式 UI
- Material3 设计规范
- 双列网格布局（历史记录）
- 水平滚动列表（本地视频库）

**核心代码位置**：
- [MainActivity.kt](../app/src/main/java/ai/wallpaper/aurora/MainActivity.kt)

### 2. 壁纸服务层 (VideoLiveWallpaperService)

**职责**：
- 视频壁纸渲染
- 接收和处理切换指令
- 音量控制
- 生命周期管理

**关键特性**：
- 独立进程运行（`:wallpaper`）
- 广播接收器监听切换指令
- MediaPlayer 视频播放
- Surface 渲染管理

**核心代码位置**：
- [VideoLiveWallpaperService.kt](../app/src/main/java/ai/wallpaper/aurora/service/VideoLiveWallpaperService.kt)

### 3. 数据管理层 (WallpaperHistory)

**职责**：
- 壁纸历史记录持久化
- 历史记录去重
- JSON 序列化/反序列化

**数据结构**：
```kotlin
data class WallpaperHistoryItem(
    val id: String,
    val videoUri: String,
    val timestamp: Long,
    val displayName: String?
)
```

**核心代码位置**：
- [WallpaperHistory.kt](../app/src/main/java/ai/wallpaper/aurora/data/WallpaperHistory.kt)

### 4. 媒体处理层 (VideoMediaProcessor)

**职责**：
- ExoPlayer 播放器池管理
- LRU 缓存策略
- 视频裁剪（前 10 秒）
- 内存压力响应

**关键特性**：
- Actor 模式处理并发
- 专用线程执行播放器操作
- 自动淘汰最久未使用的播放器
- 内存上限控制（45MB）

**核心代码位置**：
- [VideoMediaProcessor.kt](../app/src/main/java/ai/wallpaper/aurora/media/VideoMediaProcessor.kt)
- [MemoryPressureMonitor.kt](../app/src/main/java/ai/wallpaper/aurora/media/MemoryPressureMonitor.kt)

### 5. 工具层

#### LocalVideoScanner
**职责**：扫描设备本地视频文件

**特性**：
- MediaStore API 集成
- 支持多种视频格式（MP4, MKV, AVI, MOV, WebM）
- 增量加载
- 缩略图生成

**核心代码位置**：
- [LocalVideoScanner.kt](../app/src/main/java/ai/wallpaper/aurora/utils/LocalVideoScanner.kt)

#### LRUPlayerPool
**职责**：播放器资源池管理

**特性**：
- LRU 淘汰策略
- 最大播放器数量限制
- 自动释放资源

**核心代码位置**：
- [LRUPlayerPool.kt](../app/src/main/java/ai/wallpaper/aurora/utils/LRUPlayerPool.kt)

## 设计模式

### 1. 进程隔离模式

**目的**：提高稳定性和资源隔离

**实现**：
```xml
<service
    android:name=".service.VideoLiveWallpaperService"
    android:process=":wallpaper"
    android:permission="android.permission.BIND_WALLPAPER">
</service>
```

**优势**：
- 主应用崩溃不影响壁纸
- 壁纸服务崩溃不影响主应用
- 资源隔离，互不干扰

### 2. 观察者模式（广播机制）

**目的**：实现跨进程通信

**实现**：
- 主进程发送广播：`VIDEO_PATH_UPDATED_ACTION`
- 壁纸进程接收广播并响应

**优势**：
- 解耦主进程和壁纸进程
- 实时响应
- 无需轮询

### 3. Actor 模式（并发控制）

**目的**：安全处理并发播放器操作

**实现**：
```kotlin
private val actor = CoroutineScope(processingContext).actor<Message> {
    for (msg in channel) {
        when (msg) {
            is Message.GetOrCreate -> handleGetOrCreate(msg)
            is Message.Release -> handleRelease(msg)
            is Message.ReleaseAll -> handleReleaseAll(msg)
        }
    }
}
```

**优势**：
- 消息队列处理
- 无锁设计
- 线程安全

### 4. LRU 缓存策略

**目的**：自动管理播放器资源

**实现**：
- LinkedHashMap 维护访问顺序
- 超过上限自动淘汰最久未使用的播放器

**优势**：
- 自动资源管理
- 内存可控
- 性能优化

## 数据流

### 视频切换流程

```
用户点击视频
    ↓
保存视频 URI 到文件
    ↓
检查壁纸是否已激活
    ↓
┌─────────────┴─────────────┐
│                           │
首次设置                  后续切换
    ↓                         ↓
打开系统壁纸设置          发送广播
    ↓                         ↓
用户手动应用              壁纸服务接收
    ↓                         ↓
壁纸服务启动              读取新路径
    ↓                         ↓
读取视频路径              释放旧播放器
    ↓                         ↓
创建播放器                创建新播放器
    ↓                         ↓
开始播放                  开始播放
    ↓                         ↓
通知主进程                通知主进程
    ↓                         ↓
添加历史记录              添加历史记录
```

### 内存压力响应流程

```
系统内存压力事件
    ↓
MemoryPressureMonitor 接收
    ↓
根据压力级别决策
    ↓
┌─────────────┼─────────────┐
│             │             │
RUNNING_LOW   RUNNING_      UI_HIDDEN
              CRITICAL
    ↓             ↓             ↓
减少到 2 个   释放所有      减少到 1 个
播放器        播放器        播放器
    ↓             ↓             ↓
通知 VideoMediaProcessor
    ↓
异步释放播放器
    ↓
释放完成
```

## 性能优化

### 1. 视频裁剪

**策略**：预览仅加载前 10 秒

**实现**：
```kotlin
val clippingConfiguration = ClippingConfiguration.Builder()
    .setEndPositionMs(10_000)
    .build()
```

**效果**：减少 80% 内存占用

### 2. 缓冲控制

**策略**：限制缓冲时间 2-5 秒

**实现**：
```kotlin
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(2000, 5000, 1000, 1000)
    .build()
```

**效果**：降低内存峰值

### 3. 播放器池管理

**策略**：LRU 策略，最大 3 个播放器

**效果**：
- 理论内存上限：45MB
- 自动资源管理
- 避免 OOM

### 4. 异步操作

**策略**：所有播放器操作在专用线程

**实现**：
```kotlin
private val processingContext = Executors.newSingleThreadExecutor()
    .asCoroutineDispatcher()
```

**效果**：主线程零阻塞

## 安全性

### 1. 广播安全

**Android 13+ 使用 RECEIVER_NOT_EXPORTED**：
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.registerReceiver(
        context,
        receiver,
        intentFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
}
```

### 2. 显式广播

**指定包名，防止泄漏**：
```kotlin
val intent = Intent(VIDEO_PATH_UPDATED_ACTION).apply {
    setPackage(context.packageName)
}
```

### 3. 权限最小化

**仅请求必要权限**：
- `READ_MEDIA_VIDEO` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 及以下)
- `SET_WALLPAPER`

## 兼容性

### Android 版本支持

- **最低版本**：Android 8.0 (API 26)
- **目标版本**：Android 16 (API 36)
- **测试设备**：OPPO PLE110 (Android 16)

### 特殊处理

**Android 13+ 广播安全**：
- 使用 `RECEIVER_NOT_EXPORTED`
- 显式广播

**Android 10+ 媒体访问**：
- 支持 `content://` URI
- MediaStore API

## 技术栈

### 核心技术
- **语言**：Kotlin 1.9+
- **UI 框架**：Jetpack Compose + Material3
- **视频播放**：
  - ExoPlayer（预览）
  - MediaPlayer（壁纸）
- **并发**：Kotlin Coroutines
- **存储**：JSON 文件

### 依赖库
- AndroidX Core KTX
- AndroidX Lifecycle
- Jetpack Compose BOM
- ExoPlayer
- Gson

## 未来扩展

### 可扩展点

1. **播放列表功能**
   - 基于现有热切换技术
   - 定时自动切换

2. **云同步**
   - 历史记录云端备份
   - 跨设备同步

3. **智能推荐**
   - 基于时间、地点推荐
   - 场景化壁纸

4. **主题联动**
   - 壁纸与应用主题同步
   - 动态颜色提取

---

**文档版本**：v1.0
**更新日期**：2026-03-08
**维护者**：QuickerStudio 开发团队
