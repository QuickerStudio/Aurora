package ai.wallpaper.aurora.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private const val MAX_THUMBNAIL_WIDTH = 240
    private const val MAX_THUMBNAIL_HEIGHT = 320
    private const val PRIMARY_FRAME_TIME_US = 500_000L
    private const val SECONDARY_FRAME_TIME_US = 1_000_000L
    private val generationMutex = Mutex()

    /**
     * 获取视频缩略图，优先从缓存读取
     */
    suspend fun getThumbnail(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = generateCacheKey(videoUri)
            val cacheFile = getCacheFile(context, cacheKey)

            // 优先从缓存读取
            if (cacheFile.exists()) {
                return@withContext BitmapFactory.decodeFile(cacheFile.absolutePath)
            }

            generationMutex.withLock {
                if (cacheFile.exists()) {
                    return@withLock BitmapFactory.decodeFile(cacheFile.absolutePath)
                }

                val thumbnail = extractThumbnail(context, videoUri)
                thumbnail?.let { saveThumbnail(cacheFile, it) }
                thumbnail
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to get thumbnail", e)
            null
        }
    }

    /**
     * 从视频提取缩略图，优先跳过开头黑帧
     */
    private fun extractThumbnail(context: Context, videoUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val safeFrameTimeUs = computeFrameTimeUs(durationMs)

            val frame = retriever.getFrameAtTime(safeFrameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(SECONDARY_FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(-1)

            frame?.let { scaleBitmapPreservingAspectRatio(it, MAX_THUMBNAIL_WIDTH, MAX_THUMBNAIL_HEIGHT) }
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

    private fun computeFrameTimeUs(durationMs: Long): Long {
        if (durationMs <= 0L) return PRIMARY_FRAME_TIME_US
        val durationUs = durationMs * 1000L
        return PRIMARY_FRAME_TIME_US.coerceAtMost((durationUs * 0.3f).toLong().coerceAtLeast(PRIMARY_FRAME_TIME_US))
    }

    /**
     * 将 Bitmap 等比缩放到目标边界内
     */
    private fun scaleBitmapPreservingAspectRatio(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return source
        }

        val widthScale = maxWidth.toFloat() / sourceWidth.toFloat()
        val heightScale = maxHeight.toFloat() / sourceHeight.toFloat()
        val scale = minOf(widthScale, heightScale, 1f)

        if (scale >= 1f) {
            return source
        }

        val targetWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)

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

    fun deleteThumbnail(context: Context, videoUri: Uri) {
        try {
            getCacheFile(context, generateCacheKey(videoUri)).takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to delete thumbnail", e)
        }
    }

    fun pruneCache(context: Context, retainedUris: Collection<Uri>) {
        pruneCacheByKey(context, retainedUris.mapTo(hashSetOf()) { generateCacheKey(it) })
    }

    fun pruneCacheByUriString(context: Context, retainedUriStrings: Collection<String>) {
        pruneCacheByKey(
            context,
            retainedUriStrings.asSequence()
                .filter { it.isNotBlank() }
                .map { generateCacheKey(Uri.parse(it)) }
                .toHashSet()
        )
    }

    private fun pruneCacheByKey(context: Context, retainedKeys: Set<String>) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (!cacheDir.exists()) return

            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val fileKey = file.nameWithoutExtension
                    if (fileKey !in retainedKeys) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to prune cache", e)
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
