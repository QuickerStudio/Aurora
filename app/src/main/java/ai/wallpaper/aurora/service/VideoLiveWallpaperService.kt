package ai.wallpaper.aurora.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 视频动态壁纸服务 - 基于母版架构，对齐 Android 16/API 36
 *
 * 设计原则：
 * 1. 文件通信：通过 video_live_wallpaper_file_path 文件读取视频路径
 * 2. 广播切换：监听 VIDEO_PATH_UPDATED 广播，实现动态切换视频
 * 3. 省电优化：同步 prepare() + start()，避免异步回调开销
 * 4. 独立进程：运行在 :wallpaper 进程，与主应用隔离
 * 5. 历史记录：通过广播通知主进程添加历史（跨进程通信）
 */
class VideoLiveWallpaperService : WallpaperService() {

    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var videoFilePath: String? = null
        private var currentHolder: SurfaceHolder? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            // 读取视频路径
            videoFilePath = readVideoFilePath()

            // 注册广播：音量控制 + 视频路径更新
            val intentFilter = IntentFilter().apply {
                addAction(VIDEO_PARAMS_CONTROL_ACTION)
                addAction(VIDEO_PATH_UPDATED_ACTION)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        VIDEO_PARAMS_CONTROL_ACTION -> handleVolumeControl(intent)
                        VIDEO_PATH_UPDATED_ACTION -> {
                            Log.d(TAG, "Received video path update broadcast")
                            reloadVideo()
                        }
                    }
                }
            }.also { broadcastReceiver = it }

            // Android 13+ 使用 RECEIVER_NOT_EXPORTED（安全性）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this@VideoLiveWallpaperService,
                    receiver,
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(receiver, intentFilter)
            }
        }

        private fun readVideoFilePath(): String? {
            return try {
                this@VideoLiveWallpaperService.openFileInput("video_live_wallpaper_file_path")
                    .bufferedReader().use { it.readText().trim() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read video path", e)
                null
            }
        }

        private fun handleVolumeControl(intent: Intent) {
            val shouldMute = intent.getBooleanExtra(KEY_ACTION, false)
            mediaPlayer?.setVolume(
                if (shouldMute) 0f else 1.0f,
                if (shouldMute) 0f else 1.0f
            )
        }

        /**
         * 重新加载视频（收到广播后调用）
         */
        private fun reloadVideo() {
            Log.d(TAG, "Reloading video...")

            // 重新读取视频路径
            videoFilePath = readVideoFilePath()
            val path = videoFilePath

            if (path.isNullOrEmpty()) {
                Log.e(TAG, "Reload failed: video path is null or empty")
                return
            }

            val holder = currentHolder
            if (holder == null) {
                Log.e(TAG, "Reload failed: surface holder is null")
                return
            }

            // 释放旧播放器
            releasePlayer()

            // 创建新播放器并播放
            try {
                startPlayback(holder, path)
                Log.d(TAG, "Video reloaded successfully: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload video", e)
            }
        }

        /**
         * 启动播放
         */
        private fun startPlayback(holder: SurfaceHolder, path: String) {
            mediaPlayer = MediaPlayer().apply {
                setSurface(holder.surface)

                // 支持 content:// URI（Android 10+ 媒体库）
                if (path.startsWith("content://")) {
                    setDataSource(this@VideoLiveWallpaperService, Uri.parse(path))
                } else {
                    setDataSource(path)
                }

                isLooping = true
                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)

                // 同步 prepare（母版设计，省电稳定）
                prepare()
                start()
            }

            // 读取音量状态
            val unmuteFile = File(filesDir, "unmute")
            val volume = if (unmuteFile.exists()) 1.0f else 0f
            mediaPlayer?.setVolume(volume, volume)

            Log.d(TAG, "Playback started: $path")

            // 成功播放后，通过广播通知主进程添加历史记录
            if (path.startsWith("content://")) {
                notifyVideoPlaybackStarted(path)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)

            // 保存 holder 引用，用于重新加载视频
            currentHolder = holder

            val path = videoFilePath
            if (path.isNullOrEmpty()) {
                Log.e(TAG, "Video path is null or empty")
                return
            }

            try {
                startPlayback(holder, path)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start playback", e)
                releasePlayer()
            }
        }

        /**
         * 通知主进程添加历史记录（跨进程通信）
         */
        private fun notifyVideoPlaybackStarted(videoUri: String) {
            try {
                val intent = Intent(ACTION_VIDEO_PLAYBACK_STARTED).apply {
                    putExtra(EXTRA_VIDEO_URI, videoUri)
                    setPackage(this@VideoLiveWallpaperService.packageName)
                }
                this@VideoLiveWallpaperService.sendBroadcast(intent)
                Log.d(TAG, "Sent playback started broadcast: $videoUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send playback broadcast", e)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            mediaPlayer?.let { player ->
                try {
                    if (visible) {
                        if (!player.isPlaying) player.start()
                    } else {
                        if (player.isPlaying) player.pause()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in visibility change", e)
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            currentHolder = null
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
            broadcastReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver", e)
                }
            }
        }

        private fun releasePlayer() {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping player", e)
                }
                try {
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing player", e)
                }
            }
            mediaPlayer = null
        }
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    companion object {
        private const val TAG = "VideoWallpaper"
        const val VIDEO_PARAMS_CONTROL_ACTION = "ai.wallpaper.aurora.VOLUME_CONTROL"
        const val VIDEO_PATH_UPDATED_ACTION = "ai.wallpaper.aurora.VIDEO_PATH_UPDATED"
        const val ACTION_VIDEO_PLAYBACK_STARTED = "ai.wallpaper.aurora.VIDEO_PLAYBACK_STARTED"
        const val EXTRA_VIDEO_URI = "video_uri"
        private const val KEY_ACTION = "music"
        private const val ACTION_MUSIC_UNMUTE = false
        private const val ACTION_MUSIC_MUTE = true

        fun muteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                putExtra(KEY_ACTION, ACTION_MUSIC_MUTE)
            }.also { context.sendBroadcast(it) }
        }

        fun unmuteMusic(context: Context) {
            Intent(VIDEO_PARAMS_CONTROL_ACTION).apply {
                putExtra(KEY_ACTION, ACTION_MUSIC_UNMUTE)
            }.also { context.sendBroadcast(it) }
        }

        /**
         * 通知壁纸服务视频路径已更新（遥控器发送信号）
         */
        fun notifyVideoPathChanged(context: Context) {
            val intent = Intent(VIDEO_PATH_UPDATED_ACTION).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Sent video path update broadcast")
        }

        fun setToWallPaper(context: Context) {
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, VideoLiveWallpaperService::class.java)
                )
            }.also {
                context.startActivity(it)
            }
        }
    }
}
