package ai.wallpaper.aurora.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import java.util.LinkedHashMap

/**
 * LRU 播放器池管理器
 * 严格限制播放器数量，使用 LRU 策略淘汰最久未使用的播放器
 */
class LRUPlayerPool(
    private val context: Context,
    private val maxSize: Int = 3
) {
    // LinkedHashMap with accessOrder=true 实现 LRU
    private val pool = object : LinkedHashMap<String, ExoPlayer>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExoPlayer>?): Boolean {
            val shouldRemove = size > maxSize
            if (shouldRemove && eldest != null) {
                // 释放最久未使用的播放器
                android.util.Log.d("LRUPlayerPool", "Evicting player for URI: ${eldest.key}")
                eldest.value.release()
            }
            return shouldRemove
        }
    }

    /**
     * 获取或创建播放器
     * 如果池中已存在，直接返回并更新访问时间
     * 如果不存在，创建新播放器（可能触发 LRU 淘汰）
     */
    fun getOrCreate(uriKey: String, videoUri: android.net.Uri): ExoPlayer {
        return pool.getOrPut(uriKey) {
            logMemoryUsage("Before creating player")
            android.util.Log.d("LRUPlayerPool", "Creating new player for URI: $uriKey (pool size: ${pool.size})")
            val player = createPlayer(videoUri)
            logMemoryUsage("After creating player")
            player
        }
    }

    /**
     * 创建播放器实例
     * 关键优化：只加载前10秒，限制缓冲大小
     */
    private fun createPlayer(videoUri: android.net.Uri): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2000,  // minBufferMs: 最少缓冲2秒
                        5000,  // maxBufferMs: 最多缓冲5秒
                        1000,  // bufferForPlaybackMs
                        1000   // bufferForPlaybackAfterRebufferMs
                    )
                    .build()
            )
            .build()
            .apply {
                // 使用 ClippingConfiguration 只加载前10秒
                val clippedMediaItem = MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(10_000) // 只加载前10秒
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
     * 释放指定播放器
     */
    fun release(uriKey: String) {
        pool.remove(uriKey)?.let { player ->
            android.util.Log.d("LRUPlayerPool", "Releasing player for URI: $uriKey")
            player.release()
        }
    }

    /**
     * 释放所有播放器
     */
    fun releaseAll() {
        android.util.Log.d("LRUPlayerPool", "Releasing all players (count: ${pool.size})")
        pool.values.forEach { it.release() }
        pool.clear()
    }

    /**
     * 获取当前池大小
     */
    fun size(): Int = pool.size

    /**
     * 检查是否包含指定 URI 的播放器
     */
    fun contains(uriKey: String): Boolean = pool.containsKey(uriKey)

    /**
     * 记录内存使用情况
     */
    private fun logMemoryUsage(tag: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val availableMemory = maxMemory - usedMemory

        android.util.Log.d(
            "LRUPlayerPool",
            "[$tag] Memory: ${usedMemory}MB / ${maxMemory}MB (Available: ${availableMemory}MB) | Players: ${pool.size}"
        )
    }
}
