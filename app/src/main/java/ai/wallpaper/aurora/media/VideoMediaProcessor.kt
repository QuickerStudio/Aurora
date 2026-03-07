package ai.wallpaper.aurora.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.util.LinkedHashMap

/**
 * 视频媒体处理器
 *
 * 核心职责：
 * 1. 管理播放器的创建/销毁/复用
 * 2. 使用专用线程处理播放器操作，避免阻塞主线程
 * 3. 使用 Actor 模式处理事件，避免并发问题
 * 4. LRU 策略控制播放器数量上限
 *
 * 设计思路借鉴自 VLC Android：
 * - 专用单线程上下文处理播放器操作
 * - Actor 模式处理播放器事件
 * - 协程生命周期绑定，自动取消
 */
class VideoMediaProcessor(
    private val context: Context,
    private val maxPlayerCount: Int = 3,
    private val logger: Logger = AndroidLogger()
) {
    // 专用单线程上下文，用于播放器操作（借鉴 VLC）
    private val playerContext = newSingleThreadContext("aurora-player")

    // 主协程作用域
    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // LRU 播放器池
    private val playerPool = object : LinkedHashMap<String, ExoPlayer>(maxPlayerCount, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExoPlayer>?): Boolean {
            val shouldRemove = size > maxPlayerCount
            if (shouldRemove && eldest != null) {
                // 在专用线程中异步释放播放器
                processorScope.launch(playerContext) {
                    logger.d(TAG, "LRU evicting player: ${eldest.key}")
                    releasePlayerInternal(eldest.value)
                }
            }
            return shouldRemove
        }
    }

    // Actor 模式处理播放器请求（借鉴 VLC）
    private val playerActor = processorScope.actor<PlayerRequest>(
        context = playerContext,
        capacity = Channel.UNLIMITED
    ) {
        for (request in channel) {
            when (request) {
                is PlayerRequest.GetOrCreate -> {
                    val player = getOrCreatePlayerInternal(request.uriKey, request.videoUri)
                    request.result.complete(player)
                }
                is PlayerRequest.Release -> {
                    releasePlayerByKeyInternal(request.uriKey)
                    request.result.complete(Unit)
                }
                is PlayerRequest.ReleaseAll -> {
                    releaseAllPlayersInternal()
                    request.result.complete(Unit)
                }
            }
        }
    }

    /**
     * 获取或创建播放器（公开 API）
     * 在主线程调用，内部会切换到专用线程
     */
    suspend fun getOrCreatePlayer(uriKey: String, videoUri: Uri): ExoPlayer {
        val deferred = CompletableDeferred<ExoPlayer>()
        playerActor.send(PlayerRequest.GetOrCreate(uriKey, videoUri, deferred))
        return deferred.await()
    }

    /**
     * 释放指定播放器（公开 API）
     */
    suspend fun releasePlayer(uriKey: String) {
        val deferred = CompletableDeferred<Unit>()
        playerActor.send(PlayerRequest.Release(uriKey, deferred))
        deferred.await()
    }

    /**
     * 释放所有播放器（公开 API）
     */
    suspend fun releaseAllPlayers() {
        val deferred = CompletableDeferred<Unit>()
        playerActor.send(PlayerRequest.ReleaseAll(deferred))
        deferred.await()
    }

    /**
     * 裁剪播放器池到指定大小（用于内存压力响应）
     */
    fun trimToSize(targetSize: Int) {
        processorScope.launch(playerContext) {
            trimToSizeInternal(targetSize)
        }
    }

    /**
     * 立即清空所有播放器（用于紧急内存释放）
     */
    fun clearAll() {
        processorScope.launch(playerContext) {
            releaseAllPlayersInternal()
        }
    }

    /**
     * 获取当前播放器数量
     */
    fun getPlayerCount(): Int = playerPool.size

    /**
     * 内部方法：获取或创建播放器
     * 必须在 playerContext 中调用
     */
    private fun getOrCreatePlayerInternal(uriKey: String, videoUri: Uri): ExoPlayer {
        return playerPool.getOrPut(uriKey) {
            logger.d(TAG, "Creating player: $uriKey (pool size: ${playerPool.size})")
            createPlayerInternal(videoUri)
        }
    }

    /**
     * 内部方法：创建播放器
     * 必须在 playerContext 中调用
     */
    private fun createPlayerInternal(videoUri: Uri): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2000,  // minBufferMs
                        5000,  // maxBufferMs
                        1000,  // bufferForPlaybackMs
                        1000   // bufferForPlaybackAfterRebufferMs
                    )
                    .build()
            )
            .build()
            .apply {
                // 只加载前10秒
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(10_000)
                            .build()
                    )
                    .build()

                setMediaItem(clippedMediaItem)
                prepare()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    /**
     * 内部方法：释放指定播放器
     * 必须在 playerContext 中调用
     */
    private fun releasePlayerByKeyInternal(uriKey: String) {
        playerPool.remove(uriKey)?.let { player ->
            logger.d(TAG, "Releasing player: $uriKey")
            releasePlayerInternal(player)
        }
    }

    /**
     * 内部方法：释放播放器实例
     * 必须在 playerContext 中调用
     */
    private fun releasePlayerInternal(player: ExoPlayer) {
        try {
            player.stop()
            player.release()
        } catch (e: Exception) {
            logger.e(TAG, "Error releasing player", e)
        }
    }

    /**
     * 内部方法：释放所有播放器
     * 必须在 playerContext 中调用
     */
    private fun releaseAllPlayersInternal() {
        logger.d(TAG, "Releasing all players (count: ${playerPool.size})")
        playerPool.values.forEach { releasePlayerInternal(it) }
        playerPool.clear()
    }

    /**
     * 内部方法：裁剪播放器池到指定大小
     * 必须在 playerContext 中调用
     */
    private fun trimToSizeInternal(targetSize: Int) {
        if (playerPool.size <= targetSize) {
            logger.d(TAG, "Pool size ${playerPool.size} already <= target $targetSize, no trim needed")
            return
        }

        logger.d(TAG, "Trimming pool from ${playerPool.size} to $targetSize")

        // LinkedHashMap 的迭代器按访问顺序返回，最久未访问的在前面
        val iterator = playerPool.entries.iterator()
        var removed = 0

        while (iterator.hasNext() && playerPool.size > targetSize) {
            val entry = iterator.next()
            logger.d(TAG, "Trimming player: ${entry.key}")
            releasePlayerInternal(entry.value)
            iterator.remove()
            removed++
        }

        logger.d(TAG, "Trimmed $removed players, new size: ${playerPool.size}")
    }

    /**
     * 关闭媒体处理器
     * 释放所有资源
     */
    fun shutdown() {
        processorScope.launch(playerContext) {
            releaseAllPlayersInternal()
            playerContext.close()
            processorScope.cancel()
        }
    }

    companion object {
        private const val TAG = "VideoMediaProcessor"
    }
}

/**
 * 日志接口，用于测试时注入
 */
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Android Log 实现
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
    }
}

/**
 * 播放器请求密封类
 * 用于 Actor 模式的消息传递
 */
private sealed class PlayerRequest {
    data class GetOrCreate(
        val uriKey: String,
        val videoUri: Uri,
        val result: CompletableDeferred<ExoPlayer>
    ) : PlayerRequest()

    data class Release(
        val uriKey: String,
        val result: CompletableDeferred<Unit>
    ) : PlayerRequest()

    data class ReleaseAll(
        val result: CompletableDeferred<Unit>
    ) : PlayerRequest()
}
