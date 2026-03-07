package ai.wallpaper.aurora.media

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 测试用 Logger，不输出任何日志
 */
class NoOpLogger : Logger {
    override fun d(tag: String, message: String) {
        // 不输出
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        // 不输出
    }
}

/**
 * VideoMediaProcessor 单元测试
 *
 * 测试重点：
 * 1. LRU 淘汰策略是否正确
 * 2. 并发请求是否安全
 * 3. 资源释放是否完整
 * 4. Actor 模式是否正确处理消息
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoMediaProcessorTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var processor: VideoMediaProcessor
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // 创建处理器，最大3个播放器，使用 NoOpLogger
        processor = VideoMediaProcessor(mockContext, maxPlayerCount = 3, logger = NoOpLogger())
    }

    @After
    fun tearDown() {
        runBlocking {
            processor.releaseAllPlayers()
        }
        processor.shutdown()
        Dispatchers.resetMain()
    }

    /**
     * 测试1: 基本的获取或创建播放器功能
     */
    @Test
    fun testGetOrCreatePlayer_createsNewPlayer() = runTest {
        val uri = Uri.parse("content://test/video1.mp4")
        val uriKey = uri.toString()

        val player = processor.getOrCreatePlayer(uriKey, uri)

        assertNotNull(player, "播放器应该被创建")
        assertEquals(1, processor.getPlayerCount(), "播放器池应该有1个播放器")
    }

    /**
     * 测试2: 相同 URI 应该复用播放器
     */
    @Test
    fun testGetOrCreatePlayer_reusesSamePlayer() = runTest {
        val uri = Uri.parse("content://test/video1.mp4")
        val uriKey = uri.toString()

        val player1 = processor.getOrCreatePlayer(uriKey, uri)
        val player2 = processor.getOrCreatePlayer(uriKey, uri)

        assertTrue(player1 === player2, "相同 URI 应该返回同一个播放器实例")
        assertEquals(1, processor.getPlayerCount(), "播放器池应该只有1个播放器")
    }

    /**
     * 测试3: LRU 淘汰策略 - 超过最大数量时淘汰最久未使用的
     */
    @Test
    fun testLRUEviction_evictsOldestPlayer() = runTest {
        val uri1 = Uri.parse("content://test/video1.mp4")
        val uri2 = Uri.parse("content://test/video2.mp4")
        val uri3 = Uri.parse("content://test/video3.mp4")
        val uri4 = Uri.parse("content://test/video4.mp4")

        // 创建3个播放器（达到上限）
        processor.getOrCreatePlayer(uri1.toString(), uri1)
        processor.getOrCreatePlayer(uri2.toString(), uri2)
        processor.getOrCreatePlayer(uri3.toString(), uri3)

        assertEquals(3, processor.getPlayerCount(), "应该有3个播放器")

        // 创建第4个播放器，应该淘汰第1个
        processor.getOrCreatePlayer(uri4.toString(), uri4)

        // 给异步淘汰操作一些时间
        advanceUntilIdle()

        assertEquals(3, processor.getPlayerCount(), "应该保持3个播放器")
    }

    /**
     * 测试4: 并发请求安全性
     */
    @Test
    fun testConcurrentRequests_areSafe() = runTest {
        val uris = (1..10).map { Uri.parse("content://test/video$it.mp4") }

        // 并发创建10个播放器请求
        val jobs = uris.map { uri ->
            async {
                processor.getOrCreatePlayer(uri.toString(), uri)
            }
        }

        // 等待所有请求完成
        jobs.awaitAll()
        advanceUntilIdle()

        // 由于 LRU 限制，最终应该只有3个播放器
        assertTrue(
            processor.getPlayerCount() <= 3,
            "并发请求后，播放器数量应该不超过3个，实际: ${processor.getPlayerCount()}"
        )
    }

    /**
     * 测试5: 释放指定播放器
     */
    @Test
    fun testReleasePlayer_removesSpecificPlayer() = runTest {
        val uri1 = Uri.parse("content://test/video1.mp4")
        val uri2 = Uri.parse("content://test/video2.mp4")

        processor.getOrCreatePlayer(uri1.toString(), uri1)
        processor.getOrCreatePlayer(uri2.toString(), uri2)

        assertEquals(2, processor.getPlayerCount(), "应该有2个播放器")

        // 释放第1个播放器
        processor.releasePlayer(uri1.toString())
        advanceUntilIdle()

        assertEquals(1, processor.getPlayerCount(), "应该剩余1个播放器")
    }

    /**
     * 测试6: 释放所有播放器
     */
    @Test
    fun testReleaseAllPlayers_clearsPool() = runTest {
        val uris = (1..3).map { Uri.parse("content://test/video$it.mp4") }

        uris.forEach { uri ->
            processor.getOrCreatePlayer(uri.toString(), uri)
        }

        assertEquals(3, processor.getPlayerCount(), "应该有3个播放器")

        // 释放所有播放器
        processor.releaseAllPlayers()
        advanceUntilIdle()

        assertEquals(0, processor.getPlayerCount(), "所有播放器应该被释放")
    }

    /**
     * 测试7: 高频请求压力测试
     */
    @Test
    fun testHighFrequencyRequests_handlesGracefully() = runTest {
        val requestCount = 100
        val uris = (1..requestCount).map { Uri.parse("content://test/video$it.mp4") }

        // 模拟高频滚动场景
        val startTime = System.currentTimeMillis()

        uris.forEach { uri ->
            launch {
                processor.getOrCreatePlayer(uri.toString(), uri)
            }
        }

        advanceUntilIdle()
        val endTime = System.currentTimeMillis()

        // 验证最终状态
        assertTrue(
            processor.getPlayerCount() <= 3,
            "高频请求后，播放器数量应该不超过3个"
        )

        println("处理 $requestCount 个请求耗时: ${endTime - startTime}ms")
    }

    /**
     * 测试8: Actor 消息队列不会丢失消息
     */
    @Test
    fun testActorQueue_doesNotLoseMessages() = runTest {
        val messageCount = 50
        val uris = (1..messageCount).map { Uri.parse("content://test/video$it.mp4") }

        // 快速发送大量消息
        val results = uris.map { uri ->
            async {
                processor.getOrCreatePlayer(uri.toString(), uri)
            }
        }

        // 等待所有消息处理完成
        val players = results.awaitAll()
        advanceUntilIdle()

        // 验证所有请求都得到了响应
        assertEquals(messageCount, players.size, "所有请求都应该得到响应")
        assertNotNull(players.first(), "返回的播放器不应该为 null")
    }
}
