package ai.wallpaper.aurora

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.wallpaper.aurora.utils.LRUPlayerPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LRUPlayerPoolStressTest {

    private lateinit var context: Context
    private lateinit var playerPool: LRUPlayerPool

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        playerPool = LRUPlayerPool(context, maxSize = 3)
    }

    @After
    fun teardown() = runBlocking {
        withContext(Dispatchers.Main) {
            playerPool.releaseAll()
        }
    }

    @Test
    fun testRapidGetOrCreate() = runBlocking {
        // 测试快速创建和访问播放器（必须在主线程）
        withContext(Dispatchers.Main) {
            repeat(20) { index ->
                val uri = Uri.parse("content://test/video/$index")
                val player = playerPool.getOrCreate("video_$index", uri)
                assert(player != null)
                delay(50)
            }

            println("Rapid create test: pool size = ${playerPool.size()}")
            assert(playerPool.size() <= 3) // 应该不超过最大值
        }
    }

    @Test
    fun testLRUEviction() = runBlocking {
        // 测试LRU淘汰机制
        withContext(Dispatchers.Main) {
            val uris = (0 until 10).map { Uri.parse("content://test/video/$it") }

            // 创建10个播放器，应该只保留最后3个
            uris.forEachIndexed { index, uri ->
                playerPool.getOrCreate("video_$index", uri)
                delay(100)
            }

            println("LRU eviction test: pool size = ${playerPool.size()}")
            assert(playerPool.size() == 3)

            // 检查最后3个是否在池中
            assert(playerPool.contains("video_7"))
            assert(playerPool.contains("video_8"))
            assert(playerPool.contains("video_9"))
            assert(!playerPool.contains("video_0"))
        }
    }

    @Test
    fun testRepeatedAccess() = runBlocking {
        // 测试重复访问同一个播放器
        withContext(Dispatchers.Main) {
            val uri = Uri.parse("content://test/video/repeated")

            repeat(100) {
                val player1 = playerPool.getOrCreate("video_repeated", uri)
                val player2 = playerPool.getOrCreate("video_repeated", uri)
                assert(player1 === player2) // 应该是同一个实例
                delay(10)
            }

            println("Repeated access test: pool size = ${playerPool.size()}")
            assert(playerPool.size() == 1)
        }
    }

    @Test
    fun testMemoryPressure() = runBlocking {
        // 测试内存压力下的表现
        withContext(Dispatchers.Main) {
            repeat(100) { batch ->
                repeat(5) { index ->
                    val uri = Uri.parse("content://test/video/$batch/$index")
                    playerPool.getOrCreate("video_${batch}_$index", uri)
                }

                if (batch % 10 == 0) {
                    System.gc()
                    delay(100)
                }
            }

            println("Memory pressure test: pool size = ${playerPool.size()}")
            assert(playerPool.size() <= 3)
        }
    }

    @Test
    fun testReleaseAndRecreate() = runBlocking {
        // 测试释放后重新创建
        withContext(Dispatchers.Main) {
            val uri = Uri.parse("content://test/video/release")

            repeat(10) {
                val player = playerPool.getOrCreate("video_release", uri)
                assert(player != null)
                delay(100)

                playerPool.release("video_release")
                assert(!playerPool.contains("video_release"))
                delay(100)
            }

            println("Release and recreate test completed")
        }
    }

    @Test
    fun testAccessPattern() = runBlocking {
        // 测试访问模式（模拟用户滚动）
        withContext(Dispatchers.Main) {
            val uris = (0 until 20).map { Uri.parse("content://test/video/$it") }

            // 向下滚动
            uris.forEachIndexed { index, uri ->
                playerPool.getOrCreate("video_$index", uri)
                delay(100)
            }

            // 向上滚动
            uris.reversed().forEachIndexed { index, uri ->
                val originalIndex = uris.size - 1 - index
                playerPool.getOrCreate("video_$originalIndex", uri)
                delay(100)
            }

            println("Access pattern test: pool size = ${playerPool.size()}")
            assert(playerPool.size() <= 3)
        }
    }
}
