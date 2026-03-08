package ai.wallpaper.aurora

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import ai.wallpaper.aurora.service.UnlockWallpaperService
import ai.wallpaper.aurora.service.VideoLiveWallpaperService
import ai.wallpaper.aurora.ui.theme.AuroraTheme
import ai.wallpaper.aurora.ui.theme.getThemeColors
import ai.wallpaper.aurora.ui.theme.getThemeAwareAuroraIcon
import ai.wallpaper.aurora.data.WallpaperHistoryManager
import ai.wallpaper.aurora.utils.LocalVideoScanner
import ai.wallpaper.aurora.utils.LocalImageScanner
import ai.wallpaper.aurora.utils.LocalVideo
import ai.wallpaper.aurora.utils.MediaType
import ai.wallpaper.aurora.utils.PreviewProcessor
import ai.wallpaper.aurora.utils.VideoThumbnailCache
import java.io.File

class MainActivity : ComponentActivity() {

    private var refreshHistoryCallback: (() -> Unit)? = null

    private val historyRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_REFRESH_HISTORY) {
                android.util.Log.d("MainActivity", "Received history refresh broadcast")
                refreshHistoryCallback?.invoke()
            }
        }
    }

    private val historyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED) {
                val videoUri = intent.getStringExtra(VideoLiveWallpaperService.EXTRA_VIDEO_URI)
                videoUri?.let {
                    try {
                        WallpaperHistoryManager.addHistory(context, Uri.parse(it))
                        android.util.Log.d("MainActivity", "Added to history from wallpaper service: $it")

                        // 发送本地广播通知 UI 刷新
                        val refreshIntent = Intent(ACTION_REFRESH_HISTORY)
                        context.sendBroadcast(refreshIntent)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to add history", e)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_HISTORY = "ai.wallpaper.aurora.REFRESH_HISTORY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册广播接收器（接收壁纸服务的播放通知）
        val filter = IntentFilter(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                historyReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(historyReceiver, filter)
        }

        // 注册历史刷新广播接收器
        val refreshFilter = IntentFilter(ACTION_REFRESH_HISTORY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                historyRefreshReceiver,
                refreshFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(historyRefreshReceiver, refreshFilter)
        }

        setContent {
            // 在 setContent 内部管理主题状态，这样可以响应状态变化
            var followSystemTheme by remember {
                mutableStateOf(File(filesDir, "follow_system_theme").exists())
            }
            var selectedTheme by remember {
                val themeFile = File(filesDir, "selected_theme")
                mutableStateOf(
                    if (themeFile.exists()) themeFile.readText() else "classic"
                )
            }

            // 历史刷新触发器
            var historyRefreshTrigger by remember { mutableStateOf(0) }

            // 设置刷新回调
            DisposableEffect(Unit) {
                refreshHistoryCallback = {
                    historyRefreshTrigger++
                }
                onDispose {
                    refreshHistoryCallback = null
                }
            }

            AuroraTheme(
                selectedTheme = selectedTheme,
                followSystemTheme = followSystemTheme
            ) {
                MainScreen(
                    initialFollowSystemTheme = followSystemTheme,
                    initialSelectedTheme = selectedTheme,
                    historyRefreshTrigger = historyRefreshTrigger,
                    onThemeChange = { theme ->
                        selectedTheme = theme
                    },
                    onFollowSystemThemeChange = { follow ->
                        followSystemTheme = follow
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(historyReceiver)
            unregisterReceiver(historyRefreshReceiver)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error unregistering receiver", e)
        }
    }
}

// 视频数据模型
data class VideoItem(
    val id: Int,
    val uri: Uri?,
    val thumbnailUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val mediaType: MediaType = MediaType.VIDEO
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialFollowSystemTheme: Boolean = false,
    initialSelectedTheme: String = "classic",
    historyRefreshTrigger: Int = 0,
    onThemeChange: (String) -> Unit = {},
    onFollowSystemThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var videoPath by remember { mutableStateOf("") }

    // 视频列表状态 - 从历史记录加载
    var videoList by remember { mutableStateOf(listOf<VideoItem>()) }
    var selectedVideoId by remember { mutableStateOf<Int?>(null) }
    // 保存 hashCode 到原始 ID 的映射
    var videoIdMap by remember { mutableStateOf(mapOf<Int, String>()) }
    // 历史记录显示数量（支持下拉加载更多）
    var historyDisplayCount by remember { mutableStateOf(10) }
    var isLoadingMoreHistory by remember { mutableStateOf(false) }

    // 历史卡片播放器池 - LRU 策略，最多 3 个播放器
    val historyPlayerPool = remember { ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3) }
    val historyPreviewProcessor = remember { PreviewProcessor(context) }

    // 本地视频库预览处理器
    val localPreviewProcessor = remember { PreviewProcessor(context) }

    // 清理历史播放器池
    DisposableEffect(Unit) {
        onDispose {
            historyPreviewProcessor.cancel()
            localPreviewProcessor.cancel()
            historyPlayerPool.releaseAll()
        }
    }

    // 本地视频库状态
    var localVideos by remember { mutableStateOf(listOf<LocalVideo>()) }
    var localVideoOffset by remember { mutableStateOf(0) }
    var isLoadingLocalVideos by remember { mutableStateOf(false) }
    var isLocalLibraryVisible by remember { mutableStateOf(true) }
    // 本地视频预览缩略图状态
    val localVideoPreviews = remember { mutableStateMapOf<Long, Bitmap>() }

    // 本地视频库播放器池 - LRU 策略，最多 3 个播放器
    val localPlayerPool = remember { ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3) }

    // 清理本地播放器池
    DisposableEffect(Unit) {
        onDispose {
            localPlayerPool.releaseAll()
        }
    }

    // 设置状态
    var showFab by remember {
        mutableStateOf(File(context.filesDir, "show_fab").exists())
    }
    var autoHideTimer by remember {
        val timerFile = File(context.filesDir, "auto_hide_timer")
        mutableStateOf(if (timerFile.exists()) timerFile.readText().toIntOrNull() ?: 10 else 10)
    }
    var playWithSound by remember {
        mutableStateOf(File(context.filesDir, "unmute").exists())
    }
    var hideIcon by remember {
        mutableStateOf(
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, MainActivity::class.java)
            ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )
    }
    var clearDesktopIcons by remember {
        mutableStateOf(File(context.filesDir, "clear_desktop_enabled").exists())
    }
    var followSystemTheme by remember {
        mutableStateOf(initialFollowSystemTheme)
    }
    var selectedTheme by remember {
        mutableStateOf(initialSelectedTheme)
    }
    var mediaDisplayMode by remember {
        val modeFile = File(context.filesDir, "media_display_mode")
        mutableStateOf(if (modeFile.exists()) modeFile.readText() else "fit")
    }
    var showUserGuideDialog by remember { mutableStateOf(false) }
    var showReportIssueDialog by remember { mutableStateOf(false) }
    var issueTitle by remember { mutableStateOf("") }
    var issueBody by remember { mutableStateOf("") }

    // 获取当前主题颜色配置
    val themeColors = if (!followSystemTheme) {
        getThemeColors(selectedTheme)
    } else {
        null
    }

    // 获取当前主题图标（根据系统主题或自定义主题）
    val auroraIconRes = getThemeAwareAuroraIcon(
        isDarkTheme = if (followSystemTheme) {
            androidx.compose.foundation.isSystemInDarkTheme()
        } else {
            false // 自定义主题使用浅色图标
        }
    )
    val websiteButtonBackground = lerp(
        themeColors?.buttonBackground ?: MaterialTheme.colorScheme.primary,
        Color.White,
        0.3f
    )
    val websiteButtonContent = themeColors?.buttonContent ?: MaterialTheme.colorScheme.onPrimary
    val updateButtonBackground = lerp(
        themeColors?.buttonBackground ?: MaterialTheme.colorScheme.primary,
        Color.White,
        0.3f
    )
    val updateButtonContent = themeColors?.buttonContent ?: MaterialTheme.colorScheme.onPrimary
    // 终止壁纸按钮使用红色系
    val stopButtonBackground = lerp(
        Color(0xFFE53935), // 红色
        Color.White,
        0.2f
    )
    val stopButtonContent = Color.White
    val issuesNewUrl = stringResource(R.string.issues_new_url)
    val appVersion = stringResource(R.string.version_name)
    val currentThemeName = if (followSystemTheme) {
        stringResource(R.string.follow_system_theme)
    } else {
        when (selectedTheme) {
            "classic" -> stringResource(R.string.theme_classic)
            "modern" -> stringResource(R.string.theme_modern)
            "elegant" -> stringResource(R.string.theme_elegant)
            "vibrant" -> stringResource(R.string.theme_vibrant)
            else -> selectedTheme
        }
    }
    val deviceInfo = buildString {
        appendLine("## Environment")
        appendLine("- Aurora version: $appVersion")
        appendLine("- Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("- Current theme: $currentThemeName")
        appendLine()
        appendLine("## Feedback")
    }
    val fabButtonBackground = lerp(
        themeColors?.buttonBackground ?: MaterialTheme.colorScheme.primary,
        Color.White,
        0.3f
    )
    val fabButtonContent = themeColors?.buttonContent ?: MaterialTheme.colorScheme.onPrimary

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            scope.launch {
                isLoadingLocalVideos = true
                // 并行扫描视频和图片
                val videosDeferred = async { LocalVideoScanner.scanVideos(context, offset = 0, limit = 100) }
                val imagesDeferred = async { LocalImageScanner.scanImages(context, offset = 0, limit = 100) }
                // 合并并按时间排序，取前20个
                localVideos = (videosDeferred.await() + imagesDeferred.await())
                    .sortedByDescending { it.dateAdded }
                    .take(20)
                localVideoOffset = 20
                isLoadingLocalVideos = false
            }
        }
    }

    // 视频选择器
    // FAB 按钮视频选择器 - 始终调用系统壁纸设置（备用方案，最可靠）
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            saveVideoUri(context, it)

            // FAB 按钮始终打开系统壁纸设置界面（安全可靠的备用方案）
            // 适用于所有 Android 版本，不依赖广播机制
            VideoLiveWallpaperService.setToWallPaper(context)

            val (items, idMap) = loadHistoryVideoItems(context)
            videoList = items
            videoIdMap = idMap
        }
    }

    LaunchedEffect(Unit) {
        // 请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            ))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }

        // 从历史记录加载视频列表
        val (items, idMap) = loadHistoryVideoItems(context)
        videoList = items
        videoIdMap = idMap

        // 仅在权限已存在时加载本地视频库
        val hasVideoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasVideoPermission) {
            isLoadingLocalVideos = true
            // 并行扫描视频和图片
            val videosDeferred = async { LocalVideoScanner.scanVideos(context, offset = 0, limit = 100) }
            val imagesDeferred = async { LocalImageScanner.scanImages(context, offset = 0, limit = 100) }
            // 合并并按时间排序，取前20个
            localVideos = (videosDeferred.await() + imagesDeferred.await())
                .sortedByDescending { it.dateAdded }
                .take(20)
            localVideoOffset = 20
            isLoadingLocalVideos = false
        }
    }

    // 监听历史刷新触发器
    LaunchedEffect(historyRefreshTrigger) {
        if (historyRefreshTrigger > 0) {
            android.util.Log.d("MainActivity", "Refreshing history list, trigger: $historyRefreshTrigger")
            val (items, idMap) = loadHistoryVideoItems(context)
            videoList = items
            videoIdMap = idMap
            historyPreviewProcessor.clear()
            // 重置显示数量
            historyDisplayCount = minOf(10, items.size)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = themeColors?.drawerBackground ?: MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    val releasesUrl = stringResource(R.string.releases_url)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 终止壁纸按钮状态
                            var stopButtonState by remember { mutableStateOf("normal") } // normal, success, error
                            var isStopPressed by remember { mutableStateOf(false) }
                            var shakeOffset by remember { mutableStateOf(0f) }

                            val stopButtonScale by animateFloatAsState(
                                targetValue = if (isStopPressed) 0.92f else 1f,
                                animationSpec = tween(durationMillis = 100)
                            )

                            // 抖动动画
                            LaunchedEffect(stopButtonState) {
                                if (stopButtonState == "error") {
                                    repeat(4) { i ->
                                        shakeOffset = if (i % 2 == 0) 10f else -10f
                                        delay(50)
                                    }
                                    shakeOffset = 0f
                                    delay(1500)
                                    stopButtonState = "normal"
                                }
                            }

                            // 成功状态自动恢复
                            LaunchedEffect(stopButtonState) {
                                if (stopButtonState == "success") {
                                    delay(2000)
                                    stopButtonState = "normal"
                                }
                            }

                            // 动态按钮颜色
                            val currentStopButtonBackground = when (stopButtonState) {
                                "success" -> Color(0xFF4CAF50) // 绿色
                                "error" -> stopButtonBackground
                                else -> stopButtonBackground
                            }

                            // 终止壁纸按钮
                            Button(
                                onClick = {
                                    scope.launch {
                                        isStopPressed = true
                                        delay(100)
                                        try {
                                            // 检查是否有视频壁纸正在运行
                                            val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                                            val wallpaperInfo = wallpaperManager.wallpaperInfo

                                            if (wallpaperInfo != null &&
                                                wallpaperInfo.packageName == context.packageName &&
                                                wallpaperInfo.serviceName == VideoLiveWallpaperService::class.java.name) {
                                                // 有视频壁纸在运行，清除它
                                                wallpaperManager.clear()
                                                stopButtonState = "success"
                                            } else {
                                                // 没有视频壁纸在运行，显示错误（抖动）
                                                stopButtonState = "error"
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to clear wallpaper", e)
                                            stopButtonState = "error"
                                        } finally {
                                            isStopPressed = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = currentStopButtonBackground,
                                    contentColor = stopButtonContent
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = stopButtonScale
                                        scaleY = stopButtonScale
                                        translationX = shakeOffset
                                    }
                            ) {
                                Text(
                                    text = stringResource(R.string.stop_wallpaper),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            // 检查更新按钮
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(releasesUrl)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = updateButtonBackground,
                                    contentColor = updateButtonContent
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.check_update),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                    // 壁纸设置
                    Text(
                        text = stringResource(R.string.wallpaper_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 自定义路径
                    Text(
                        text = stringResource(R.string.add_video_file_path),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = videoPath,
                            onValueChange = { videoPath = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.add_path)) }
                        )
                        IconButton(
                            onClick = {
                                if (videoPath.isNotBlank()) {
                                    saveVideoPath(context, videoPath)

                                    // 如果壁纸未激活，打开设置界面
                                    if (!isAuroraWallpaperActive(context)) {
                                        VideoLiveWallpaperService.setToWallPaper(context)
                                    } else {
                                        // 如果壁纸已激活，发送广播通知切换视频
                                        VideoLiveWallpaperService.notifyVideoPathChanged(context)
                                    }

                                    videoPath = ""
                                    scope.launch { drawerState.close() }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.apply))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.play_video_with_sound),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (playWithSound) stringResource(R.string.enable)
                                       else stringResource(R.string.disable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playWithSound,
                            onCheckedChange = { checked ->
                                playWithSound = checked
                                val unmuteFile = File(context.filesDir, "unmute")
                                if (checked) {
                                    unmuteFile.createNewFile()
                                    VideoLiveWallpaperService.unmuteMusic(context)
                                } else {
                                    unmuteFile.delete()
                                    VideoLiveWallpaperService.muteMusic(context)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.clear_desktop_icons),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (clearDesktopIcons) stringResource(R.string.enable)
                                       else stringResource(R.string.disable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = clearDesktopIcons,
                            onCheckedChange = { checked ->
                                clearDesktopIcons = checked
                                val enableFile = File(context.filesDir, "clear_desktop_enabled")
                                if (checked) {
                                    enableFile.createNewFile()
                                    UnlockWallpaperService.start(context)
                                } else {
                                    enableFile.delete()
                                    UnlockWallpaperService.stop(context)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.media_display_mode),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (mediaDisplayMode == "fit")
                                       stringResource(R.string.media_display_fit)
                                       else stringResource(R.string.media_display_fill),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                mediaDisplayMode = if (mediaDisplayMode == "fit") "fill" else "fit"
                                val modeFile = File(context.filesDir, "media_display_mode")
                                modeFile.writeText(mediaDisplayMode)

                                // 清除旧的预览缓存，强制重新加载
                                videoList = videoList.map { it.copy(previewBitmap = null) }
                                historyPreviewProcessor.clear()
                                localVideoPreviews.clear()
                                localPreviewProcessor.clear()
                            },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (mediaDisplayMode == "fit")
                                       stringResource(R.string.media_display_fill)
                                       else stringResource(R.string.media_display_fit),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 应用设置
                    Text(
                        text = stringResource(R.string.applications_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.show_fab_button),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (showFab) stringResource(R.string.show)
                                       else stringResource(R.string.hide),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showFab,
                            onCheckedChange = { checked ->
                                showFab = checked
                                val fabFile = File(context.filesDir, "show_fab")
                                if (checked) {
                                    fabFile.createNewFile()
                                } else {
                                    fabFile.delete()
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.hide_icon_from_launcher),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (hideIcon) stringResource(R.string.hide)
                                       else stringResource(R.string.show),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideIcon,
                            onCheckedChange = { checked ->
                                hideIcon = checked
                                val componentName = ComponentName(context, MainActivity::class.java)
                                val newState = if (checked) {
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                } else {
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                }
                                context.packageManager.setComponentEnabledSetting(
                                    componentName,
                                    newState,
                                    PackageManager.DONT_KILL_APP
                                )
                            }
                        )
                    }

                    // 自动隐藏倒计时设置
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.auto_hide_timer),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${autoHideTimer}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = when (autoHideTimer) {
                                5 -> 0f
                                10 -> 1f
                                15 -> 2f
                                else -> 1f
                            },
                            onValueChange = { value ->
                                autoHideTimer = when (value.toInt()) {
                                    0 -> 5
                                    1 -> 10
                                    2 -> 15
                                    else -> 10
                                }
                                File(context.filesDir, "auto_hide_timer").writeText(autoHideTimer.toString())
                            },
                            valueRange = 0f..2f,
                            steps = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5s", style = MaterialTheme.typography.bodySmall)
                            Text("10s", style = MaterialTheme.typography.bodySmall)
                            Text("15s", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 主题设置
                    Text(
                        text = stringResource(R.string.theme),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.follow_system_theme),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (followSystemTheme) stringResource(R.string.enable)
                                       else stringResource(R.string.disable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = followSystemTheme,
                            onCheckedChange = { checked ->
                                followSystemTheme = checked
                                val themeFile = File(context.filesDir, "follow_system_theme")
                                if (checked) {
                                    themeFile.createNewFile()
                                } else {
                                    themeFile.delete()
                                }
                                // 通知父组件更新主题
                                onFollowSystemThemeChange(checked)
                            }
                        )
                    }

                    // 手动主题选择按钮（仅在不跟随系统主题时显示）
                    if (!followSystemTheme) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 主题选择按钮 - 一排显示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeButton(
                                themeName = stringResource(R.string.theme_classic),
                                themeId = "classic",
                                isSelected = selectedTheme == "classic",
                                backgroundColor = getThemeColors("classic").secondary,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedTheme = "classic"
                                File(context.filesDir, "selected_theme").writeText("classic")
                                onThemeChange("classic")
                            }

                            ThemeButton(
                                themeName = stringResource(R.string.theme_modern),
                                themeId = "modern",
                                isSelected = selectedTheme == "modern",
                                backgroundColor = getThemeColors("modern").secondary,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedTheme = "modern"
                                File(context.filesDir, "selected_theme").writeText("modern")
                                onThemeChange("modern")
                            }

                            ThemeButton(
                                themeName = stringResource(R.string.theme_elegant),
                                themeId = "elegant",
                                isSelected = selectedTheme == "elegant",
                                backgroundColor = getThemeColors("elegant").secondary,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedTheme = "elegant"
                                File(context.filesDir, "selected_theme").writeText("elegant")
                                onThemeChange("elegant")
                            }

                            ThemeButton(
                                themeName = stringResource(R.string.theme_vibrant),
                                themeId = "vibrant",
                                isSelected = selectedTheme == "vibrant",
                                backgroundColor = getThemeColors("vibrant").secondary,
                                modifier = Modifier.weight(1f)
                            ) {
                                selectedTheme = "vibrant"
                                File(context.filesDir, "selected_theme").writeText("vibrant")
                                onThemeChange("vibrant")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // About 栏
                    Text(
                        text = stringResource(R.string.about),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(auroraIconRes),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (-30).dp)
                                .size(180.dp)
                                .alpha(0.15f),
                            tint = Color.Unspecified
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val githubUrl = stringResource(R.string.github_url)
                            // 公司
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.company),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.quicker_studio),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // 产品名称
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.product_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.aurora),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // 版权
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.copyright),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.personal_ownership),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // 网站
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.website),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(githubUrl)
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = websiteButtonBackground,
                                        contentColor = websiteButtonContent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        websiteButtonBackground
                                    )
                                ) {
                                    Icon(
                                        painter = androidx.compose.ui.res.painterResource(auroraIconRes),
                                        contentDescription = stringResource(R.string.website),
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.aurora),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily(Font(R.font.mistral)),
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 24.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 使用说明
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showUserGuideDialog = true }) {
                                    Text(
                                        text = stringResource(R.string.user_guide),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // 报告错误
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showReportIssueDialog = true }) {
                                    Text(
                                        text = stringResource(R.string.report_issue),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        if (showUserGuideDialog) {
            AlertDialog(
                onDismissRequest = { showUserGuideDialog = false },
                title = {
                    Text(text = stringResource(R.string.user_guide))
                },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.user_guide_content),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUserGuideDialog = false }) {
                        Text(text = stringResource(R.string.close))
                    }
                }
            )
        }

        if (showReportIssueDialog) {
            AlertDialog(
                onDismissRequest = { showReportIssueDialog = false },
                title = {
                    Text(text = stringResource(R.string.report_issue))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = issueTitle,
                            onValueChange = { issueTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.feedback_title)) }
                        )
                        OutlinedTextField(
                            value = issueBody,
                            onValueChange = { issueBody = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text(stringResource(R.string.feedback_content)) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val title = issueTitle.trim()
                            val body = issueBody.trim()
                            if (title.isNotEmpty() && body.isNotEmpty()) {
                                val fullBody = "$deviceInfo\n$body"
                                val issueUrl = "$issuesNewUrl?title=${Uri.encode(title)}&body=${Uri.encode(fullBody)}"
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(issueUrl)
                                    )
                                )
                                showReportIssueDialog = false
                                issueTitle = ""
                                issueBody = ""
                            }
                        },
                        enabled = issueTitle.isNotBlank() && issueBody.isNotBlank()
                    ) {
                        Text(text = stringResource(R.string.submit))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReportIssueDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 自定义顶部标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors?.topBarBackground ?: MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 菜单按钮
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.settings),
                                tint = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // Aurora 图标
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(auroraIconRes),
                            contentDescription = "Aurora",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Unspecified
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Aurora 文字标题
                        Text(
                            text = "Aurora",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily(Font(R.font.mistral))
                            ),
                            color = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // FAB按钮 - 右侧（备用方案：始终调用系统壁纸设置，安全可靠）
                    if (showFab) {
                        IconButton(
                            onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier
                                .offset(x = (-10).dp)
                                .width(56.dp)
                                .background(
                                    color = fabButtonBackground,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.choose_video_file),
                                tint = fabButtonContent
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (videoList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(auroraIconRes),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(180.dp)
                                        .alpha(0.12f),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.aurora),
                                    modifier = Modifier.offset(y = (-15).dp),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily(Font(R.font.mistral))
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                                )
                            }
                        }
                    } else {
                        // 视频网格
                        val gridState = rememberLazyGridState()
                        val displayedVideos = remember(videoList, historyDisplayCount) {
                            videoList.take(historyDisplayCount)
                        }

                        LaunchedEffect(displayedVideos, gridState) {
                            snapshotFlow {
                                gridState.layoutInfo.visibleItemsInfo
                                    .mapNotNull { itemInfo -> displayedVideos.getOrNull(itemInfo.index) }
                            }.collect { visibleVideos ->
                                val prioritizedVideos = LinkedHashSet<VideoItem>()
                                visibleVideos.forEach { prioritizedVideos.add(it) }
                                displayedVideos.forEach { prioritizedVideos.add(it) }

                                historyPreviewProcessor.submit(
                                    items = prioritizedVideos.map { video ->
                                        PreviewProcessor.PreviewRequest(
                                            id = video.id,
                                            uri = video.uri,
                                            hasPreview = video.previewBitmap != null,
                                            mediaType = video.mediaType
                                        )
                                    },
                                    displayMode = mediaDisplayMode,
                                    onPreviewReady = { id, uri, bitmap ->
                                        videoList = videoList.map { item ->
                                            if (item.id == id && item.uri == uri && item.previewBitmap == null) {
                                                item.copy(previewBitmap = bitmap)
                                            } else {
                                                item
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // 监听滚动事件，滚动时隐藏本地视频库
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.isScrollInProgress }
                                .collect { isScrolling ->
                                    if (isScrolling) {
                                        isLocalLibraryVisible = false
                                    }
                                }
                        }

                        // 监听滚动位置，接近底部时自动加载更多
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastVisibleIndex ->
                                    if (lastVisibleIndex != null && !isLoadingMoreHistory) {
                                        val totalItems = historyDisplayCount
                                        // 当滚动到倒数第3个时，加载更多
                                        if (lastVisibleIndex >= totalItems - 3 && historyDisplayCount < videoList.size) {
                                            isLoadingMoreHistory = true
                                            kotlinx.coroutines.delay(100) // 短暂延迟避免频繁触发
                                            historyDisplayCount = minOf(historyDisplayCount + 10, videoList.size)
                                            isLoadingMoreHistory = false
                                        }
                                    }
                                }
                        }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            itemsIndexed(displayedVideos, key = { _, video -> video.id }) { _, video ->
                                VideoGridItem(
                                    video = video,
                                    isSelected = selectedVideoId == video.id,
                                    themeColors = themeColors,
                                    playerPool = historyPlayerPool,
                                    onVideoTouch = { videoId ->
                                        selectedVideoId = if (selectedVideoId == videoId) null else videoId
                                    },
                                    onVideoClick = { videoUri ->
                                        videoUri?.let { uri ->
                                            // 从videoList中找到对应的VideoItem获取mediaType
                                            val videoItem = displayedVideos.find { it.uri == uri }

                                            if (videoItem?.mediaType == MediaType.IMAGE) {
                                                // 图片：直接设置为静态壁纸
                                                scope.launch {
                                                    try {
                                                        val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                                                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                                            wallpaperManager.setBitmap(bitmap)
                                                            bitmap.recycle()
                                                        }
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("MainActivity", "Failed to set image wallpaper", e)
                                                    }
                                                }
                                            } else {
                                                // 视频：使用Live Wallpaper服务
                                                saveVideoPath(context, uri.toString())

                                                // 如果壁纸未激活，打开设置界面
                                                if (!isAuroraWallpaperActive(context)) {
                                                    VideoLiveWallpaperService.setToWallPaper(context)
                                                } else {
                                                    // 如果壁纸已激活，发送广播通知切换视频
                                                    VideoLiveWallpaperService.notifyVideoPathChanged(context)
                                                }
                                            }
                                        }
                                    },
                                    onVideoLongPress = { videoId ->
                                        // 长按删除历史记录
                                        val originalId = videoIdMap[videoId]
                                        originalId?.let { id ->
                                            WallpaperHistoryManager.deleteHistory(context, id)
                                            val (items, idMap) = loadHistoryVideoItems(context)
                                            videoList = items
                                            videoIdMap = idMap
                                            historyPreviewProcessor.clear()
                                            // 重置显示数量
                                            historyDisplayCount = minOf(10, items.size)
                                        }
                                    },
                                    displayMode = mediaDisplayMode
                                )
                            }
                        }
                    }

                    // 本地视频库横向滑动栏 - 增量加载和销毁
                    LocalVideoLibrary(
                        localVideos = localVideos,
                        localVideoPreviews = localVideoPreviews,
                        themeColors = themeColors,
                        isLoading = isLoadingLocalVideos,
                        autoHideTimer = autoHideTimer,
                        isVisible = isLocalLibraryVisible,
                        playerPool = localPlayerPool,
                        previewProcessor = localPreviewProcessor,
                        displayMode = mediaDisplayMode,
                        onVisibilityChange = { isVisible ->
                            isLocalLibraryVisible = isVisible
                        },
                        onLoadMore = {
                            scope.launch {
                                if (!isLoadingLocalVideos) {
                                    isLoadingLocalVideos = true
                                    // 并行扫描更多视频和图片
                                    val videosDeferred = async {
                                        LocalVideoScanner.scanVideos(context, offset = 0, limit = localVideoOffset + 100)
                                    }
                                    val imagesDeferred = async {
                                        LocalImageScanner.scanImages(context, offset = 0, limit = localVideoOffset + 100)
                                    }
                                    // 合并并按时间排序
                                    val allMedia = (videosDeferred.await() + imagesDeferred.await())
                                        .sortedByDescending { it.dateAdded }
                                    // 取新的20个
                                    localVideos = allMedia.take(localVideoOffset + 20)
                                    localVideoOffset += 20
                                    isLoadingLocalVideos = false
                                }
                            }
                        },
                        onVideoClick = { video ->
                            if (video.mediaType == MediaType.IMAGE) {
                                // 图片：直接设置为静态壁纸
                                scope.launch {
                                    try {
                                        val wallpaperManager = android.app.WallpaperManager.getInstance(context)
                                        context.contentResolver.openInputStream(video.uri)?.use { inputStream ->
                                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                                            wallpaperManager.setBitmap(bitmap)
                                            bitmap.recycle()
                                        }
                                        // 添加到历史记录
                                        WallpaperHistoryManager.addHistory(
                                            context,
                                            video.uri,
                                            video.displayName,
                                            MediaType.IMAGE
                                        )
                                        // 刷新历史列表
                                        val (items, idMap) = loadHistoryVideoItems(context)
                                        videoList = items
                                        videoIdMap = idMap
                                        historyPreviewProcessor.clear()
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Failed to set image wallpaper", e)
                                    }
                                }
                            } else {
                                // 视频：使用Live Wallpaper服务
                                saveVideoUri(context, video.uri)

                                // 如果壁纸未激活，打开设置界面
                                if (!isAuroraWallpaperActive(context)) {
                                    VideoLiveWallpaperService.setToWallPaper(context)
                                } else {
                                    // 如果壁纸已激活，发送广播通知切换视频
                                    VideoLiveWallpaperService.notifyVideoPathChanged(context)
                                }

                                // 刷新历史列表
                                isLocalLibraryVisible = true
                                val (items, idMap) = loadHistoryVideoItems(context)
                                videoList = items
                                videoIdMap = idMap
                                historyPreviewProcessor.clear()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(
    video: VideoItem,
    isSelected: Boolean,
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    playerPool: ai.wallpaper.aurora.utils.LRUPlayerPool,
    onVideoTouch: (Int) -> Unit,
    onVideoClick: (Uri?) -> Unit = {},
    onVideoLongPress: (Int) -> Unit = {},
    displayMode: String = "fit"
) {
    var isDeleting by remember { mutableStateOf(false) }

    // 删除动画状态
    val deleteProgress by animateFloatAsState(
        targetValue = if (isDeleting) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        finishedListener = {
            if (isDeleting) {
                onVideoLongPress(video.id)
            }
        }
    )

    // 按 URI 复用播放器 - 使用 LRU 池
    val uriKey = video.uri?.toString() ?: ""
    val exoPlayer = remember(uriKey) {
        if (uriKey.isNotEmpty() && video.uri != null) {
            playerPool.getOrCreate(uriKey, video.uri)
        } else {
            null
        }
    }

    // 不在这里释放，由 LRU 池自动管理
    DisposableEffect(uriKey) {
        onDispose {
            // 播放器由 LRU 池管理，不需要手动释放
        }
    }

    // 根据选中状态控制播放
    LaunchedEffect(isSelected, exoPlayer) {
        exoPlayer?.let {
            if (isSelected) {
                it.play()
            } else {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .graphicsLayer {
                // 轻微缩小
                val scale = 1f - deleteProgress * 0.2f
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = (themeColors?.cardBorder ?: MaterialTheme.colorScheme.primary).copy(
                    alpha = 1f - deleteProgress
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f - deleteProgress * 0.5f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onVideoTouch(video.id)
                        tryAwaitRelease()
                    },
                    onTap = {
                        onVideoClick(video.uri)
                    },
                    onLongPress = {
                        isDeleting = true
                    }
                )
            }
    ) {
        if (isSelected && exoPlayer != null) {
            key(displayMode) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = if (displayMode == "fit")
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            else
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                        view.resizeMode = if (displayMode == "fit")
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    },
                    onRelease = { view ->
                        view.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            val previewBitmap = video.previewBitmap
            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

private fun isAuroraWallpaperActive(context: Context): Boolean {
    val wallpaperInfo = android.app.WallpaperManager.getInstance(context).wallpaperInfo ?: return false
    return wallpaperInfo.packageName == context.packageName &&
        wallpaperInfo.serviceName == ai.wallpaper.aurora.service.VideoLiveWallpaperService::class.java.name
}

private fun loadHistoryVideoItems(context: Context): Pair<List<VideoItem>, Map<Int, String>> {
    val history = WallpaperHistoryManager.loadHistory(context)
    val idMap = mutableMapOf<Int, String>()
    val items = history.map { item ->
        val hashId = item.id.hashCode()
        idMap[hashId] = item.id
        VideoItem(
            id = hashId,
            uri = Uri.parse(item.videoUri),
            mediaType = item.mediaType
        )
    }
    return items to idMap
}

private fun loadSavedVideos(context: Context): Uri? {
    return try {
        val file = File(context.filesDir, "video_live_wallpaper_file_path")
        if (file.exists()) {
            val path = file.readText()
            Uri.parse(path)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private fun saveVideoUri(context: Context, uri: Uri) {
    try {
        val uriString = uri.toString()
        if (uriString.startsWith("content://com.android.providers") || uriString.startsWith("content://com.google.android.apps")) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            android.util.Log.d("MainActivity", "Persistable URI permission granted for: $uri")
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to take persistable URI permission", e)
    }

    context.openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
        it.write(uri.toString().toByteArray())
    }

    // 注意：不在这里添加历史记录，只有壁纸服务成功使用视频后才记录
    // 历史记录由 VideoLiveWallpaperService 在 onSurfaceCreated 成功播放后添加

    android.util.Log.d("MainActivity", "Video URI saved: $uri")
}

private fun saveVideoPath(context: Context, path: String) {
    context.openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
        it.write(path.toByteArray())
    }
    // 注意：壁纸服务采用文件通信，切换壁纸需要重新应用壁纸（系统会重启服务）
}

@Composable
fun ThemeButton(
    themeName: String,
    themeId: String,
    isSelected: Boolean,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor.copy(alpha = if (isSelected) 1f else 0.6f))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onClick()
                        tryAwaitRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = themeName,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun LocalVideoLibrary(
    localVideos: List<LocalVideo>,
    localVideoPreviews: MutableMap<Long, Bitmap>,
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    isLoading: Boolean,
    autoHideTimer: Int,
    isVisible: Boolean,
    playerPool: ai.wallpaper.aurora.utils.LRUPlayerPool,
    previewProcessor: PreviewProcessor,
    displayMode: String,
    onVisibilityChange: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (LocalVideo) -> Unit
) {
    val listState = rememberLazyListState()
    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 400.dp,
        animationSpec = tween(durationMillis = 300)
    )

    // 选中状态管理 - 触控播放
    var selectedVideoId by remember { mutableStateOf<Long?>(null) }

    // 自动隐藏倒计时 - 折叠时回收所有播放器资源
    LaunchedEffect(isVisible, autoHideTimer) {
        if (isVisible) {
            delay((autoHideTimer * 1000).toLong())
            // 折叠前释放所有播放器
            playerPool.releaseAll()
            selectedVideoId = null
            onVisibilityChange(false)
        }
    }

    // 跟踪可见项，实现增量销毁
    // 注意：LRU 池已经自动管理数量上限，这里只需要释放完全不可见的播放器
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val visibleUris = visibleItems.mapNotNull { itemInfo ->
                    val index = itemInfo.index - 1 // 减1因为第一个是提示卡片
                    if (index >= 0 && index < localVideos.size) {
                        localVideos[index].uri.toString()
                    } else null
                }.toSet()

                // 增量销毁：释放不可见的播放器
                // LRU 池会自动管理数量上限，这里只是提前清理不可见的
                // 注意：由于 LRU 池内部使用 LinkedHashMap，我们不能直接遍历 keys
                // 所以这里的逻辑简化为：让 LRU 池自动管理即可
            }
    }

    // 预览处理：为可见的图片生成缩略图
    LaunchedEffect(listState, localVideos) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val visibleVideos = visibleItems.mapNotNull { itemInfo ->
                    val index = itemInfo.index - 1 // 减1因为第一个是提示卡片
                    if (index >= 0 && index < localVideos.size) {
                        localVideos[index]
                    } else null
                }

                // 提交预览请求
                previewProcessor.submit(
                    items = visibleVideos.map { video ->
                        PreviewProcessor.PreviewRequest(
                            id = video.id.toInt(),
                            uri = video.uri,
                            hasPreview = localVideoPreviews.containsKey(video.id),
                            mediaType = video.mediaType
                        )
                    },
                    displayMode = displayMode,
                    onPreviewReady = { id, uri, bitmap ->
                        localVideoPreviews[id.toLong()] = bitmap
                    }
                )
            }
    }

    // 只在显示或动画中时占用完整空间，隐藏后只显示滑动区域
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isVisible || offsetX < 400.dp) 176.dp else 40.dp)
    ) {
        if (isVisible || offsetX < 400.dp) {
            // 横向滑动视频列表 - 添加滑动阻尼效果
            // 关键优化：降低滑动速度，确保资源回收速度 > 创建速度
            val density = LocalDensity.current
            val flingBehavior = remember {
                object : FlingBehavior {
                    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                        // 降低滑动速度到原来的 40%，增加阻尼效果
                        val dampedVelocity = initialVelocity * 0.4f
                        return scrollBy(dampedVelocity)
                    }
                }
            }

            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = offsetX)
                    .background(themeColors?.surface ?: MaterialTheme.colorScheme.surface)
            ) {
                // 提示卡片 - 最左边
                item {
                    Box(
                        modifier = Modifier
                            .height(160.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tap_video_to_set_wallpaper),
                            style = MaterialTheme.typography.bodySmall,
                            color = themeColors?.onSurface ?: MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 11.sp
                        )
                    }
                }

                items(localVideos, key = { it.id }) { video ->
                    LocalVideoCard(
                        video = video,
                        previewBitmap = localVideoPreviews[video.id],
                        themeColors = themeColors,
                        playerPool = playerPool,
                        displayMode = displayMode,
                        isSelected = selectedVideoId == video.id,
                        onClick = {
                            // 触控切换选中状态
                            selectedVideoId = if (selectedVideoId == video.id) null else video.id
                            // 点击后设置为壁纸
                            onVideoClick(video)
                        }
                    )
                }

                // 加载更多指示器
                if (localVideos.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .height(160.dp)
                                .clickable { if (!isLoading) onLoadMore() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = themeColors?.primary ?: MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.swipe_right_to_load_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = themeColors?.onSurface ?: MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 滑动展开区域 - 始终显示在底部
        if (!isVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(themeColors?.surface?.copy(alpha = 0.9f) ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            // 向左拖拽展开视频库
                            if (dragAmount < -20) {
                                onVisibilityChange(true)
                            }
                        }
                    }
                    .clickable { onVisibilityChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Show library",
                        tint = themeColors?.onSurface ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.swipe_left_to_show_library),
                        style = MaterialTheme.typography.bodySmall,
                        color = themeColors?.onSurface ?: MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // 检测滑动到末尾
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= localVideos.size - 2 && !isLoading) {
                    onLoadMore()
                }
            }
    }
}

@Composable
fun LocalVideoCard(
    video: LocalVideo,
    previewBitmap: Bitmap?,
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    playerPool: ai.wallpaper.aurora.utils.LRUPlayerPool,
    displayMode: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val uriKey = video.uri.toString()

    // 图片不需要播放器
    val exoPlayer = if (video.mediaType == MediaType.VIDEO) {
        remember(uriKey) {
            playerPool.getOrCreate(uriKey, video.uri)
        }
    } else {
        null
    }

    DisposableEffect(uriKey) {
        onDispose {
            // 播放器由 LRU 池管理，不在这里释放
        }
    }

    // 触控播放：只有视频且选中时才播放
    LaunchedEffect(isSelected, exoPlayer) {
        exoPlayer?.let {
            if (isSelected) {
                it.play()
            } else {
                it.pause()
                it.seekTo(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = themeColors?.cardBorder ?: MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (video.mediaType == MediaType.IMAGE) {
            // 显示图片预览
            previewBitmap?.let { bitmap ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } ?: Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // 显示视频
            exoPlayer?.let { player ->
                key(displayMode) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                isClickable = false
                                isFocusable = false
                                resizeMode = if (displayMode == "fit") {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { view ->
                            view.player = player
                            view.resizeMode = if (displayMode == "fit") {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        onRelease = { view ->
                            view.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(4.dp)
        ) {
            Text(
                text = video.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                fontSize = 10.sp
            )
        }
    }
}
