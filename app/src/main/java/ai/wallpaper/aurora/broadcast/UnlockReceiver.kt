package ai.wallpaper.aurora.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.wallpaper.aurora.activity.FullscreenVideoActivity
import java.io.File

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) {
            // 检查是否启用了清空桌面功能
            val enableFile = File(context.filesDir, "clear_desktop_enabled")
            if (enableFile.exists()) {
                // 启动全屏视频 Activity
                val videoIntent = Intent(context, FullscreenVideoActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                }
                context.startActivity(videoIntent)
            }
        }
    }
}
