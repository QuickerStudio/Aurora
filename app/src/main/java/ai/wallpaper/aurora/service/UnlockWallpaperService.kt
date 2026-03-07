package ai.wallpaper.aurora.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.wallpaper.aurora.R
import ai.wallpaper.aurora.activity.FullscreenVideoActivity
import java.io.File

/**
 * 解锁壁纸服务 - 监听解锁事件并显示全屏视频
 *
 * 设计原则：
 * 1. 文件通信：通过 video_live_wallpaper_file_path 读取视频路径
 * 2. 与 VideoLiveWallpaperService 共享同一个路径文件
 * 3. 解锁时自动播放当前壁纸视频
 */
class UnlockWallpaperService : Service() {

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                // 读取当前壁纸视频路径
                val videoPath = readVideoFilePath()
                if (videoPath.isNullOrEmpty()) {
                    Log.w(TAG, "No video path found, skipping unlock wallpaper")
                    return
                }

                Log.d(TAG, "Unlock detected, launching video: $videoPath")

                // 启动全屏视频 Activity
                val videoIntent = Intent(context, FullscreenVideoActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                }
                context.startActivity(videoIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 创建通知渠道
        createNotificationChannel()

        // 启动前台服务
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 注册解锁广播接收器
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(unlockReceiver, filter, RECEIVER_EXPORTED)

        Log.d(TAG, "UnlockWallpaperService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        Log.d(TAG, "UnlockWallpaperService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 读取视频路径（与 VideoLiveWallpaperService 共享同一文件）
     */
    private fun readVideoFilePath(): String? {
        return try {
            val file = File(filesDir, "video_live_wallpaper_file_path")
            if (file.exists()) {
                val path = file.readText().trim()
                Log.d(TAG, "Read video path: $path")
                path
            } else {
                Log.w(TAG, "Video path file does not exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read video path", e)
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "清空桌面服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持清空桌面功能运行"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("清空桌面已启用")
            .setContentText("解锁后自动显示视频壁纸")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "UnlockWallpaper"
        private const val CHANNEL_ID = "clear_desktop_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, UnlockWallpaperService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UnlockWallpaperService::class.java)
            context.stopService(intent)
        }
    }
}
