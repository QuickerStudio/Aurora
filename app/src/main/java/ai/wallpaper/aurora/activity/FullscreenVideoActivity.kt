package ai.wallpaper.aurora.activity

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

class FullscreenVideoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        setContent {
            MaterialTheme {
                FullscreenVideoScreen(
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun FullscreenVideoScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onDismiss()
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    // 确保 SurfaceView 可见
                    setZOrderOnTop(false)

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            try {
                                val videoPath = getVideoPath(ctx)
                                android.util.Log.d("FullscreenVideo", "Video path: $videoPath")

                                if (videoPath.isNullOrEmpty()) {
                                    android.util.Log.e("FullscreenVideo", "Video path is null or empty")
                                    return
                                }

                                mediaPlayer = MediaPlayer().apply {
                                    setOnErrorListener { mp, what, extra ->
                                        android.util.Log.e("FullscreenVideo", "MediaPlayer error: what=$what, extra=$extra")
                                        false
                                    }

                                    setOnInfoListener { mp, what, extra ->
                                        android.util.Log.d("FullscreenVideo", "MediaPlayer info: what=$what, extra=$extra")
                                        false
                                    }

                                    setSurface(holder.surface)

                                    // 支持 Uri 和文件路径
                                    if (videoPath.startsWith("content://")) {
                                        setDataSource(ctx, Uri.parse(videoPath))
                                    } else {
                                        setDataSource(videoPath)
                                    }

                                    isLooping = true
                                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                    setVolume(0f, 0f) // 静音

                                    // 异步准备
                                    setOnPreparedListener { mp ->
                                        android.util.Log.d("FullscreenVideo", "MediaPlayer prepared, starting playback")
                                        mp.start()
                                    }

                                    android.util.Log.d("FullscreenVideo", "Starting async prepare")
                                    prepareAsync()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FullscreenVideo", "Error initializing MediaPlayer", e)
                                e.printStackTrace()
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            android.util.Log.d("FullscreenVideo", "Surface changed: ${width}x${height}")
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            android.util.Log.d("FullscreenVideo", "Surface destroyed")
                            mediaPlayer?.release()
                            mediaPlayer = null
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
}

private fun getVideoPath(context: Context): String? {
    return try {
        val file = File(context.filesDir, "video_live_wallpaper_file_path")
        Log.d("FullscreenVideo", "Checking video path file: ${file.absolutePath}")
        Log.d("FullscreenVideo", "File exists: ${file.exists()}")

        if (file.exists()) {
            val path = file.readText().trim()
            Log.d("FullscreenVideo", "Read video path: $path")

            // 验证路径
            if (path.startsWith("content://")) {
                Log.d("FullscreenVideo", "Path is a content URI")
            } else if (File(path).exists()) {
                Log.d("FullscreenVideo", "Path is a valid file path")
            } else {
                Log.w("FullscreenVideo", "Path does not point to a valid file")
            }

            path
        } else {
            Log.e("FullscreenVideo", "Video path file does not exist!")
            null
        }
    } catch (e: Exception) {
        Log.e("FullscreenVideo", "Error reading video path", e)
        null
    }
}
