package ai.wallpaper.aurora.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 视频缩略图生成系统 - 纯内存实现
 * 动态生成缩略图，不使用磁盘缓存
 */
object VideoThumbnailCache {
    private const val MAX_THUMBNAIL_WIDTH = 240
    private const val MAX_THUMBNAIL_HEIGHT = 320
    private const val PRIMARY_FRAME_TIME_US = 500_000L
    private const val SECONDARY_FRAME_TIME_US = 1_000_000L
    private val generationMutex = Mutex()

    /**
     * 动态生成视频缩略图
     */
    suspend fun getThumbnail(context: Context, videoUri: Uri, displayMode: String = "fit"): Bitmap? = withContext(Dispatchers.IO) {
        try {
            generationMutex.withLock {
                extractThumbnail(context, videoUri, displayMode)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoThumbnailCache", "Failed to get thumbnail", e)
            null
        }
    }

    /**
     * 从视频提取缩略图并根据显示模式处理
     */
    private fun extractThumbnail(context: Context, videoUri: Uri, displayMode: String): Bitmap? {
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

            frame?.let {
                if (displayMode == "fit") {
                    scaleBitmapPreservingAspectRatio(it, MAX_THUMBNAIL_WIDTH, MAX_THUMBNAIL_HEIGHT)
                } else {
                    scaleBitmapCropToFill(it, MAX_THUMBNAIL_WIDTH, MAX_THUMBNAIL_HEIGHT)
                }
            }
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
     * 将 Bitmap 等比缩放到目标边界内（fit模式）
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
     * 将 Bitmap 等比缩放后裁剪填充目标尺寸（fill模式）
     */
    private fun scaleBitmapCropToFill(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceWidth = source.width
        val sourceHeight = source.height

        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return source
        }

        // 计算缩放比例，选择能填满目标尺寸的较大值
        val widthScale = targetWidth.toFloat() / sourceWidth.toFloat()
        val heightScale = targetHeight.toFloat() / sourceHeight.toFloat()
        val scale = maxOf(widthScale, heightScale)

        // 缩放后的尺寸
        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()

        // 先缩放
        val scaled = if (scale != 1f) {
            Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true).also {
                if (it != source) {
                    source.recycle()
                }
            }
        } else {
            source
        }

        // 裁剪中心区域
        val cropX = ((scaledWidth - targetWidth) / 2).coerceAtLeast(0)
        val cropY = ((scaledHeight - targetHeight) / 2).coerceAtLeast(0)
        val cropWidth = targetWidth.coerceAtMost(scaledWidth)
        val cropHeight = targetHeight.coerceAtMost(scaledHeight)

        return Bitmap.createBitmap(scaled, cropX, cropY, cropWidth, cropHeight).also {
            if (it != scaled) {
                scaled.recycle()
            }
        }
    }
}
