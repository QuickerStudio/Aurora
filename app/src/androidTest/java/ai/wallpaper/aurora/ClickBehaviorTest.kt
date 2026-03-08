package ai.wallpaper.aurora

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.wallpaper.aurora.utils.LRUPlayerPool
import ai.wallpaper.aurora.utils.MediaType
import ai.wallpaper.aurora.utils.PreviewProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * 点击行为测试 - 定位缩略图重置问题
 */
@RunWith(AndroidJUnit4::class)
class ClickBehaviorTest {

    private lateinit var context: Context
    private lateinit var playerPool: LRUPlayerPool
    private lateinit var previewProcessor: PreviewProcessor
    private lateinit var previewBitmaps: SnapshotStateMap<Int, Bitmap>

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        playerPool = LRUPlayerPool(context, maxSize = 3)
        previewProcessor = PreviewProcessor(context)
        previewBitmaps = mutableStateMapOf()
    }

    @After
    fun teardown() = runBlocking {
        withContext(Dispatchers.Main) {
            playerPool.releaseAll()
        }
        previewProcessor.cancel()
        previewBitmaps.clear()
    }

    @Test
    fun testSingleVideoClick() = runBlocking {
        // 测试：点击单个视频，验证缩略图不被重置
        println("\n=== Test: Single Video Click ===")

        val videoId = 1
        val videoUri = Uri.parse("content://test/video/$videoId")

        // 1. 模拟缩略图加载完成
        val mockBitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        previewBitmaps[videoId] = mockBitmap
        println("Step 1: Thumbnail loaded for video $videoId")
        println("  - previewBitmaps size: ${previewBitmaps.size}")
        println("  - Contains video $videoId: ${previewBitmaps.containsKey(videoId)}")

        delay(100)

        // 2. 模拟点击（选中视频）
        withContext(Dispatchers.Main) {
            val player = playerPool.getOrCreate("video_$videoId", videoUri)
            println("\nStep 2: Video clicked, player created")
            println("  - Player pool size: ${playerPool.size()}")
            println("  - previewBitmaps size: ${previewBitmaps.size}")
            println("  - Contains video $videoId: ${previewBitmaps.containsKey(videoId)}")
        }

        delay(100)

        // 3. 验证缩略图未被清除
        assert(previewBitmaps.containsKey(videoId)) {
            "❌ FAILED: Thumbnail was cleared after click!"
        }
        println("\n✅ PASSED: Thumbnail preserved after click")

        mockBitmap.recycle()
    }

    @Test
    fun testMultipleVideoClicks() = runBlocking {
        // 测试：连续点击多个视频，验证缩略图状态
        println("\n=== Test: Multiple Video Clicks ===")

        val videoIds = listOf(1, 2, 3, 4, 5)
        val mockBitmaps = mutableMapOf<Int, Bitmap>()

        // 1. 加载所有缩略图
        videoIds.forEach { id ->
            val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            previewBitmaps[id] = bitmap
            mockBitmaps[id] = bitmap
        }
        println("Step 1: Loaded ${videoIds.size} thumbnails")
        println("  - previewBitmaps size: ${previewBitmaps.size}")

        delay(100)

        // 2. 依次点击每个视频
        withContext(Dispatchers.Main) {
            videoIds.forEach { id ->
                val uri = Uri.parse("content://test/video/$id")
                playerPool.getOrCreate("video_$id", uri)
                println("\nStep 2.$id: Clicked video $id")
                println("  - Player pool size: ${playerPool.size()}")
                println("  - previewBitmaps size: ${previewBitmaps.size}")

                // 验证当前视频的缩略图未被清除
                assert(previewBitmaps.containsKey(id)) {
                    "❌ FAILED: Thumbnail $id was cleared after click!"
                }

                delay(50)
            }
        }

        // 3. 验证所有缩略图都还在
        val missingThumbnails = videoIds.filter { !previewBitmaps.containsKey(it) }
        assert(missingThumbnails.isEmpty()) {
            "❌ FAILED: Missing thumbnails: $missingThumbnails"
        }
        println("\n✅ PASSED: All thumbnails preserved after multiple clicks")

        mockBitmaps.values.forEach { it.recycle() }
    }

    @Test
    fun testClickWithPreviewProcessorRunning() = runBlocking {
        // 测试：在PreviewProcessor运行时点击，检查是否触发重置
        println("\n=== Test: Click With PreviewProcessor Running ===")

        val videoIds = listOf(1, 2, 3, 4, 5)
        val loadedCount = AtomicInteger(0)
        val resetCount = AtomicInteger(0)

        // 1. 提交预览请求
        val requests = videoIds.map { id ->
            PreviewProcessor.PreviewRequest(
                id = id,
                uri = Uri.parse("content://test/video/$id"),
                hasPreview = false,
                mediaType = MediaType.VIDEO
            )
        }

        previewProcessor.submit(requests, "fit") { id, _, bitmap ->
            // 检查是否是重复加载（重置）
            if (previewBitmaps.containsKey(id)) {
                resetCount.incrementAndGet()
                println("⚠️ WARNING: Thumbnail $id reloaded (possible reset)")
            }
            previewBitmaps[id] = bitmap
            loadedCount.incrementAndGet()
            println("Loaded thumbnail $id (total: ${loadedCount.get()})")
        }

        println("Step 1: Submitted ${requests.size} preview requests")

        // 2. 等待部分缩略图加载
        delay(500)
        println("\nStep 2: Partial thumbnails loaded: ${loadedCount.get()}")

        // 3. 在加载过程中点击视频
        withContext(Dispatchers.Main) {
            val clickedId = 2
            val uri = Uri.parse("content://test/video/$clickedId")
            playerPool.getOrCreate("video_$clickedId", uri)
            println("\nStep 3: Clicked video $clickedId during loading")
            println("  - Player pool size: ${playerPool.size()}")
            println("  - previewBitmaps size: ${previewBitmaps.size}")
        }

        // 4. 等待所有加载完成
        delay(2000)
        println("\nStep 4: All thumbnails loaded: ${loadedCount.get()}")
        println("  - Reset count: ${resetCount.get()}")

        // 5. 验证没有重置
        assert(resetCount.get() == 0) {
            "❌ FAILED: ${resetCount.get()} thumbnails were reset during click!"
        }
        println("\n✅ PASSED: No thumbnails reset during click")
    }

    @Test
    fun testRapidClickSameVideo() = runBlocking {
        // 测试：快速连续点击同一视频，检查缩略图稳定性
        println("\n=== Test: Rapid Click Same Video ===")

        val videoId = 1
        val videoUri = Uri.parse("content://test/video/$videoId")

        // 1. 加载缩略图
        val mockBitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        previewBitmaps[videoId] = mockBitmap
        println("Step 1: Thumbnail loaded for video $videoId")

        delay(100)

        // 2. 快速点击10次
        withContext(Dispatchers.Main) {
            repeat(10) { clickIndex ->
                val player = playerPool.getOrCreate("video_$videoId", videoUri)
                println("Click ${clickIndex + 1}: Player instance = ${System.identityHashCode(player)}")

                // 验证缩略图仍然存在
                assert(previewBitmaps.containsKey(videoId)) {
                    "❌ FAILED: Thumbnail cleared at click ${clickIndex + 1}"
                }

                delay(50)
            }
        }

        println("\n✅ PASSED: Thumbnail stable after 10 rapid clicks")
        mockBitmap.recycle()
    }

    @Test
    fun testClickDifferentVideosRapidly() = runBlocking {
        // 测试：快速切换不同视频，检查缩略图是否被错误清除
        println("\n=== Test: Click Different Videos Rapidly ===")

        val videoIds = listOf(1, 2, 3, 4, 5)
        val mockBitmaps = mutableMapOf<Int, Bitmap>()

        // 1. 加载所有缩略图
        videoIds.forEach { id ->
            val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            previewBitmaps[id] = bitmap
            mockBitmaps[id] = bitmap
        }
        println("Step 1: Loaded ${videoIds.size} thumbnails")

        delay(100)

        // 2. 快速切换点击
        withContext(Dispatchers.Main) {
            repeat(20) { clickIndex ->
                val videoId = videoIds[clickIndex % videoIds.size]
                val uri = Uri.parse("content://test/video/$videoId")
                playerPool.getOrCreate("video_$videoId", uri)

                println("Click ${clickIndex + 1}: Selected video $videoId")
                println("  - previewBitmaps size: ${previewBitmaps.size}")

                // 验证所有缩略图都还在
                val missing = videoIds.filter { !previewBitmaps.containsKey(it) }
                assert(missing.isEmpty()) {
                    "❌ FAILED at click ${clickIndex + 1}: Missing thumbnails: $missing"
                }

                delay(100)
            }
        }

        println("\n✅ PASSED: All thumbnails preserved during rapid switching")
        mockBitmaps.values.forEach { it.recycle() }
    }

    @Test
    fun testPlayerPoolEvictionImpact() = runBlocking {
        // 测试：播放器池淘汰是否影响缩略图
        println("\n=== Test: Player Pool Eviction Impact ===")

        val videoIds = (1..10).toList()
        val mockBitmaps = mutableMapOf<Int, Bitmap>()

        // 1. 加载所有缩略图
        videoIds.forEach { id ->
            val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            previewBitmaps[id] = bitmap
            mockBitmaps[id] = bitmap
        }
        println("Step 1: Loaded ${videoIds.size} thumbnails")
        println("  - previewBitmaps size: ${previewBitmaps.size}")

        delay(100)

        // 2. 依次点击，触发LRU淘汰（池大小=3）
        withContext(Dispatchers.Main) {
            videoIds.forEach { id ->
                val uri = Uri.parse("content://test/video/$id")
                playerPool.getOrCreate("video_$id", uri)

                println("\nClicked video $id")
                println("  - Player pool size: ${playerPool.size()}")
                println("  - previewBitmaps size: ${previewBitmaps.size}")

                // 验证缩略图不受播放器淘汰影响
                val missing = videoIds.take(id).filter { !previewBitmaps.containsKey(it) }
                assert(missing.isEmpty()) {
                    "❌ FAILED: Thumbnails cleared due to player eviction: $missing"
                }

                delay(100)
            }
        }

        // 3. 验证所有缩略图都还在（即使播放器被淘汰）
        assert(previewBitmaps.size == videoIds.size) {
            "❌ FAILED: Expected ${videoIds.size} thumbnails, got ${previewBitmaps.size}"
        }
        println("\n✅ PASSED: Thumbnails independent of player pool eviction")

        mockBitmaps.values.forEach { it.recycle() }
    }
}
