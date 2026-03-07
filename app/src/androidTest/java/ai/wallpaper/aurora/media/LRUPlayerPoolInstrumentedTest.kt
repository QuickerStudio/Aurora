package ai.wallpaper.aurora.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LRUPlayerPool 真机测试
 *
 * 在真实设备上运行，验证：
 * 1. 播放器数量是否严格控制在 3 个以内
 * 2. 内存使用是否合理
 * 3. LRU 淘汰是否正常工作
 */
@RunWith(AndroidJUnit4::class)
class LRUPlayerPoolInstrumentedTest {

    private lateinit var context: Context
    private lateinit var playerPool: ai.wallpaper.aurora.utils.LRUPlayerPool

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        playerPool = ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3)
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            playerPool.releaseAll()
        }
    }

    /**
     * 测试1: 基本功能 - 创建3个播放器
     */
    @Test
    fun test_createThreePlayers_shouldNotExceedLimit() = runBlocking {
        println("\n=== 测试1: 创建3个播放器 ===")

        val memoryBefore = getMemoryUsage()
        println("初始内存: ${memoryBefore}MB")

        // 创建3个播放器
        val uris = (1..3).map {
            Uri.parse("android.resource://${context.packageName}/raw/sample_video_$it")
        }

        uris.forEachIndexed { index, uri ->
            withContext(Dispatchers.Main) {
                val player = playerPool.getOrCreate(uri.toString(), uri)
                val currentMemory = getMemoryUsage()
                println("创建播放器 ${index + 1}: 内存=${currentMemory}MB, 池大小=${playerPool.size()}")

                assertTrue("播放器数量不应超过3个", playerPool.size() <= 3)
            }
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore
        println("内存增长: ${memoryIncrease}MB")
        println("最终池大小: ${playerPool.size()}")

        assertTrue("3个播放器内存增长应该 < 50MB，实际: ${memoryIncrease}MB", memoryIncrease < 50)
    }

    /**
     * 测试2: LRU 淘汰 - 创建10个播放器，验证淘汰
     */
    @Test
    fun test_createTenPlayers_shouldEvictOldest() = runBlocking {
        println("\n=== 测试2: 创建10个播放器，验证LRU淘汰 ===")

        val memoryBefore = getMemoryUsage()
        println("初始内存: ${memoryBefore}MB")

        // 创建10个播放器
        repeat(10) { index ->
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("content://test/video_$index")
                playerPool.getOrCreate(uri.toString(), uri)

                val currentMemory = getMemoryUsage()
                val poolSize = playerPool.size()

                println("创建播放器 ${index + 1}: 内存=${currentMemory}MB, 池大小=${poolSize}")

                // 关键断言：池大小永远不超过3
                assertTrue("第${index + 1}次创建后，池大小=${poolSize}，不应超过3", poolSize <= 3)
            }
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore
        println("内存增长: ${memoryIncrease}MB")
        println("最终池大小: ${playerPool.size()}")

        // 验证最终状态
        assertTrue("最终应该只有3个播放器", playerPool.size() == 3)
        assertTrue("内存增长应该 < 50MB，实际: ${memoryIncrease}MB", memoryIncrease < 50)
    }

    /**
     * 测试3: 快速连续创建 - 模拟快速滚动
     */
    @Test
    fun test_rapidCreation_shouldHandleGracefully() = runBlocking {
        println("\n=== 测试3: 快速连续创建50个播放器 ===")

        val memoryBefore = getMemoryUsage()
        println("初始内存: ${memoryBefore}MB")

        val startTime = System.currentTimeMillis()

        // 快速创建50个播放器
        repeat(50) { index ->
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("content://test/video_$index")
                playerPool.getOrCreate(uri.toString(), uri)

                if (index % 10 == 0) {
                    val currentMemory = getMemoryUsage()
                    println("创建了 ${index + 1} 个播放器: 内存=${currentMemory}MB, 池大小=${playerPool.size()}")
                }

                assertTrue("快速创建时池大小也不应超过3", playerPool.size() <= 3)
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        println("创建50个播放器耗时: ${duration}ms")
        println("内存增长: ${memoryIncrease}MB")
        println("最终池大小: ${playerPool.size()}")

        assertTrue("最终应该只有3个播放器", playerPool.size() == 3)
        assertTrue("内存增长应该 < 50MB", memoryIncrease < 50)
    }

    /**
     * 测试4: 内存压力测试 - 持续创建100个播放器
     */
    @Test
    fun test_memoryPressure_create100Players() = runBlocking {
        println("\n=== 测试4: 内存压力测试 - 100个播放器 ===")

        val memoryBefore = getMemoryUsage()
        println("初始内存: ${memoryBefore}MB")

        val memorySnapshots = mutableListOf<Int>()

        repeat(100) { index ->
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("content://test/video_$index")
                playerPool.getOrCreate(uri.toString(), uri)

                if (index % 20 == 0) {
                    val currentMemory = getMemoryUsage()
                    memorySnapshots.add(currentMemory)
                    println("创建了 ${index + 1} 个: 内存=${currentMemory}MB, 池大小=${playerPool.size()}")
                }
            }
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        println("\n内存快照: $memorySnapshots")
        println("内存增长: ${memoryIncrease}MB")
        println("最终池大小: ${playerPool.size()}")

        // 验证内存没有持续增长
        val maxMemory = memorySnapshots.maxOrNull() ?: 0
        val minMemory = memorySnapshots.minOrNull() ?: 0
        val memoryVariation = maxMemory - minMemory

        println("内存波动: ${memoryVariation}MB (最高=${maxMemory}MB, 最低=${minMemory}MB)")

        assertTrue("最终应该只有3个播放器", playerPool.size() == 3)
        assertTrue("总内存增长应该 < 50MB", memoryIncrease < 50)
        assertTrue("内存波动应该 < 30MB，说明没有泄漏", memoryVariation < 30)
    }

    /**
     * 测试5: 释放功能测试
     */
    @Test
    fun test_releaseAll_shouldFreeMemory() = runBlocking {
        println("\n=== 测试5: 释放功能测试 ===")

        val memoryBefore = getMemoryUsage()
        println("初始内存: ${memoryBefore}MB")

        // 创建3个播放器
        repeat(3) { index ->
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("content://test/video_$index")
                playerPool.getOrCreate(uri.toString(), uri)
            }
        }

        val memoryAfterCreate = getMemoryUsage()
        println("创建后内存: ${memoryAfterCreate}MB, 池大小: ${playerPool.size()}")

        // 释放所有播放器
        withContext(Dispatchers.Main) {
            playerPool.releaseAll()
        }

        // 强制 GC
        System.gc()
        Thread.sleep(1000)

        val memoryAfterRelease = getMemoryUsage()
        println("释放后内存: ${memoryAfterRelease}MB, 池大小: ${playerPool.size()}")

        val memoryFreed = memoryAfterCreate - memoryAfterRelease
        println("释放的内存: ${memoryFreed}MB")

        assertTrue("释放后池应该为空", playerPool.size() == 0)
        assertTrue("释放后内存应该下降", memoryFreed > 0)
    }

    /**
     * 获取当前内存使用（MB）
     */
    private fun getMemoryUsage(): Int {
        val runtime = Runtime.getRuntime()
        return ((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).toInt()
    }
}
