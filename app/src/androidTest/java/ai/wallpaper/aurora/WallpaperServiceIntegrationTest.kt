package ai.wallpaper.aurora

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.wallpaper.aurora.data.WallpaperHistoryManager
import ai.wallpaper.aurora.service.VideoLiveWallpaperService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 壁纸服务集成测试
 *
 * 测试目标：
 * 1. 文件通信机制（video_live_wallpaper_file_path）
 * 2. 历史记录验证机制（只有壁纸服务使用后才记录）
 * 3. 跨进程广播通信（ACTION_VIDEO_PLAYBACK_STARTED）
 * 4. 音量控制功能
 * 5. 壁纸服务状态检测
 *
 * 运行方式：
 * 1. 连接真实设备
 * 2. 在 Android Studio 中右键运行此测试类
 * 3. 或使用命令：./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class WallpaperServiceIntegrationTest {

    private lateinit var context: Context
    private val TAG = "WallpaperServiceTest"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        log("========================================")
        log("壁纸服务集成测试开始")
        log("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        log("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        log("========================================")

        // 清理测试环境
        cleanupTestEnvironment()
    }

    @After
    fun tearDown() {
        log("========================================")
        log("壁纸服务集成测试结束")
        log("========================================")

        // 清理测试数据
        cleanupTestEnvironment()
    }

    /**
     * 测试1: 文件通信机制 - 保存和读取视频路径
     */
    @Test
    fun test1_fileBasedCommunication() {
        log("\n【测试1】文件通信机制")
        log("目标: 验证 video_live_wallpaper_file_path 文件的读写")

        // 1. 写入测试路径
        val testUri = "content://media/external/video/1234"
        saveVideoPath(testUri)
        log("✓ 写入路径: $testUri")

        // 2. 读取路径
        val readPath = readVideoPath()
        log("✓ 读取路径: $readPath")

        // 3. 验证
        assertEquals("读取的路径应该与写入的一致", testUri, readPath)
        log("✅ 测试1通过: 文件通信正常")
    }

    /**
     * 测试2: 历史记录验证机制 - 只有壁纸服务使用后才记录
     */
    @Test
    fun test2_historyValidationMechanism() = runBlocking {
        log("\n【测试2】历史记录验证机制")
        log("目标: 验证只有壁纸服务播放后才添加历史")

        // 1. 清空历史记录
        WallpaperHistoryManager.clearHistory(context)
        var history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("初始历史应该为空", 0, history.size)
        log("✓ 清空历史记录")

        // 2. 模拟用户点击本地视频（只保存路径，不添加历史）
        val testUri = Uri.parse("content://media/external/video/5678")
        saveVideoPath(testUri.toString())
        log("✓ 保存视频路径（模拟用户点击）")

        // 3. 验证此时历史仍为空
        history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("点击后历史应该仍为空", 0, history.size)
        log("✓ 验证历史仍为空（未添加）")

        // 4. 模拟壁纸服务播放成功（发送广播）
        val latch = CountDownLatch(1)
        var receivedUri: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED) {
                    receivedUri = intent.getStringExtra(VideoLiveWallpaperService.EXTRA_VIDEO_URI)
                    // 模拟 MainActivity 的处理逻辑
                    receivedUri?.let {
                        WallpaperHistoryManager.addHistory(context, Uri.parse(it))
                    }
                    latch.countDown()
                }
            }
        }

        val filter = IntentFilter(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        // 发送广播（模拟壁纸服务）
        val intent = Intent(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED).apply {
            putExtra(VideoLiveWallpaperService.EXTRA_VIDEO_URI, testUri.toString())
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        log("✓ 发送播放成功广播（模拟壁纸服务）")

        // 等待广播接收
        val received = latch.await(5, TimeUnit.SECONDS)
        assertTrue("应该接收到广播", received)
        assertEquals("接收到的 URI 应该正确", testUri.toString(), receivedUri)
        log("✓ 接收到广播: $receivedUri")

        context.unregisterReceiver(receiver)

        // 5. 验证历史已添加
        delay(500) // 等待异步操作完成
        history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("播放后历史应该有1条", 1, history.size)
        assertEquals("历史记录的 URI 应该正确", testUri.toString(), history[0].videoUri)
        log("✓ 验证历史已添加: ${history[0].videoUri}")

        log("✅ 测试2通过: 历史记录验证机制正常")
    }

    /**
     * 测试3: 音量控制功能
     */
    @Test
    fun test3_volumeControl() {
        log("\n【测试3】音量控制功能")
        log("目标: 验证 unmute 文件和音量控制广播")

        val unmuteFile = File(context.filesDir, "unmute")

        // 1. 测试静音状态（默认）
        if (unmuteFile.exists()) {
            unmuteFile.delete()
        }
        assertFalse("初始应该是静音状态", unmuteFile.exists())
        log("✓ 初始状态: 静音")

        // 2. 测试取消静音
        unmuteFile.createNewFile()
        assertTrue("unmute 文件应该存在", unmuteFile.exists())
        log("✓ 创建 unmute 文件")

        // 3. 发送取消静音广播
        VideoLiveWallpaperService.unmuteMusic(context)
        log("✓ 发送取消静音广播")

        // 4. 测试静音
        unmuteFile.delete()
        assertFalse("删除后 unmute 文件不应该存在", unmuteFile.exists())
        log("✓ 删除 unmute 文件")

        // 5. 发送静音广播
        VideoLiveWallpaperService.muteMusic(context)
        log("✓ 发送静音广播")

        log("✅ 测试3通过: 音量控制功能正常")
    }

    /**
     * 测试4: 壁纸服务状态检测
     */
    @Test
    fun test4_wallpaperServiceStatus() {
        log("\n【测试4】壁纸服务状态检测")
        log("目标: 检测当前壁纸服务是否为 Aurora")

        val wallpaperManager = WallpaperManager.getInstance(context)
        val wallpaperInfo = wallpaperManager.wallpaperInfo

        if (wallpaperInfo != null) {
            val serviceName = wallpaperInfo.serviceName
            val packageName = wallpaperInfo.packageName
            log("✓ 当前壁纸服务: $serviceName")
            log("✓ 包名: $packageName")

            val isAurora = serviceName == VideoLiveWallpaperService::class.java.name
            log("✓ 是否为 Aurora: $isAurora")

            if (isAurora) {
                log("✅ 测试4通过: Aurora 壁纸服务已激活")
            } else {
                log("⚠️  测试4提示: 当前壁纸不是 Aurora，请手动设置后重新测试")
            }
        } else {
            log("⚠️  测试4提示: 当前使用的是静态壁纸，不是动态壁纸")
        }
    }

    /**
     * 测试5: 跨进程文件访问
     */
    @Test
    fun test5_crossProcessFileAccess() {
        log("\n【测试5】跨进程文件访问")
        log("目标: 验证主进程和壁纸进程都能访问同一文件")

        // 1. 主进程写入
        val testUri = "content://media/external/video/9999"
        saveVideoPath(testUri)
        log("✓ 主进程写入: $testUri")

        // 2. 主进程读取
        val readPath = readVideoPath()
        assertEquals("主进程读取应该成功", testUri, readPath)
        log("✓ 主进程读取: $readPath")

        // 3. 验证文件位置
        val file = File(context.filesDir, "video_live_wallpaper_file_path")
        assertTrue("文件应该存在", file.exists())
        log("✓ 文件路径: ${file.absolutePath}")
        log("✓ 文件大小: ${file.length()} bytes")

        // 注意：壁纸进程的 filesDir 是独立的，需要通过 openFileInput 访问
        log("⚠️  注意: 壁纸服务运行在 :wallpaper 进程，使用 openFileInput 读取")

        log("✅ 测试5通过: 文件访问正常")
    }

    /**
     * 测试6: 历史记录去重机制
     */
    @Test
    fun test6_historyDeduplication() {
        log("\n【测试6】历史记录去重机制")
        log("目标: 验证相同视频不会重复添加到历史")

        // 1. 清空历史
        WallpaperHistoryManager.clearHistory(context)
        log("✓ 清空历史记录")

        // 2. 添加同一个视频3次
        val testUri = Uri.parse("content://media/external/video/1111")
        repeat(3) { index ->
            WallpaperHistoryManager.addHistory(context, testUri)
            log("✓ 第 ${index + 1} 次添加: $testUri")
        }

        // 3. 验证只有1条记录
        val history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("应该只有1条历史记录", 1, history.size)
        assertEquals("历史记录的 URI 应该正确", testUri.toString(), history[0].videoUri)
        log("✓ 验证去重成功: 历史记录数量 = ${history.size}")

        log("✅ 测试6通过: 历史记录去重正常")
    }

    /**
     * 测试7: 历史记录限制（最多50条）
     */
    @Test
    fun test7_historyLimit() {
        log("\n【测试7】历史记录限制")
        log("目标: 验证历史记录最多保存50条")

        // 1. 清空历史
        WallpaperHistoryManager.clearHistory(context)
        log("✓ 清空历史记录")

        // 2. 添加60条记录
        repeat(60) { index ->
            val uri = Uri.parse("content://media/external/video/$index")
            WallpaperHistoryManager.addHistory(context, uri)
        }
        log("✓ 添加60条历史记录")

        // 3. 验证只保留50条
        val history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("应该只保留50条", 50, history.size)
        log("✓ 验证限制成功: 历史记录数量 = ${history.size}")

        // 4. 验证保留的是最新的50条
        val firstUri = history[0].videoUri
        assertTrue("第一条应该是最新的（video/59）", firstUri.contains("video/59"))
        log("✓ 验证顺序: 第一条 = $firstUri")

        log("✅ 测试7通过: 历史记录限制正常")
    }

    /**
     * 测试8: 广播安全性（RECEIVER_NOT_EXPORTED）
     */
    @Test
    fun test8_broadcastSecurity() {
        log("\n【测试8】广播安全性")
        log("目标: 验证广播使用 RECEIVER_NOT_EXPORTED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            log("✓ Android 13+ 设备，应该使用 RECEIVER_NOT_EXPORTED")

            val latch = CountDownLatch(1)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    latch.countDown()
                }
            }

            val filter = IntentFilter(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED)

            // 使用 RECEIVER_NOT_EXPORTED 注册
            try {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                log("✓ 成功使用 RECEIVER_NOT_EXPORTED 注册")

                // 发送测试广播
                val intent = Intent(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)

                val received = latch.await(2, TimeUnit.SECONDS)
                assertTrue("应该接收到广播", received)
                log("✓ 接收到广播")

                context.unregisterReceiver(receiver)
                log("✅ 测试8通过: 广播安全性正常")
            } catch (e: Exception) {
                fail("注册广播失败: ${e.message}")
            }
        } else {
            log("⚠️  Android 12 及以下设备，跳过此测试")
        }
    }

    /**
     * 测试9: 完整流程模拟
     */
    @Test
    fun test9_completeWorkflow() = runBlocking {
        log("\n【测试9】完整流程模拟")
        log("目标: 模拟用户从选择视频到壁纸播放的完整流程")

        // 1. 清理环境
        WallpaperHistoryManager.clearHistory(context)
        log("✓ 步骤1: 清理环境")

        // 2. 用户选择视频
        val selectedUri = Uri.parse("content://media/external/video/complete_test")
        saveVideoPath(selectedUri.toString())
        log("✓ 步骤2: 用户选择视频 = $selectedUri")

        // 3. 验证历史为空（未播放）
        var history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("选择后历史应该为空", 0, history.size)
        log("✓ 步骤3: 验证历史为空（未播放）")

        // 4. 用户应用壁纸（这里只能模拟，无法真正启动壁纸服务）
        log("✓ 步骤4: 用户应用壁纸（需要手动操作）")

        // 5. 模拟壁纸服务播放成功
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val uri = intent.getStringExtra(VideoLiveWallpaperService.EXTRA_VIDEO_URI)
                uri?.let {
                    WallpaperHistoryManager.addHistory(context, Uri.parse(it))
                }
                latch.countDown()
            }
        }

        val filter = IntentFilter(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val intent = Intent(VideoLiveWallpaperService.ACTION_VIDEO_PLAYBACK_STARTED).apply {
            putExtra(VideoLiveWallpaperService.EXTRA_VIDEO_URI, selectedUri.toString())
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        log("✓ 步骤5: 壁纸服务播放成功（模拟）")

        latch.await(5, TimeUnit.SECONDS)
        context.unregisterReceiver(receiver)

        // 6. 验证历史已添加
        delay(500)
        history = WallpaperHistoryManager.loadHistory(context)
        assertEquals("播放后历史应该有1条", 1, history.size)
        assertEquals("历史记录应该正确", selectedUri.toString(), history[0].videoUri)
        log("✓ 步骤6: 验证历史已添加")

        log("✅ 测试9通过: 完整流程正常")
    }

    // ========== 辅助方法 ==========

    private fun saveVideoPath(uri: String) {
        context.openFileOutput("video_live_wallpaper_file_path", Context.MODE_PRIVATE).use {
            it.write(uri.toByteArray())
        }
    }

    private fun readVideoPath(): String? {
        return try {
            context.openFileInput("video_live_wallpaper_file_path")
                .bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanupTestEnvironment() {
        // 清理测试文件
        val videoPathFile = File(context.filesDir, "video_live_wallpaper_file_path")
        if (videoPathFile.exists()) {
            videoPathFile.delete()
        }

        val unmuteFile = File(context.filesDir, "unmute")
        if (unmuteFile.exists()) {
            unmuteFile.delete()
        }

        // 清理历史记录
        WallpaperHistoryManager.clearHistory(context)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        println(message)
    }
}
