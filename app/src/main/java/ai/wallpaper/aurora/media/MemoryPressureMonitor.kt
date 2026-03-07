package ai.wallpaper.aurora.media

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration

/**
 * 内存压力监控器
 *
 * 监听系统内存压力事件，主动释放资源：
 * - TRIM_MEMORY_RUNNING_LOW: 减少播放器数量
 * - TRIM_MEMORY_RUNNING_CRITICAL: 释放所有播放器
 * - onLowMemory: 紧急释放所有资源
 */
class MemoryPressureMonitor(
    private val processor: VideoMediaProcessor,
    private val logger: Logger = AndroidLogger()
) {
    private var isRegistered = false

    private val callbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    logger.d(TAG, "Memory pressure: RUNNING_LOW, trimming player pool")
                    // 内存紧张，减少到 2 个播放器
                    processor.trimToSize(2)
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    logger.d(TAG, "Memory pressure: RUNNING_CRITICAL, clearing all players")
                    // 内存严重不足，释放所有播放器
                    processor.clearAll()
                }
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    logger.d(TAG, "UI hidden, trimming player pool")
                    // UI 不可见，减少到 1 个播放器
                    processor.trimToSize(1)
                }
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            // 配置变化不需要处理
        }

        override fun onLowMemory() {
            logger.d(TAG, "Low memory warning, clearing all players")
            // 系统内存极低，立即释放所有资源
            processor.clearAll()
        }
    }

    /**
     * 注册内存压力监听
     */
    fun register(context: Context) {
        if (!isRegistered) {
            context.registerComponentCallbacks(callbacks)
            isRegistered = true
            logger.d(TAG, "Memory pressure monitor registered")
        }
    }

    /**
     * 取消注册
     */
    fun unregister(context: Context) {
        if (isRegistered) {
            context.unregisterComponentCallbacks(callbacks)
            isRegistered = false
            logger.d(TAG, "Memory pressure monitor unregistered")
        }
    }

    companion object {
        private const val TAG = "MemoryPressureMonitor"
    }
}
