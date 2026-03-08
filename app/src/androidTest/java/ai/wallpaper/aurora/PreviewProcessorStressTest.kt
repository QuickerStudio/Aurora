package ai.wallpaper.aurora

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.wallpaper.aurora.utils.MediaType
import ai.wallpaper.aurora.utils.PreviewProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PreviewProcessorStressTest {

    private lateinit var context: Context
    private lateinit var processor: PreviewProcessor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        processor = PreviewProcessor(context)
    }

    @After
    fun teardown() {
        processor.cancel()
    }

    @Test
    fun testRapidSubmit() = runBlocking {
        // 测试快速连续提交请求
        val completedCount = AtomicInteger(0)
        val totalRequests = 100

        repeat(10) { batch ->
            val requests = (0 until 10).map { index ->
                PreviewProcessor.PreviewRequest(
                    id = batch * 10 + index,
                    uri = Uri.parse("content://test/$batch/$index"),
                    hasPreview = false,
                    mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
                )
            }

            processor.submit(requests, "fit") { _, _, _ ->
                completedCount.incrementAndGet()
            }

            delay(50) // 模拟快速滚动
        }

        // 等待处理完成
        delay(5000)
        println("Rapid submit test: completed ${completedCount.get()}/$totalRequests requests")
    }

    @Test
    fun testConcurrentSubmit() = runBlocking {
        // 测试并发提交
        val latch = CountDownLatch(5)
        val completedCount = AtomicInteger(0)

        repeat(5) { threadId ->
            Thread {
                val requests = (0 until 20).map { index ->
                    PreviewProcessor.PreviewRequest(
                        id = threadId * 20 + index,
                        uri = Uri.parse("content://test/$threadId/$index"),
                        hasPreview = false,
                        mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
                    )
                }

                processor.submit(requests, "fit") { _, _, _ ->
                    completedCount.incrementAndGet()
                }

                latch.countDown()
            }.start()
        }

        latch.await(10, TimeUnit.SECONDS)
        delay(5000)
        println("Concurrent submit test: completed ${completedCount.get()}/100 requests")
    }

    @Test
    fun testRepeatedClearAndSubmit() = runBlocking {
        // 测试反复清理和提交
        val completedCount = AtomicInteger(0)

        repeat(20) { iteration ->
            val requests = (0 until 10).map { index ->
                PreviewProcessor.PreviewRequest(
                    id = index,
                    uri = Uri.parse("content://test/$iteration/$index"),
                    hasPreview = false,
                    mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
                )
            }

            processor.submit(requests, "fit") { _, _, _ ->
                completedCount.incrementAndGet()
            }

            delay(100)
            processor.clear()
        }

        delay(2000)
        println("Repeated clear test: completed ${completedCount.get()} requests")
    }

    @Test
    fun testAlternatingDisplayMode() = runBlocking {
        // 测试交替切换显示模式
        val completedCount = AtomicInteger(0)

        repeat(10) { iteration ->
            val requests = (0 until 10).map { index ->
                PreviewProcessor.PreviewRequest(
                    id = index,
                    uri = Uri.parse("content://test/$iteration/$index"),
                    hasPreview = false,
                    mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
                )
            }

            val mode = if (iteration % 2 == 0) "fit" else "fill"
            processor.submit(requests, mode) { _, _, _ ->
                completedCount.incrementAndGet()
            }

            delay(200)
        }

        delay(5000)
        println("Alternating mode test: completed ${completedCount.get()} requests")
    }

    @Test
    fun testLargeDataset() = runBlocking {
        // 测试大数据集（减少到200项，避免超时）
        val completedCount = AtomicInteger(0)
        val totalItems = 200

        val requests = (0 until totalItems).map { index ->
            PreviewProcessor.PreviewRequest(
                id = index,
                uri = Uri.parse("content://test/large/$index"),
                hasPreview = false,
                mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
            )
        }

        processor.submit(requests, "fit") { _, _, _ ->
            completedCount.incrementAndGet()
        }

        delay(15000) // 增加等待时间
        println("Large dataset test: completed ${completedCount.get()}/$totalItems requests")
    }

    @Test
    fun testMemoryPressure() = runBlocking {
        // 测试内存压力
        val completedCount = AtomicInteger(0)

        repeat(50) { batch ->
            val requests = (0 until 20).map { index ->
                PreviewProcessor.PreviewRequest(
                    id = batch * 20 + index,
                    uri = Uri.parse("content://test/memory/$batch/$index"),
                    hasPreview = false,
                    mediaType = if (index % 2 == 0) MediaType.VIDEO else MediaType.IMAGE
                )
            }

            processor.submit(requests, "fit") { _, _, _ ->
                completedCount.incrementAndGet()
            }

            if (batch % 5 == 0) {
                // 定期清理
                processor.clear()
                System.gc()
            }

            delay(100)
        }

        delay(5000)
        println("Memory pressure test: completed ${completedCount.get()} requests")
    }

    @Test
    fun testOddEvenDistribution() = runBlocking {
        // 测试奇偶分离是否均衡
        val oddCount = AtomicInteger(0)
        val evenCount = AtomicInteger(0)

        val requests = (0 until 100).map { index ->
            PreviewProcessor.PreviewRequest(
                id = index,
                uri = Uri.parse("content://test/distribution/$index"),
                hasPreview = false,
                mediaType = MediaType.VIDEO
            )
        }

        processor.submit(requests, "fit") { id, _, _ ->
            if (id % 2 == 1) {
                oddCount.incrementAndGet()
            } else {
                evenCount.incrementAndGet()
            }
        }

        delay(10000)
        println("Distribution test: odd=${oddCount.get()}, even=${evenCount.get()}")
    }
}
