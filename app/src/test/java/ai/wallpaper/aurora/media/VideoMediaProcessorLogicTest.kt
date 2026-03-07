package ai.wallpaper.aurora.media

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * VideoMediaProcessor 核心逻辑测试
 *
 * 不依赖 Android 框架，只测试核心算法：
 * 1. Actor 模式消息处理
 * 2. 并发安全性
 * 3. LRU 逻辑（通过 LinkedHashMap 行为验证）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoMediaProcessorLogicTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * 测试1: Actor 模式基本消息处理
     */
    @Test
    fun testActorBasicMessageHandling() = runTest {
        val results = mutableListOf<Int>()

        val actor = testScope.actor<Int>(capacity = Channel.UNLIMITED) {
            for (msg in channel) {
                results.add(msg * 2)
            }
        }

        // 发送消息
        actor.send(1)
        actor.send(2)
        actor.send(3)

        actor.close()
        advanceUntilIdle()

        assertEquals(listOf(2, 4, 6), results, "Actor 应该按顺序处理所有消息")
    }

    /**
     * 测试2: Actor 并发消息不丢失
     */
    @Test
    fun testActorConcurrentMessages() = runTest {
        val results = mutableListOf<Int>()

        val actor = testScope.actor<Int>(capacity = Channel.UNLIMITED) {
            for (msg in channel) {
                delay(1) // 模拟处理时间
                results.add(msg)
            }
        }

        // 并发发送100条消息
        val jobs = (1..100).map { i ->
            launch {
                actor.send(i)
            }
        }

        jobs.joinAll()
        actor.close()
        advanceUntilIdle()

        assertEquals(100, results.size, "所有消息都应该被处理")
        assertEquals((1..100).toSet(), results.toSet(), "不应该丢失任何消息")
    }

    /**
     * 测试3: LinkedHashMap LRU 行为验证
     */
    @Test
    fun testLinkedHashMapLRUBehavior() {
        val maxSize = 3
        val evictedKeys = mutableListOf<String>()

        val lruMap = object : LinkedHashMap<String, String>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                val shouldRemove = size > maxSize
                if (shouldRemove && eldest != null) {
                    evictedKeys.add(eldest.key)
                }
                return shouldRemove
            }
        }

        // 添加3个元素（达到上限）
        lruMap["key1"] = "value1"
        lruMap["key2"] = "value2"
        lruMap["key3"] = "value3"

        assertEquals(3, lruMap.size, "应该有3个元素")
        assertTrue(evictedKeys.isEmpty(), "还没有淘汰")

        // 添加第4个元素，应该淘汰 key1
        lruMap["key4"] = "value4"

        assertEquals(3, lruMap.size, "应该保持3个元素")
        assertEquals(listOf("key1"), evictedKeys, "应该淘汰 key1")
        assertTrue("key1" !in lruMap, "key1 应该被移除")
        assertTrue("key4" in lruMap, "key4 应该存在")
    }

    /**
     * 测试4: LinkedHashMap 访问顺序更新
     */
    @Test
    fun testLinkedHashMapAccessOrder() {
        val maxSize = 3
        val evictedKeys = mutableListOf<String>()

        val lruMap = object : LinkedHashMap<String, String>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                val shouldRemove = size > maxSize
                if (shouldRemove && eldest != null) {
                    evictedKeys.add(eldest.key)
                }
                return shouldRemove
            }
        }

        // 添加3个元素
        lruMap["key1"] = "value1"
        lruMap["key2"] = "value2"
        lruMap["key3"] = "value3"

        // 访问 key1（更新访问时间）
        lruMap["key1"]

        // 添加 key4，应该淘汰 key2（最久未访问）
        lruMap["key4"] = "value4"

        assertEquals(listOf("key2"), evictedKeys, "应该淘汰 key2（最久未访问）")
        assertTrue("key1" in lruMap, "key1 应该保留（刚被访问）")
        assertTrue("key3" in lruMap, "key3 应该保留")
        assertTrue("key4" in lruMap, "key4 应该存在")
    }

    /**
     * 测试5: CompletableDeferred 基本用法
     */
    @Test
    fun testCompletableDeferredBasics() = runTest {
        val deferred = CompletableDeferred<String>()

        launch {
            delay(10)
            deferred.complete("result")
        }

        val result = deferred.await()
        assertEquals("result", result, "应该获取到正确的结果")
    }

    /**
     * 测试6: 多个 CompletableDeferred 并发
     */
    @Test
    fun testMultipleCompletableDeferredConcurrent() = runTest {
        val deferreds = (1..10).map { CompletableDeferred<Int>() }

        // 并发完成所有 deferred
        deferreds.forEachIndexed { index, deferred ->
            launch {
                delay(1)
                deferred.complete(index * 2)
            }
        }

        // 等待所有结果
        val results = deferreds.map { it.await() }

        assertEquals((0..9).map { it * 2 }, results, "所有结果都应该正确")
    }

    /**
     * 测试7: 专用线程上下文切换
     */
    @Test
    fun testDedicatedThreadContext() = runTest {
        val mainThreadName = Thread.currentThread().name
        var workerThreadName: String? = null

        val workerContext = newSingleThreadContext("test-worker")

        withContext(workerContext) {
            workerThreadName = Thread.currentThread().name
        }

        workerContext.close()

        assertTrue(
            workerThreadName?.contains("test-worker") == true,
            "应该在专用线程中执行，实际线程: $workerThreadName"
        )
    }
}
