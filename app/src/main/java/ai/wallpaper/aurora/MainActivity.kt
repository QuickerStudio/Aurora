package ai.wallpaper.aurora

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ai.wallpaper.aurora.service.UnlockWallpaperService
import ai.wallpaper.aurora.service.VideoLiveWallpaperService
import ai.wallpaper.aurora.ui.theme.AuroraTheme
import ai.wallpaper.aurora.ui.theme.getThemeColors
import ai.wallpaper.aurora.data.WallpaperHistoryManager
import ai.wallpaper.aurora.utils.LocalVideoScanner
import ai.wallpaper.aurora.utils.LocalVideo
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
    val thumbnailUri: Uri? = null
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

    // 视频列表状态 - 从历史记录加载
    var videoList by remember { mutableStateOf(listOf<VideoItem>()) }
    var selectedVideoId by remember { mutableStateOf<Int?>(null) }
    // 保存 hashCode 到原始 ID 的映射
    var videoIdMap by remember { mutableStateOf(mapOf<Int, String>()) }

    // 本地视频库状态
    var localVideos by remember { mutableStateOf(listOf<LocalVideo>()) }
    var localVideoOffset by remember { mutableStateOf(0) }
    var isLoadingLocalVideos by remember { mutableStateOf(false) }
    var isLocalLibraryVisible by remember { mutableStateOf(true) }

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

    // 获取当前主题颜色配置
    val themeColors = if (!followSystemTheme) {
        getThemeColors(selectedTheme)
    } else {
        null
    }
    val websiteButtonBackground = lerp(
        themeColors?.buttonBackground ?: MaterialTheme.colorScheme.primary,
        Color.White,
        0.3f
    )
    val websiteButtonContent = themeColors?.buttonContent ?: MaterialTheme.colorScheme.onPrimary
    val fabButtonBackground = lerp(
        themeColors?.buttonBackground ?: MaterialTheme.colorScheme.primary,
        Color.White,
        0.3f
    )
    val fabButtonContent = themeColors?.buttonContent ?: MaterialTheme.colorScheme.onPrimary

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // 可以显示权限说明
        }
    }

    // 视频选择器
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            saveVideoUri(context, it)
            VideoLiveWallpaperService.setToWallPaper(context)
            // 重新加载历史记录
            val history = WallpaperHistoryManager.loadHistory(context)
            val idMap = mutableMapOf<Int, String>()
            videoList = history.map { item ->
                val hashId = item.id.hashCode()
                idMap[hashId] = item.id
                VideoItem(
                    id = hashId,
                    uri = Uri.parse(item.videoUri)
                )
            }
            videoIdMap = idMap
        }
    }

    LaunchedEffect(Unit) {
        // 请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 从历史记录加载视频列表
        val history = WallpaperHistoryManager.loadHistory(context)
        val idMap = mutableMapOf<Int, String>()
        videoList = history.map { item ->
            val hashId = item.id.hashCode()
            idMap[hashId] = item.id
            VideoItem(
                id = hashId,
                uri = Uri.parse(item.videoUri)
            )
        }
        videoIdMap = idMap

        // 加载本地视频库
        isLoadingLocalVideos = true
        localVideos = LocalVideoScanner.scanVideos(context, offset = 0, limit = 20)
        localVideoOffset = 20
        isLoadingLocalVideos = false
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
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

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
                                    VideoLiveWallpaperService.setToWallPaper(context)
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
                            painter = androidx.compose.ui.res.painterResource(R.drawable.aurora_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.Center)
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
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.aurora_icon),
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
                        }
                    }
                }
            }
        }
    ) {
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
                            painter = androidx.compose.ui.res.painterResource(R.drawable.aurora_icon),
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

                    // FAB按钮 - 右侧
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
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.aurora_icon),
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

                        // 监听滚动事件，滚动时隐藏本地视频库
                        LaunchedEffect(gridState) {
                            snapshotFlow { gridState.isScrollInProgress }
                                .collect { isScrolling ->
                                    if (isScrolling) {
                                        isLocalLibraryVisible = false
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
                            items(videoList) { video ->
                                VideoGridItem(
                                    video = video,
                                    isSelected = selectedVideoId == video.id,
                                    themeColors = themeColors,
                                    onVideoTouch = { videoId ->
                                        selectedVideoId = if (selectedVideoId == videoId) null else videoId
                                    },
                                    onVideoClick = { videoUri ->
                                        // 直接切换壁纸路径，不打开系统壁纸选择器
                                        videoUri?.let { uri ->
                                            saveVideoPath(context, uri.toString())
                                        }
                                    },
                                    onVideoLongPress = { videoId ->
                                        // 长按删除历史记录
                                        val originalId = videoIdMap[videoId]
                                        originalId?.let { id ->
                                            WallpaperHistoryManager.deleteHistory(context, id)
                                            // 重新加载历史记录
                                            val history = WallpaperHistoryManager.loadHistory(context)
                                            val idMap = mutableMapOf<Int, String>()
                                            videoList = history.map { item ->
                                                val hashId = item.id.hashCode()
                                                idMap[hashId] = item.id
                                                VideoItem(
                                                    id = hashId,
                                                    uri = Uri.parse(item.videoUri)
                                                )
                                            }
                                            videoIdMap = idMap
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 本地视频库横向滑动栏
                    LocalVideoLibrary(
                        localVideos = localVideos,
                        themeColors = themeColors,
                        isLoading = isLoadingLocalVideos,
                        autoHideTimer = autoHideTimer,
                        isVisible = isLocalLibraryVisible,
                        onVisibilityChange = { isVisible ->
                            isLocalLibraryVisible = isVisible
                        },
                        onLoadMore = {
                            scope.launch {
                                if (!isLoadingLocalVideos) {
                                    isLoadingLocalVideos = true
                                    val newVideos = LocalVideoScanner.scanVideos(
                                        context,
                                        offset = localVideoOffset,
                                        limit = 20
                                    )
                                    localVideos = localVideos + newVideos
                                    localVideoOffset += 20
                                    isLoadingLocalVideos = false
                                }
                            }
                        },
                        onVideoClick = { video ->
                            // 直接保存视频路径，不打开系统壁纸选择器
                            saveVideoUri(context, video.uri)
                            // 添加到历史记录
                            val history = WallpaperHistoryManager.loadHistory(context)
                            videoList = history.map { item ->
                                VideoItem(
                                    id = item.id.hashCode(),
                                    uri = Uri.parse(item.videoUri)
                                )
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
    onVideoTouch: (Int) -> Unit,
    onVideoClick: (Uri?) -> Unit = {},
    onVideoLongPress: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            video.uri?.let { uri ->
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                volume = 0f // 静音
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            exoPlayer.play()
            isPlaying = true
        } else {
            exoPlayer.pause()
            exoPlayer.seekTo(0)
            isPlaying = false
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .graphicsLayer {
                // 顺时针旋转
                rotationZ = deleteProgress * 360f
                // 缩放消失
                scaleX = 1f - deleteProgress
                scaleY = 1f - deleteProgress
                alpha = 1f - deleteProgress
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = (themeColors?.cardBorder ?: MaterialTheme.colorScheme.primary).copy(
                    alpha = 1f - deleteProgress * 0.8f
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
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
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
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
    // 持久化 URI 权限
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        android.util.Log.d("MainActivity", "Persistable URI permission granted for: $uri")
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to take persistable URI permission", e)
    }

    // 保存 URI 路径
    context.openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
        it.write(uri.toString().toByteArray())
    }

    // 添加到历史记录
    WallpaperHistoryManager.addHistory(context, uri)

    android.util.Log.d("MainActivity", "Video URI saved: $uri")
}

private fun saveVideoPath(context: Context, path: String) {
    context.openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
        it.write(path.toByteArray())
    }
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
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    isLoading: Boolean,
    autoHideTimer: Int,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (LocalVideo) -> Unit
) {
    val listState = rememberLazyListState()
    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 400.dp,
        animationSpec = tween(durationMillis = 300)
    )

    // 自动隐藏倒计时
    LaunchedEffect(isVisible, autoHideTimer) {
        if (isVisible) {
            delay((autoHideTimer * 1000).toLong())
            onVisibilityChange(false)
        }
    }

    // 只在显示或动画中时占用完整空间，隐藏后只显示滑动区域
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isVisible || offsetX < 400.dp) 176.dp else 40.dp)
    ) {
        if (isVisible || offsetX < 400.dp) {
            // 横向滑动视频列表
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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

                items(localVideos) { video ->
                    LocalVideoCard(
                        video = video,
                        themeColors = themeColors,
                        onClick = { onVideoClick(video) }
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
    themeColors: ai.wallpaper.aurora.ui.theme.ThemeColors?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
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
            .clickable { onClick() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 视频名称覆盖层
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

    LaunchedEffect(Unit) {
        exoPlayer.play()
    }
}
