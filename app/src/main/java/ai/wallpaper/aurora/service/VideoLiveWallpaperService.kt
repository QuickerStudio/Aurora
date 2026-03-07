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

class VideoLiveWallpaperService : WallpaperService() {

    internal inner class VideoEngine : Engine() {
        private var mediaPlayer: MediaPlayer? = null
        private var broadcastReceiver: BroadcastReceiver? = null
        private var videoFilePath: String? = null
        private var currentHolder: SurfaceHolder? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            videoFilePath = readVideoFilePath()

            val intentFilter = IntentFilter().apply {
                addAction(VIDEO_PARAMS_CONTROL_ACTION)
                addAction(VIDEO_PATH_UPDATED_ACTION)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        VIDEO_PARAMS_CONTROL_ACTION -> handleVolumeControl(intent)
                        VIDEO_PATH_UPDATED_ACTION -> {
                            Log.d("VideoWallpaper", "Received path update broadcast")
                            reloadVideo()
                        }
                    }
                }
            }.also { broadcastReceiver = it }

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
                Log.e("VideoWallpaper", "Failed to read video path", e)
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

        private fun releasePlayer() {
            mediaPlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
            mediaPlayer = null
        }

        private fun startPlayback(holder: SurfaceHolder) {
            currentHolder = holder
            val path = videoFilePath ?: return

            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(holder.surface)

                    if (path.startsWith("content://")) {
                        setDataSource(this@VideoLiveWallpaperService, Uri.parse(path))
                    } else {
                        setDataSource(path)
                    }

                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }

                val unmuteFile = File(filesDir, "unmute")
                val volume = if (unmuteFile.exists()) 1.0f else 0f
                mediaPlayer?.setVolume(volume, volume)
                Log.d("VideoWallpaper", "Started playback for: $path")
            } catch (e: Exception) {
                Log.e("VideoWallpaper", "Failed to start playback for: $path", e)
                releasePlayer()
            }
        }

        private fun reloadVideo() {
            videoFilePath = readVideoFilePath()
            val holder = currentHolder ?: return
            releasePlayer()
            startPlayback(holder)
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startPlayback(holder)
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
            currentHolder = null
            releasePlayer()
        }

        override fun onDestroy() {
            super.onDestroy()
            releasePlayer()
            broadcastReceiver?.let { unregisterReceiver(it) }
        }
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    companion object {
        const val VIDEO_PARAMS_CONTROL_ACTION = "com.wallpaper.livewallpaper"
        const val VIDEO_PATH_UPDATED_ACTION = "ai.wallpaper.aurora.VIDEO_PATH_UPDATED"
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

        fun notifyVideoPathChanged(context: Context) {
            context.sendBroadcast(Intent(VIDEO_PATH_UPDATED_ACTION))
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
