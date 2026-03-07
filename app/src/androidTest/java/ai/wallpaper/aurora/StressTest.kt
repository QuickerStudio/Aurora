package ai.wallpaper.aurora

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aurora 压力测试套件
 *
 * 测试目标：
 * 1. 历史视频卡片的滚动和播放器管理
 * 2. 本地视频库的增量加载和播放器管理
 * 3. 双池并发压力测试
 * 4. 极限场景模拟
 *
 * 关键监控点：
 * - 播放器数量是否超过限制
 * - 内存是否持续增长
 * - LRU 淘汰是否正常
 * - 是否有崩溃或异常
 */
@RunWith(AndroidJUnit4::class)
class StressTest {

    private lateinit var context: Context
    private lateinit var historyPool: ai.wallpaper.aurora.utils.LRUPlayerPool
    private lateinit var localPool: ai.wallpaper.aurora.utils.LRUPlayerPool

    private val TAG = "StressTest"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        historyPool = ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3)
        localPool = ai.wallpaper.aurora.utils.LRUPlayerPool(context, maxSize = 3)

        log("========================================")
        log("压力测试开始")
        log("初始内存: ${getMemoryUsage()}MB")
        log("========================================")
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            historyPool.releaseAll()
            localPool.releaseAll()
        }

        log("========================================")
        log("压力测试结束")
        log("最终内存: ${getMemoryUsage()}MB")
        log("========================================")
    }

    /**
     * 测试1: 历史卡片快速滚动 - 模拟用户疯狂滑动
     */
    @Test
    fun test1_historyCardRapidScroll() = runBlocking {
        log("\n【测试1】历史卡片快速滚动 - 模拟疯狂滑动")
        log("场景: 用户快速滚动历史列表，触发大量播放器创建和销毁")

        val memoryBefore = getMemoryUsage()
        val startTime = System.currentTimeMillis()

        // 模拟滚动100个历史视频卡片
        repeat(100) { index ->
            withContext(Dispatchers.Main) {
                val uri = Uri.parse("content://history/video_$index")
                historyPool.getOrCreate(uri.toString(), uri)

                // 每10个打印一次状态
                if (index % 10 == 0) {
                    val currentMemory = getMemoryUsage()
                    log("  滚动到第 ${index + 1} 个: 内存=${currentMemory}MB, 历史池=${historyPool.size()}")

                    // 断言：历史池不超过3个
                    assertTrue("历史池大小=${historyPool.size()}，不应超过3", historyPool.size() <= 3)
                }
            }

            // 模拟滚动间隔
            delay(10)
        }

        val duration = System.currentTimeMillis() - startTime
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        log("  耗时: ${duration}ms")
        log("  内存增长: ${memoryIncrease}MB")
        log("  最终历史池大小: ${historyPool.size()}")

        assertTrue("历史池最终大小应该=3", historyPool.size() == 3)
        assertTrue("内存增长应该 < 50MB，实际: ${memoryIncrease}MB", memoryIncrease < 50)
        log("✅ 测试1通过")
    }

    /**
     * 测试2: 本地视频库增量加载 - 模拟不断加载更多
     */
    @Test
    fun test2_localLibraryIncrementalLoad() = runBlocking {
        log("\n【测试2】本地视频库增量加载")
        log("场景: 用户滚动本地视频库，触发多次增量加载")

        val memoryBefore = getMemoryUsage()

        // 模拟5次增量加载，每次20个视频
        repeat(5) { batch ->
            log("  第 ${batch + 1} 批加载开始...")

            repeat(20) { index ->
                withContext(Dispatchers.Main) {
                    val globalIndex = batch * 20 + index
                    val uri = Uri.parse("content://local/video_$globalIndex")
                    localPool.getOrCreate(uri.toString(), uri)
                }
                delay(5) // 模拟加载间隔
            }

            val currentMemory = getMemoryUsage()
            log("  第 ${batch + 1} 批加载完成: 内存=${currentMemory}MB, 本地池=${localPool.size()}")

            // 断言：本地池不超过3个
            assertTrue("本地池大小=${localPool.size()}，不应超过3", localPool.size() <= 3)

            delay(100) // 批次间隔
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        log("  总共加载: 100个视频")
        log("  内存增长: ${memoryIncrease}MB")
        log("  最终本地池大小: ${localPool.size()}")

        assertTrue("本地池最终大小应该=3", localPool.size() == 3)
        assertTrue("内存增长应该 < 50MB，实际: ${memoryIncrease}MB", memoryIncrease < 50)
        log("✅ 测试2通过")
    }

    /**
     * 测试3: 双池并发压力 - 同时操作历史和本地库
     */
    @Test
    fun test3_dualPoolConcurrentStress() = runBlocking {
        log("\n【测试3】双池并发压力测试")
        log("场景: 用户同时滚动历史列表和本地视频库")

        val memoryBefore = getMemoryUsage()
        val startTime = System.currentTimeMillis()

        // 并发操作两个池
        repeat(50) { index ->
            withContext(Dispatchers.Main) {
                // 历史池操作
                val historyUri = Uri.parse("content://history/video_$index")
                historyPool.getOrCreate(historyUri.toString(), historyUri)

                // 本地池操作
                val localUri = Uri.parse("content://local/video_$index")
                localPool.getOrCreate(localUri.toString(), localUri)

                if (index % 10 == 0) {
                    val currentMemory = getMemoryUsage()
                    val totalPlayers = historyPool.size() + localPool.size()
                    log("  第 ${index + 1} 轮: 内存=${currentMemory}MB, 历史池=${historyPool.size()}, 本地池=${localPool.size()}, 总计=${totalPlayers}")

                    // 断言：每个池不超过3个
                    assertTrue("历史池=${historyPool.size()}，不应超过3", historyPool.size() <= 3)
                    assertTrue("本地池=${localPool.size()}，不应超过3", localPool.size() <= 3)
                    // 断言：总计不超过6个
                    assertTrue("总播放器=${totalPlayers}，不应超过6", totalPlayers <= 6)
                }
            }
            delay(10)
        }

        val duration = System.currentTimeMillis() - startTime
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore
        val totalPlayers = historyPool.size() + localPool.size()

        log("  耗时: ${duration}ms")
        log("  内存增长: ${memoryIncrease}MB")
        log("  最终状态: 历史池=${historyPool.size()}, 本地池=${localPool.size()}, 总计=${totalPlayers}")

        assertTrue("总播放器应该=6", totalPlayers == 6)
        assertTrue("内存增长应该 < 100MB，实际: ${memoryIncrease}MB", memoryIncrease < 100)
        log("✅ 测试3通过")
    }

    /**
     * 测试4: 极限场景 - 连续200次操作
     */
    @Test
    fun test4_extremeScenario() = runBlocking {
        log("\n【测试4】极限场景测试")
        log("场景: 连续200次快速操作，模拟极端使用情况")

        val memoryBefore = getMemoryUsage()
        val startTime = System.currentTimeMillis()
        val memorySnapshots = mutableListOf<Int>()

        repeat(200) { index ->
            withContext(Dispatchers.Main) {
                // 交替使用两个池
                if (index % 2 == 0) {
                    val uri = Uri.parse("content://history/video_$index")
                    historyPool.getOrCreate(uri.toString(), uri)
                } else {
                    val uri = Uri.parse("content://local/video_$index")
                    localPool.getOrCreate(uri.toString(), uri)
                }

                // 每20次记录内存快照
                if (index % 20 == 0) {
                    val currentMemory = getMemoryUsage()
                    memorySnapshots.add(currentMemory)
                    val totalPlayers = historyPool.size() + localPool.size()
                    log("  第 ${index + 1} 次: 内存=${currentMemory}MB, 总播放器=${totalPlayers}")
                }
            }
            delay(5)
        }

        val duration = System.currentTimeMillis() - startTime
        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore
        val totalPlayers = historyPool.size() + localPool.size()

        // 分析内存波动
        val maxMemory = memorySnapshots.maxOrNull() ?: 0
        val minMemory = memorySnapshots.minOrNull() ?: 0
        val memoryVariation = maxMemory - minMemory

        log("  耗时: ${duration}ms")
        log("  内存增长: ${memoryIncrease}MB")
        log("  内存波动: ${memoryVariation}MB (最高=${maxMemory}MB, 最低=${minMemory}MB)")
        log("  最终状态: 历史池=${historyPool.size()}, 本地池=${localPool.size()}, 总计=${totalPlayers}")

        assertTrue("总播放器应该 <= 6", totalPlayers <= 6)
        assertTrue("内存增长应该 < 100MB，实际: ${memoryIncrease}MB", memoryIncrease < 100)
        assertTrue("内存波动应该 < 50MB，说明没有泄漏", memoryVariation < 50)
        log("✅ 测试4通过")
    }

    /**
     * 测试5: 删除操作压力 - 模拟频繁删除历史记录
     */
    @Test
    fun test5_frequentDeletion() = runBlocking {
        log("\n【测试5】频繁删除操作测试")
        log("场景: 用户频繁删除历史记录，触发池重置")

        val memoryBefore = getMemoryUsage()

        repeat(20) { round ->
            // 创建3个播放器
            withContext(Dispatchers.Main) {
                repeat(3) { index ->
                    val uri = Uri.parse("content://history/video_${round}_$index")
                    historyPool.getOrCreate(uri.toString(), uri)
                }
            }

            log("  第 ${round + 1} 轮: 创建3个播放器，池大小=${historyPool.size()}")
            assertTrue("创建后池大小应该=3", historyPool.size() == 3)

            delay(50)

            // 模拟删除操作 - 释放所有播放器
            withContext(Dispatchers.Main) {
                historyPool.releaseAll()
            }

            log("  第 ${round + 1} 轮: 删除后池大小=${historyPool.size()}")
            assertTrue("删除后池大小应该=0", historyPool.size() == 0)

            delay(50)
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        log("  总共执行: 20轮创建-删除循环")
        log("  内存增长: ${memoryIncrease}MB")
        log("  最终池大小: ${historyPool.size()}")

        assertTrue("最终池应该为空", historyPool.size() == 0)
        assertTrue("内存增长应该 < 30MB，实际: ${memoryIncrease}MB", memoryIncrease < 30)
        log("✅ 测试5通过")
    }

    /**
     * 测试6: 增量加载边界测试 - 模拟历史列表从10到100的增量加载
     */
    @Test
    fun test6_incrementalLoadingBoundary() = runBlocking {
        log("\n【测试6】增量加载边界测试")
        log("场景: 模拟历史列表从10条增长到100条的过程")

        val memoryBefore = getMemoryUsage()
        var displayCount = 10

        // 模拟9次增量加载（10 -> 20 -> 30 -> ... -> 100）
        repeat(9) { batch ->
            log("  显示数量: $displayCount -> ${displayCount + 10}")

            // 模拟滚动到新加载的10条
            repeat(10) { index ->
                withContext(Dispatchers.Main) {
                    val globalIndex = displayCount + index
                    val uri = Uri.parse("content://history/video_$globalIndex")
                    historyPool.getOrCreate(uri.toString(), uri)
                }
                delay(10)
            }

            displayCount += 10
            val currentMemory = getMemoryUsage()
            log("  当前显示: ${displayCount}条, 内存=${currentMemory}MB, 池大小=${historyPool.size()}")

            assertTrue("池大小=${historyPool.size()}，不应超过3", historyPool.size() <= 3)
        }

        val memoryAfter = getMemoryUsage()
        val memoryIncrease = memoryAfter - memoryBefore

        log("  最终显示: ${displayCount}条历史记录")
        log("  内存增长: ${memoryIncrease}MB")
        log("  最终池大小: ${historyPool.size()}")

        assertTrue("最终池大小应该=3", historyPool.size() == 3)
        assertTrue("内存增长应该 < 50MB，实际: ${memoryIncrease}MB", memoryIncrease < 50)
        log("✅ 测试6通过")
    }

    // ========== 辅助方法 ==========

    private fun getMemoryUsage(): Int {
        val runtime = Runtime.getRuntime()
        return ((runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024).toInt()
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        println(message)
    }
}
