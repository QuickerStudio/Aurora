# Aurora API 参考文档

## 概述

本文档提供 Aurora 核心 API 和组件的详细参考，包括公开接口、数据结构和使用示例。

## 核心服务

### VideoLiveWallpaperService

**包名**：`ai.wallpaper.aurora.service`

**描述**：动态壁纸服务，负责视频播放和渲染。

#### 公开方法

##### setToWallPaper

```kotlin
companion object {
    fun setToWallPaper(context: Context)
}
```

**描述**：打开系统壁纸设置，让用户选择 Aurora 作为壁纸。

**参数**：
- `context`: Context - 应用上下文

**使用示例**：
```kotlin
VideoLiveWallpaperService.setToWallPaper(context)
```

##### notifyVideoPathChanged

```kotlin
companion object {
    fun notifyVideoPathChanged(context: Context)
}
```

**描述**：发送广播通知壁纸服务切换视频（热切换）。

**参数**：
- `context`: Context - 应用上下文

**使用示例**：
```kotlin
// 保存新视频路径后
saveVideoUri(context, videoUri)
// 通知壁纸服务切换
VideoLiveWallpaperService.notifyVideoPathChanged(context)
```

##### isAuroraWallpaperActive

```kotlin
fun isAuroraWallpaperActive(context: Context): Boolean
```

**描述**：检查 Aurora 是否为当前活动壁纸。

**参数**：
- `context`: Context - 应用上下文

**返回值**：
- `Boolean` - true 表示 Aurora 已激活，false 表示未激活

**使用示例**：
```kotlin
if (isAuroraWallpaperActive(context)) {
    // 使用热切换
    VideoLiveWallpaperService.notifyVideoPathChanged(context)
} else {
    // 打开系统设置
    VideoLiveWallpaperService.setToWallPaper(context)
}
```

#### 广播 Action

##### VIDEO_PATH_UPDATED_ACTION

```kotlin
const val VIDEO_PATH_UPDATED_ACTION = "ai.wallpaper.aurora.VIDEO_PATH_UPDATED"
```

**描述**：视频路径更新广播，用于触发热切换。

##### VIDEO_PARAMS_CONTROL_ACTION

```kotlin
const val VIDEO_PARAMS_CONTROL_ACTION = "ai.wallpaper.aurora.VIDEO_PARAMS_CONTROL"
```

**描述**：视频参数控制广播，用于音量控制等。

## 数据管理

### WallpaperHistory

**包名**：`ai.wallpaper.aurora.data`

**描述**：壁纸历史记录管理。

#### 数据结构

##### WallpaperHistoryItem

```kotlin
data class WallpaperHistoryItem(
    val id: String,
    val videoUri: String,
    val timestamp: Long,
    val displayName: String?
)
```

**字段说明**：
- `id`: String - 唯一标识符（时间戳字符串）
- `videoUri`: String - 视频 URI
- `timestamp`: Long - 添加时间戳（毫秒）
- `displayName`: String? - 显示名称（可选）

#### 公开方法

##### loadHistory

```kotlin
fun loadHistory(context: Context): List<WallpaperHistoryItem>
```

**描述**：加载历史记录。

**参数**：
- `context`: Context - 应用上下文

**返回值**：
- `List<WallpaperHistoryItem>` - 历史记录列表（按时间倒序）

**使用示例**：
```kotlin
val history = WallpaperHistory.loadHistory(context)
history.forEach { item ->
    println("${item.displayName}: ${item.videoUri}")
}
```

##### addHistory

```kotlin
fun addHistory(
    context: Context,
    videoUri: Uri,
    displayName: String? = null
)
```

**描述**：添加历史记录（自动去重）。

**参数**：
- `context`: Context - 应用上下文
- `videoUri`: Uri - 视频 URI
- `displayName`: String? - 显示名称（可选）

**使用示例**：
```kotlin
WallpaperHistory.addHistory(
    context = context,
    videoUri = Uri.parse("content://media/video/123"),
    displayName = "my_video.mp4"
)
```

##### deleteHistory

```kotlin
fun deleteHistory(context: Context, id: String)
```

**描述**：删除指定历史记录。

**参数**：
- `context`: Context - 应用上下文
- `id`: String - 历史记录 ID

**使用示例**：
```kotlin
WallpaperHistory.deleteHistory(context, historyItem.id)
```

##### clearHistory

```kotlin
fun clearHistory(context: Context)
```

**描述**：清空所有历史记录。

**参数**：
- `context`: Context - 应用上下文

**使用示例**：
```kotlin
WallpaperHistory.clearHistory(context)
```

## 媒体处理

### VideoMediaProcessor

**包名**：`ai.wallpaper.aurora.media`

**描述**：视频播放器池管理，使用 LRU 策略和 Actor 模式。

#### 构造函数

```kotlin
class VideoMediaProcessor(
    private val context: Context,
    private val maxPlayerCount: Int = 3,
    private val logger: Logger = AndroidLogger
)
```

**参数**：
- `context`: Context - 应用上下文
- `maxPlayerCount`: Int - 最大播放器数量（默认 3）
- `logger`: Logger - 日志接口（默认 AndroidLogger）

#### 公开方法

##### getOrCreatePlayer

```kotlin
suspend fun getOrCreatePlayer(uriKey: String, videoUri: Uri): ExoPlayer
```

**描述**：获取或创建播放器（异步）。

**参数**：
- `uriKey`: String - URI 键（通常是 URI 字符串）
- `videoUri`: Uri - 视频 URI

**返回值**：
- `ExoPlayer` - ExoPlayer 实例

**使用示例**：
```kotlin
lifecycleScope.launch {
    val player = videoProcessor.getOrCreatePlayer(
        uriKey = video.uri.toString(),
        videoUri = video.uri
    )
    playerView.player = player
}
```

##### releasePlayer

```kotlin
suspend fun releasePlayer(uriKey: String)
```

**描述**：释放指定播放器（异步）。

**参数**：
- `uriKey`: String - URI 键

**使用示例**：
```kotlin
lifecycleScope.launch {
    videoProcessor.releasePlayer(video.uri.toString())
}
```

##### releaseAllPlayers

```kotlin
suspend fun releaseAllPlayers()
```

**描述**：释放所有播放器（异步）。

**使用示例**：
```kotlin
lifecycleScope.launch {
    videoProcessor.releaseAllPlayers()
}
```

##### trimToSize

```kotlin
fun trimToSize(targetSize: Int)
```

**描述**：调整播放器池大小（同步，用于内存压力响应）。

**参数**：
- `targetSize`: Int - 目标大小

**使用示例**：
```kotlin
// 内存压力时减少到 2 个播放器
videoProcessor.trimToSize(2)
```

##### clearAll

```kotlin
fun clearAll()
```

**描述**：立即清空所有播放器（同步，用于紧急情况）。

**使用示例**：
```kotlin
override fun onLowMemory() {
    super.onLowMemory()
    videoProcessor.clearAll()
}
```

### MemoryPressureMonitor

**包名**：`ai.wallpaper.aurora.media`

**描述**：内存压力监控，自动调整播放器数量。

#### 构造函数

```kotlin
class MemoryPressureMonitor(
    private val processor: VideoMediaProcessor
)
```

**参数**：
- `processor`: VideoMediaProcessor - 视频处理器实例

#### 公开方法

##### register

```kotlin
fun register(context: Context)
```

**描述**：注册内存压力监听器。

**参数**：
- `context`: Context - 应用上下文

**使用示例**：
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var memoryMonitor: MemoryPressureMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryMonitor = MemoryPressureMonitor(videoProcessor)
        memoryMonitor.register(this)
    }
}
```

##### unregister

```kotlin
fun unregister(context: Context)
```

**描述**：取消注册内存压力监听器。

**参数**：
- `context`: Context - 应用上下文

**使用示例**：
```kotlin
override fun onDestroy() {
    super.onDestroy()
    memoryMonitor.unregister(this)
}
```

## 工具类

### LocalVideoScanner

**包名**：`ai.wallpaper.aurora.utils`

**描述**：本地视频扫描工具。

#### 数据结构

##### LocalVideo

```kotlin
data class LocalVideo(
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val size: Long
)
```

**字段说明**：
- `uri`: Uri - 视频 URI
- `displayName`: String - 显示名称
- `duration`: Long - 时长（毫秒）
- `size`: Long - 文件大小（字节）

#### 公开方法

##### scanVideos

```kotlin
suspend fun scanVideos(
    context: Context,
    limit: Int = 50,
    offset: Int = 0
): List<LocalVideo>
```

**描述**：扫描本地视频（异步）。

**参数**：
- `context`: Context - 应用上下文
- `limit`: Int - 每次加载数量（默认 50）
- `offset`: Int - 偏移量（默认 0）

**返回值**：
- `List<LocalVideo>` - 视频列表

**使用示例**：
```kotlin
lifecycleScope.launch {
    val videos = LocalVideoScanner.scanVideos(
        context = context,
        limit = 50,
        offset = 0
    )
    // 显示视频列表
}
```

### LRUPlayerPool

**包名**：`ai.wallpaper.aurora.utils`

**描述**：LRU 播放器资源池（已被 VideoMediaProcessor 替代，保留用于兼容）。

#### 构造函数

```kotlin
class LRUPlayerPool(
    private val maxSize: Int = 3
)
```

**参数**：
- `maxSize`: Int - 最大播放器数量（默认 3）

#### 公开方法

##### get

```kotlin
fun get(key: String): ExoPlayer?
```

**描述**：获取播放器。

**参数**：
- `key`: String - 键

**返回值**：
- `ExoPlayer?` - 播放器实例或 null

##### put

```kotlin
fun put(key: String, player: ExoPlayer)
```

**描述**：添加播放器。

**参数**：
- `key`: String - 键
- `player`: ExoPlayer - 播放器实例

##### remove

```kotlin
fun remove(key: String)
```

**描述**：移除播放器。

**参数**：
- `key`: String - 键

##### clear

```kotlin
fun clear()
```

**描述**：清空所有播放器。

## 主题系统

### ThemeColors

**包名**：`ai.wallpaper.aurora.ui.theme`

**描述**：主题颜色配置。

#### 主题枚举

```kotlin
enum class AppTheme {
    CLASSIC,    // 经典主题（紫色 + 金色）
    MODERN,     // 现代主题（青色 + 珊瑚橙）
    ELEGANT,    // 优雅主题（薰衣草 + 玫瑰粉）
    VIBRANT     // 活力主题（玫瑰红 + 薄荷绿）
}
```

#### 公开方法

##### getColorScheme

```kotlin
fun getColorScheme(
    theme: AppTheme,
    isDarkTheme: Boolean
): ColorScheme
```

**描述**：获取主题颜色方案。

**参数**：
- `theme`: AppTheme - 主题类型
- `isDarkTheme`: Boolean - 是否深色模式

**返回值**：
- `ColorScheme` - Material3 颜色方案

**使用示例**：
```kotlin
val colorScheme = ThemeColors.getColorScheme(
    theme = AppTheme.CLASSIC,
    isDarkTheme = isSystemInDarkTheme()
)

MaterialTheme(colorScheme = colorScheme) {
    // UI 内容
}
```

## 文件路径

### 视频路径文件

**文件名**：`video_live_wallpaper_file_path`

**位置**：`context.filesDir`

**格式**：纯文本，单行 URI 字符串

**读取示例**：
```kotlin
fun readVideoFilePath(context: Context): String? {
    val file = File(context.filesDir, "video_live_wallpaper_file_path")
    return if (file.exists()) {
        file.readText().trim()
    } else {
        null
    }
}
```

**写入示例**：
```kotlin
fun saveVideoUri(context: Context, uri: Uri) {
    val file = File(context.filesDir, "video_live_wallpaper_file_path")
    file.writeText(uri.toString())
}
```

### 历史记录文件

**文件名**：`wallpaper_history.json`

**位置**：`context.filesDir`

**格式**：JSON 数组

**示例内容**：
```json
[
  {
    "id": "1709884800000",
    "videoUri": "content://media/external/video/media/123",
    "timestamp": 1709884800000,
    "displayName": "my_video.mp4"
  }
]
```

### 音量控制文件

**文件名**：`unmute`

**位置**：`context.filesDir`

**格式**：空文件（存在表示开启音量，不存在表示静音）

**检查示例**：
```kotlin
val unmuteFile = File(context.filesDir, "unmute")
val isMuted = !unmuteFile.exists()
```

## 权限

### 必需权限

```xml
<!-- 读取视频文件 (Android 13+) -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- 读取外部存储 (Android 12 及以下) -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- 设置壁纸 -->
<uses-permission android:name="android.permission.SET_WALLPAPER" />
```

### 权限请求

```kotlin
// Android 13+
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    requestPermissions(
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
        REQUEST_CODE
    )
} else {
    requestPermissions(
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
        REQUEST_CODE
    )
}
```

## 常量

### 广播 Action

```kotlin
// 视频路径更新
const val VIDEO_PATH_UPDATED_ACTION = "ai.wallpaper.aurora.VIDEO_PATH_UPDATED"

// 视频参数控制
const val VIDEO_PARAMS_CONTROL_ACTION = "ai.wallpaper.aurora.VIDEO_PARAMS_CONTROL"

// 视频播放开始
const val VIDEO_PLAYBACK_STARTED_ACTION = "ai.wallpaper.aurora.VIDEO_PLAYBACK_STARTED"
```

### 文件名

```kotlin
// 视频路径文件
const val VIDEO_PATH_FILE = "video_live_wallpaper_file_path"

// 历史记录文件
const val HISTORY_FILE = "wallpaper_history.json"

// 音量控制文件
const val UNMUTE_FILE = "unmute"
```

### 默认值

```kotlin
// 最大播放器数量
const val MAX_PLAYER_COUNT = 3

// 视频裁剪时长（毫秒）
const val VIDEO_CLIP_DURATION_MS = 10_000L

// 最小缓冲时长（毫秒）
const val MIN_BUFFER_MS = 2000

// 最大缓冲时长（毫秒）
const val MAX_BUFFER_MS = 5000
```

## 错误处理

### 常见异常

#### VideoNotFoundException

**描述**：视频文件未找到

**处理**：
```kotlin
try {
    val player = videoProcessor.getOrCreatePlayer(uriKey, videoUri)
} catch (e: FileNotFoundException) {
    Log.e(TAG, "Video not found: $videoUri", e)
    // 显示错误提示
}
```

#### PermissionDeniedException

**描述**：权限被拒绝

**处理**：
```kotlin
if (ContextCompat.checkSelfPermission(context, permission)
    != PackageManager.PERMISSION_GRANTED) {
    // 请求权限
    requestPermissions(arrayOf(permission), REQUEST_CODE)
}
```

#### OutOfMemoryError

**描述**：内存不足

**处理**：
```kotlin
try {
    val player = videoProcessor.getOrCreatePlayer(uriKey, videoUri)
} catch (e: OutOfMemoryError) {
    Log.e(TAG, "Out of memory", e)
    videoProcessor.clearAll()
    // 显示错误提示
}
```

## 版本兼容性

### Android 版本

- **最低版本**：Android 8.0 (API 26)
- **目标版本**：Android 16 (API 36)

### 特殊处理

**Android 13+ 广播注册**：
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.registerReceiver(
        context,
        receiver,
        intentFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
} else {
    context.registerReceiver(receiver, intentFilter)
}
```

**Android 10+ 媒体访问**：
```kotlin
if (path.startsWith("content://")) {
    mediaPlayer.setDataSource(context, Uri.parse(path))
} else {
    mediaPlayer.setDataSource(path)
}
```

---

**文档版本**：v1.0
**更新日期**：2026-03-08
**维护者**：QuickerStudio 开发团队
