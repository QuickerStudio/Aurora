package ai.wallpaper.aurora.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 视频缩略图缓存系统
 * 避免实时生成视频预览，减少 MediaCodec 资源占用
 */
object VideoThumbnailCache {
    private const val CACHE_DIR = "video_thumbnails"
    private const val THUMBNAIL_WIDTH = 240
    private const val THUMBNAIL_HEIGHT = 320

    /**
     * 获取视频缩略图，优先从缓存读取
     */
    suspend fun getThumbnail(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = generateCacheKey(videoUri)
            val cacheFile = getCacheFile(context, cacheKey)

            // 优先从缓存读取
            if (cacheFile.exists()) {
                return@withContext android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
            }

            // 缓存不存在，生成缩略图
            val thumbnail = extractThumbnail(context, videoUri)

            // 保存到缓存
            thumbnail?.let { saveThumbnail(cacheFile, it) }

            thumbnail
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to get thumbnail", e)
            null
        }
    }

    /**
     * 从视频提取首帧缩略图
     */
    private fun extractThumbnail(context: Context, videoUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            // 缩放到目标尺寸以节省内存
            frame?.let { scaleBitmap(it, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to extract thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                android.util.Log.e("VideoThumbnailCache", "Failed to release retriever", e)
            }
        }
    }

    /**
     * 缩放 Bitmap 到目标尺寸
     */
    private fun scaleBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true).also {
            if (it != source) {
                source.recycle()
            }
        }
    }

    /**
     * 保存缩略图到缓存
     */
    private fun saveThumbnail(cacheFile: File, bitmap: Bitmap) {
        try {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to save thumbnail", e)
        }
    }

    /**
     * 生成缓存键（基于 URI 的 MD5）
     */
    private fun generateCacheKey(uri: Uri): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * 获取缓存文件路径
     */
    private fun getCacheFile(context: Context, cacheKey: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        return File(cacheDir, "$cacheKey.jpg")
    }

    /**
     * 清理缓存
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to clear cache", e)
        }
    }
}
