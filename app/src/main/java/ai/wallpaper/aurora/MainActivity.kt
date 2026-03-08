package ai.wallpaper.aurora

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import ai.wallpaper.aurora.utils.LocalVideoScanner
import ai.wallpaper.aurora.utils.LocalImageScanner
import ai.wallpaper.aurora.utils.LocalVideo
import ai.wallpaper.aurora.utils.MediaType
import ai.wallpaper.aurora.utils.PreviewProcessor
import ai.wallpaper.aurora.utils.VideoThumbnailCache
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            AuroraTheme(
                selectedTheme = selectedTheme,
                followSystemTheme = followSystemTheme
            ) {
                MainScreen(
                    initialFollowSystemTheme = followSystemTheme,
                    initialSelectedTheme = selectedTheme,
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
}

// 视频数据模型
data class VideoItem(
    val id: Int,
    val uri: Uri?,
    val thumbnailUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val displayName: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialFollowSystemTheme: Boolean = false,
    initialSelectedTheme: String = "classic",
    onThemeChange: (String) -> Unit = {},
    onFollowSystemThemeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var videoPath by remember { mutableStateOf("") }

    // 视频列表状态 - 从媒体库扫描加载
    var videoList by remember { mutableStateOf(listOf<VideoItem>()) }
    var selectedVideoId by remember { mutableStateOf<Int?>(null) }
    var pressedCardId by remember { mutableStateOf<Int?>(null) }
    val cardBounds = remember { mutableStateMapOf<Int, Rect>() }
    // 保留已触达卡片的播放器：播放态/暂停态都属于已保留，滑出屏幕才销毁
    val retainedPlayerIds = remember { mutableStateListOf<Int>() }
    // 预览窗口显示数量（支持下拉加载更多）
    var previewDisplayCount by remember { mutableStateOf(10) }
    var isLoadingMorePreview by remember { mutableStateOf(false) }
    // 预览缩略图缓存 - 使用 Map 避免遍历整个列表
    val previewBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    // 预览窗口播放器池 - LRU 策略，最多 3 个播放器
    val previewPlayerPool = remember { ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3) }
    val previewProcessor = remember { PreviewProcessor(context) }

    // 清理预览窗口播放器池
    DisposableEffect(Unit) {
        onDispose {
            previewProcessor.cancel()
            previewPlayerPool.releaseAll()
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

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

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
                // 权限授予后重新加载列表（仅包含本地媒体）
                val items = loadPreviewItems(context)
                videoList = items
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

            scope.launch {
                val items = loadPreviewItems(context)
                videoList = items
            }
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

        // 从预览窗口加载媒体列表
        val items = loadPreviewItems(context)
        videoList = items
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

                                        stopAuroraWallpaper(
                                            context = context,
                                            onSuccess = {
                                                stopButtonState = "success"
                                            },
                                            onError = {
                                                stopButtonState = "error"
                                            }
                                        )

                                        isStopPressed = false
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
                                previewBitmaps.clear()
                                previewProcessor.clear()
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
                        .height(64.dp)
                        .background(themeColors?.topBarBackground ?: MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：菜单按钮
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.settings),
                            tint = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // 中间区域：图标 + 标题 或 搜索框
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 默认显示：图标 + 标题
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isSearchVisible,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                        }

                        // 搜索展开时显示：搜索框
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSearchVisible,
                            enter = expandHorizontally(),
                            exit = shrinkHorizontally()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("搜索媒体...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground,
                                    unfocusedTextColor = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground,
                                    focusedBorderColor = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground,
                                    unfocusedBorderColor = (themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground).copy(alpha = 0.5f),
                                    cursorColor = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground,
                                    focusedPlaceholderColor = (themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground).copy(alpha = 0.6f),
                                    unfocusedPlaceholderColor = (themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground).copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp)
                            )
                        }
                    }

                    // 右侧按钮区域
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // 搜索按钮（始终显示）
                        IconButton(
                            onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    searchQuery = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = themeColors?.topBarContent ?: MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // FAB按钮
                        if (showFab) {
                            IconButton(
                                onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                                modifier = Modifier
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
                }

                // 5秒自动折叠搜索框
                LaunchedEffect(isSearchVisible) {
                    if (isSearchVisible) {
                        delay(5000)
                        isSearchVisible = false
                        searchQuery = ""
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
                        val displayedVideos = remember(videoList, previewDisplayCount, searchQuery) {
                            val filtered = if (searchQuery.isNotBlank()) {
                                videoList.filter { video ->
                                    video.displayName.contains(searchQuery, ignoreCase = true)
                                }
                            } else {
                                videoList
                            }
                            filtered.take(previewDisplayCount)
                        }

                        LaunchedEffect(displayedVideos, gridState) {
                            snapshotFlow {
                                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                val visibleVideos = visibleItems.mapNotNull { itemInfo ->
                                    displayedVideos.getOrNull(itemInfo.index)
                                }

                                // 计算可见行范围（2列布局）
                                val firstVisibleRow = visibleItems.firstOrNull()?.index?.div(2) ?: 0
                                val lastVisibleRow = visibleItems.lastOrNull()?.index?.div(2) ?: 0

                                // 扩展范围：可见行 + 前后各2行预加载
                                val startRow = (firstVisibleRow - 2).coerceAtLeast(0)
                                val endRow = (lastVisibleRow + 2).coerceAtMost((displayedVideos.size - 1) / 2)

                                // 返回包含firstVisibleRow和lastVisibleRow的数据
                                data class ScrollData(
                                    val visibleVideos: List<VideoItem>,
                                    val startRow: Int,
                                    val endRow: Int,
                                    val firstVisibleRow: Int,
                                    val lastVisibleRow: Int
                                )
                                ScrollData(visibleVideos, startRow, endRow, firstVisibleRow, lastVisibleRow)
                            }.collect { scrollData ->
                                // 逐行扫描：按行优先级提交预览请求
                                val rowPrioritizedVideos = mutableListOf<VideoItem>()

                                // 先加载可见行
                                for (row in scrollData.startRow..scrollData.endRow) {
                                    val index1 = row * 2
                                    val index2 = row * 2 + 1

                                    if (index1 < displayedVideos.size) {
                                        rowPrioritizedVideos.add(displayedVideos[index1])
                                    }
                                    if (index2 < displayedVideos.size) {
                                        rowPrioritizedVideos.add(displayedVideos[index2])
                                    }
                                }

                                // 提交预览请求（缩略图）
                                previewProcessor.submit(
                                    items = rowPrioritizedVideos.map { video ->
                                        PreviewProcessor.PreviewRequest(
                                            id = video.id,
                                            uri = video.uri,
                                            hasPreview = previewBitmaps.containsKey(video.id),
                                            mediaType = video.mediaType
                                        )
                                    },
                                    displayMode = mediaDisplayMode,
                                    onPreviewReady = { id, uri, bitmap ->
                                        // 直接更新缓存，避免遍历整个列表
                                        previewBitmaps[id] = bitmap
                                    }
                                )


                                // 增量回收：只清理远离可见区域的预览（保留更大范围）
                                // 保留可见行 + 前后10行，避免点击时误删
                                val extendedStartRow = (scrollData.firstVisibleRow - 10).coerceAtLeast(0)
                                val extendedEndRow = (scrollData.lastVisibleRow + 10).coerceAtMost((displayedVideos.size - 1) / 2)
                                val extendedActiveIds = mutableSetOf<Int>()
                                for (row in extendedStartRow..extendedEndRow) {
                                    val index1 = row * 2
                                    val index2 = row * 2 + 1
                                    if (index1 < displayedVideos.size) {
                                        extendedActiveIds.add(displayedVideos[index1].id)
                                    }
                                    if (index2 < displayedVideos.size) {
                                        extendedActiveIds.add(displayedVideos[index2].id)
                                    }
                                }
                                val keysToRemove = previewBitmaps.keys.filter { it !in extendedActiveIds }
                                if (keysToRemove.isNotEmpty()) {
                                    android.util.Log.d("PreviewCleanup", "🗑️ Removing ${keysToRemove.size} thumbnails out of range")
                                }
                                keysToRemove.forEach { previewBitmaps.remove(it) }

                                // 三态逻辑：滑出屏幕才销毁；只要还在保留范围内，就允许维持暂停态
                                val retainedIdsToRemove = retainedPlayerIds.filter { it !in extendedActiveIds }
                                if (retainedIdsToRemove.isNotEmpty()) {
                                    android.util.Log.d("PreviewCleanup", "🗑️ Destroying ${retainedIdsToRemove.size} retained players out of range")
                                    retainedPlayerIds.removeAll(retainedIdsToRemove.toSet())
                                }

                                // 如果当前播放目标滑出范围，先暂停并取消选中
                                selectedVideoId?.let { currentSelected ->
                                    if (currentSelected !in extendedActiveIds) {
                                        android.util.Log.d("PreviewCleanup", "🚫 Selected video $currentSelected out of range, deselecting")
                                        selectedVideoId = null
                                    }
                                }
                            }
                        }

                        // 监听滚动事件，滚动时隐藏本地视频库
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.isScrollInProgress }
                                .collect { isScrolling ->
                                    // 滚动时的处理逻辑（如果需要）
                                }
                        }

                        // 监听滚动位置，接近底部时自动加载更多
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { lastVisibleIndex ->
                                    if (lastVisibleIndex != null && !isLoadingMorePreview) {
                                        val totalItems = previewDisplayCount
                                        // 当滚动到倒数第3个时，加载更多
                                        if (lastVisibleIndex >= totalItems - 3 && previewDisplayCount < videoList.size) {
                                            isLoadingMorePreview = true
                                            kotlinx.coroutines.delay(100) // 短暂延迟避免频繁触发
                                            previewDisplayCount = minOf(previewDisplayCount + 10, videoList.size)
                                            isLoadingMorePreview = false
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
                            // 优化：减少过度滚动，提升稳定性
                            userScrollEnabled = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pointerInput(displayedVideos) {
                                }
                        ) {
                            itemsIndexed(displayedVideos, key = { _, video -> video.id }) { _, video ->
                                VideoGridItem(
                                    video = video,
                                    previewBitmap = previewBitmaps[video.id],
                                    isSelected = selectedVideoId == video.id,
                                    shouldRetainPlayer = video.id in retainedPlayerIds,
                                    themeColors = themeColors,
                                    playerPool = previewPlayerPool,
                                    pressedCardId = pressedCardId,
                                    onVideoTouch = { videoId ->
                                        android.util.Log.d("VideoClick", "🎯 Select video $videoId, current: $selectedVideoId")
                                        if (videoId == -1) {
                                            selectedVideoId = null
                                            pressedCardId = null
                                            android.util.Log.d("VideoClick", "   Deselected, now: null")
                                        } else {
                                            if (videoId !in retainedPlayerIds) {
                                                retainedPlayerIds.add(videoId)
                                                android.util.Log.d("VideoClick", "   Retain player for: $videoId")
                                            }
                                            pressedCardId = videoId
                                            selectedVideoId = videoId
                                            android.util.Log.d("VideoClick", "   Selected: $selectedVideoId")
                                        }
                                    },
                                    onPointerMove = { sourceVideoId, rawX, rawY ->
                                        val hitCardId = cardBounds.entries.firstOrNull { entry ->
                                            val bounds = entry.value
                                            rawX >= bounds.left && rawX <= bounds.right && rawY >= bounds.top && rawY <= bounds.bottom
                                        }?.key

                                        if (hitCardId == null) {
                                            if (selectedVideoId != null || pressedCardId != null) {
                                                android.util.Log.d("VideoTouch", "⏸️ Pointer left all cards, pause preview")
                                            }
                                            selectedVideoId = null
                                            pressedCardId = null
                                        } else {
                                            if (hitCardId !in retainedPlayerIds) {
                                                retainedPlayerIds.add(hitCardId)
                                            }
                                            if (pressedCardId != hitCardId) {
                                                android.util.Log.d("VideoTouch", "🔄 Pointer moved from $pressedCardId to $hitCardId (source $sourceVideoId)")
                                            }
                                            pressedCardId = hitCardId
                                            selectedVideoId = hitCardId
                                        }
                                    },
                                    onPointerRelease = {
                                        android.util.Log.d("VideoTouch", "👆 Pointer released from card $pressedCardId")
                                        pressedCardId = null
                                        selectedVideoId = null
                                    },
                                    onBoundsChanged = { id, bounds ->
                                        cardBounds[id] = bounds
                                    },
                                    onBoundsDisposed = { id ->
                                        cardBounds.remove(id)
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
                                    onVideoLongPress = {},
                                    displayMode = mediaDisplayMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoGridItem(
    video: VideoItem,
    previewBitmap: Bitmap?,
    isSelected: Boolean,
    shouldRetainPlayer: Boolean,
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    playerPool: ai.wallpaper.aurora.utils.LRUPlayerPool,
    pressedCardId: Int?,
    onVideoTouch: (Int) -> Unit,
    onPointerMove: (Int, Float, Float) -> Unit,
    onPointerRelease: () -> Unit,
    onBoundsChanged: (Int, Rect) -> Unit,
    onBoundsDisposed: (Int) -> Unit,
    onVideoClick: (Uri?) -> Unit = {},
    onVideoLongPress: (Int) -> Unit = {},
    displayMode: String = "fit"
) {
    val deleteProgress = 0f

    // 按 URI 延迟创建播放器：只有进入播放/暂停生命周期后才创建，避免未触达卡片提前占用播放器
    val uriKey = video.uri?.toString() ?: ""
    val shouldHavePlayer = shouldRetainPlayer && uriKey.isNotEmpty() && video.uri != null && video.mediaType == MediaType.VIDEO
    val exoPlayer = remember(shouldHavePlayer, uriKey) {
        if (shouldHavePlayer) {
            playerPool.getOrCreate(uriKey, video.uri!!)
        } else {
            null
        }
    }

    // 播放器准备状态 - 只依赖 exoPlayer，不依赖 isSelected
    var isPlayerReady by remember(exoPlayer) {
        mutableStateOf(false).also {
            android.util.Log.d("PlayerState", "🔄 Reset isPlayerReady for video ${video.id}")
        }
    }

    // 监听播放器状态并在离开保留态时解绑监听
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose { }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateStr = when(playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("PlayerState", "🎬 Video ${video.id} state changed: $stateStr")

                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlayerReady = true
                        android.util.Log.d("PlayerState", "✅ Video ${video.id} ready to play")
                    }
                    Player.STATE_IDLE -> {
                        isPlayerReady = false
                        android.util.Log.w("PlayerState", "⚠️ Video ${video.id} went to IDLE state")
                    }
                    Player.STATE_ENDED -> {
                        android.util.Log.d("PlayerState", "🔁 Video ${video.id} ended, looping")
                    }
                }
            }
        }

        android.util.Log.d("PlayerState", "👂 Start listening video ${video.id}, current state: ${exoPlayer.playbackState}")
        exoPlayer.addListener(listener)

        if (exoPlayer.playbackState == Player.STATE_READY) {
            isPlayerReady = true
            android.util.Log.d("PlayerState", "✅ Video ${video.id} already ready")
        }

        onDispose {
            exoPlayer.removeListener(listener)
            android.util.Log.d("PlayerState", "🧹 Cleanup for video ${video.id}")
        }
    }

    // 不在这里释放，由 LRU 池和保留范围共同管理
    DisposableEffect(uriKey) {
        onDispose {
            onBoundsDisposed(video.id)
            // 播放器由 LRU 池管理，不需要手动释放
        }
    }

    // 根据选中状态控制播放
    LaunchedEffect(isSelected, exoPlayer) {
        exoPlayer?.let { player ->
            try {
                if (isSelected) {
                    android.util.Log.d("VideoPreview", "▶️ Playing video ${video.id}, player state: ${player.playbackState}")
                    player.playWhenReady = true
                    player.play()
                } else {
                    android.util.Log.d("VideoPreview", "⏸️ Pausing video ${video.id}, player state: ${player.playbackState}")
                    player.playWhenReady = false
                    player.pause()
                    // 不要 seekTo(0)，避免显示黑色第一帧
                    // 保持当前位置，下次播放从当前位置继续
                }
            } catch (e: IllegalStateException) {
                // 播放器已被释放，忽略
                android.util.Log.w("VideoPreview", "⚠️ Player already released for video ${video.id}: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(video.id, coordinates.boundsInRoot())
            }
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
            .pointerInput(pressedCardId) {
                detectTapGestures(
                    onPress = {
                        android.util.Log.d("VideoClick", "👇 Press down on video ${video.id}")
                        onVideoTouch(video.id)
                        val released = tryAwaitRelease()
                        if (released) {
                            onPointerRelease()
                        }
                    },
                    onTap = {
                        // 快速点击应用壁纸
                        onVideoClick(video.uri)
                    }
                )
            }
            .pointerInteropFilter { event ->
                if (pressedCardId == null) {
                    return@pointerInteropFilter false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_DOWN -> {
                        onPointerMove(video.id, event.rawX, event.rawY)
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        onPointerRelease()
                    }
                }
                false
            }
    ) {
        // 底层：始终显示缩略图（避免黑屏）
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

        // 上层：选中时叠加播放器（仅视频且播放器已准备好）
        if (isSelected && exoPlayer != null && video.mediaType == MediaType.VIDEO && isPlayerReady) {
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
                            // 设置背景透明，让底层缩略图可见
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
        }
    }
}

private fun isAuroraWallpaperActive(context: Context): Boolean {
    val wallpaperInfo = android.app.WallpaperManager.getInstance(context).wallpaperInfo ?: return false
    return wallpaperInfo.packageName == context.packageName &&
        wallpaperInfo.serviceName == ai.wallpaper.aurora.service.VideoLiveWallpaperService::class.java.name
}

/**
 * 统一停止 Aurora 壁纸功能
 * 包括：主屏动态壁纸 + 锁屏壁纸（恢复默认）
 */
private fun stopAuroraWallpaper(
    context: Context,
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    try {
        var hasActiveWallpaper = false
        val wallpaperManager = android.app.WallpaperManager.getInstance(context)

        // 1. 检查并停止主屏动态壁纸
        val wallpaperInfo = wallpaperManager.wallpaperInfo
        if (wallpaperInfo != null &&
            wallpaperInfo.packageName == context.packageName &&
            wallpaperInfo.serviceName == VideoLiveWallpaperService::class.java.name) {
            // 清除 Aurora 主屏动态壁纸
            android.util.Log.d("MainActivity", "Clearing home screen live wallpaper")
            wallpaperManager.clear(android.app.WallpaperManager.FLAG_SYSTEM)
            hasActiveWallpaper = true
        }

        // 2. 检查并清除锁屏壁纸
        // 注意：在 Android 7.0+ (API 24+) 支持独立的锁屏壁纸
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                // 检查锁屏是否使用了 Live Wallpaper
                val lockWallpaperInfo = wallpaperManager.getWallpaperInfo(android.app.WallpaperManager.FLAG_LOCK)
                android.util.Log.d("MainActivity", "Lock screen wallpaper info: $lockWallpaperInfo")

                if (lockWallpaperInfo != null &&
                    lockWallpaperInfo.packageName == context.packageName &&
                    lockWallpaperInfo.serviceName == VideoLiveWallpaperService::class.java.name) {
                    // 锁屏使用了 Aurora 的 Live Wallpaper，清除它
                    android.util.Log.d("MainActivity", "Clearing Aurora lock screen live wallpaper")
                    wallpaperManager.clear(android.app.WallpaperManager.FLAG_LOCK)
                    android.util.Log.d("MainActivity", "Lock screen wallpaper cleared successfully")
                    hasActiveWallpaper = true
                } else {
                    // 锁屏可能使用了静态壁纸，也尝试清除
                    android.util.Log.d("MainActivity", "Attempting to clear lock screen wallpaper (static or other)")
                    wallpaperManager.clear(android.app.WallpaperManager.FLAG_LOCK)
                    hasActiveWallpaper = true
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to clear lock screen wallpaper", e)
                // 即使锁屏壁纸清除失败，如果主屏壁纸清除成功，仍然算作成功
            }
        } else {
            // Android 7.0 以下版本，clear() 会同时清除主屏和锁屏
            if (!hasActiveWallpaper) {
                try {
                    android.util.Log.d("MainActivity", "Clearing wallpaper (both home and lock)")
                    wallpaperManager.clear()
                    hasActiveWallpaper = true
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to clear wallpaper", e)
                }
            }
        }

        if (hasActiveWallpaper) {
            onSuccess()
        } else {
            onError()
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to stop Aurora wallpaper", e)
        onError()
    }
}

private suspend fun loadPreviewItems(context: Context): List<VideoItem> {
    // 并行加载本地视频和图片
    val localVideos = LocalVideoScanner.scanVideos(context, offset = 0, limit = 100)
    val localImages = LocalImageScanner.scanImages(context, offset = 0, limit = 100)

    val localVideoItems = localVideos.map { media ->
        VideoItem(
            id = media.uri.toString().hashCode(),
            uri = media.uri,
            mediaType = MediaType.VIDEO,
            displayName = media.displayName
        )
    }

    val localImageItems = localImages.map { media ->
        VideoItem(
            id = media.uri.toString().hashCode(),
            uri = media.uri,
            mediaType = MediaType.IMAGE,
            displayName = media.displayName
        )
    }

    // 交错排列：左列图片（大拇指易触达），右列视频
    val interleavedItems = mutableListOf<VideoItem>()
    val maxSize = maxOf(localVideoItems.size, localImageItems.size)
    for (i in 0 until maxSize) {
        if (i < localImageItems.size) {
            interleavedItems.add(localImageItems[i])
        }
        if (i < localVideoItems.size) {
            interleavedItems.add(localVideoItems[i])
        }
    }

    return interleavedItems
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

    // 仅保存选中的媒体路径，预览列表始终来自媒体库扫描

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
