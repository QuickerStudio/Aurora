package ai.wallpaper.aurora.service

import android.app.WallpaperManager
import android.content.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.io.File

class VideoLiveWallpaperService : WallpaperService() {

    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var videoFilePath: String? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            videoFilePath = try {
                this@VideoLiveWallpaperService.openFileInput("video_live_wallpaper_file_path")
                    .bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                null
            }

            val intentFilter = IntentFilter(VIDEO_PARAMS_CONTROL_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    this@VideoLiveWallpaperService,
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            handleVolumeControl(intent)
                        }
                    }.also { broadcastReceiver = it },
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        handleVolumeControl(intent)
                    }
                }.also { broadcastReceiver = it }, intentFilter)
            }
        }

        private fun handleVolumeControl(intent: Intent) {
            val shouldMute = intent.getBooleanExtra(KEY_ACTION, false)
            mediaPlayer?.setVolume(
                if (shouldMute) 0f else 1.0f,
                if (shouldMute) 0f else 1.0f
            )
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)

            videoFilePath?.let { path ->
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setSurface(holder.surface)

                        // 支持 Uri 和文件路径
                        if (path.startsWith("content://")) {
                            setDataSource(this@VideoLiveWallpaperService, Uri.parse(path))
                        } else {
                            setDataSource(path)
                        }

                        isLooping = true
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)

                        // 异步准备
                        setOnPreparedListener {
                            it.start()
                        }
                        prepareAsync()
                    }

                    // 检查静音设置
                    val unmuteFile = File(filesDir, "unmute")
                    val volume = if (unmuteFile.exists()) 1.0f else 0f
                    mediaPlayer?.setVolume(volume, volume)

                } catch (e: Exception) {
                    e.printStackTrace()
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            mediaPlayer?.let {
                if (visible && !it.isPlaying) {
                    it.start()
                } else if (!visible && it.isPlaying) {
                    it.pause()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        }

        override fun onDestroy() {
            super.onDestroy()
            mediaPlayer?.release()
            mediaPlayer = null
            broadcastReceiver?.let { unregisterReceiver(it) }
        }
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    companion object {
        const val VIDEO_PARAMS_CONTROL_ACTION = "com.wallpaper.livewallpaper"
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
