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
import androidx.core.app.NotificationCompat
import ai.wallpaper.aurora.R
import ai.wallpaper.aurora.activity.FullscreenVideoActivity
import java.io.File

class ClearDesktopService : Service() {

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        private const val CHANNEL_ID = "clear_desktop_channel"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, ClearDesktopService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClearDesktopService::class.java)
            context.stopService(intent)
        }
    }
}
