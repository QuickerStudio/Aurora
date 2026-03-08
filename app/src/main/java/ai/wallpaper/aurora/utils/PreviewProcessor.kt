package ai.wallpaper.aurora.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

/**
 * 通用预览处理器
 * 用于处理视频和图片的缩略图生成，支持优先级队列和失败重试
 */
class PreviewProcessor(
    context: Context,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val pendingItems = LinkedHashMap<String, PreviewRequest>()
    private val failedKeys = mutableSetOf<String>()
    private var workerRunning = false

    fun submit(items: List<PreviewRequest>, displayMode: String = "fit", onPreviewReady: (Int, Uri, Bitmap) -> Unit) {
        synchronized(this) {
            val activeKeys = items.mapTo(linkedSetOf()) { it.stableKey }
            pendingItems.keys.retainAll(activeKeys)
            failedKeys.retainAll(activeKeys)

            items.forEach { item ->
                val uri = item.uri ?: return@forEach
                if (item.hasPreview) {
                    pendingItems.remove(item.stableKey)
                    failedKeys.remove(item.stableKey)
                    return@forEach
                }
                if (item.stableKey in failedKeys) return@forEach
                pendingItems[item.stableKey] = item.copy(uri = uri)
            }

            if (workerRunning || pendingItems.isEmpty()) {
                return
            }
            workerRunning = true
        }

        scope.launch {
            try {
                while (true) {
                    val next = synchronized(this@PreviewProcessor) {
                        val entry = pendingItems.entries.firstOrNull()
                        if (entry != null) {
                            pendingItems.remove(entry.key)
                            entry.value
                        } else {
                            workerRunning = false
                            null
                        }
                    } ?: break

                    val uri = next.uri ?: continue
                    try {
                        val thumbnail = if (next.mediaType == MediaType.IMAGE) {
                            // 图片：直接加载并缩放
                            loadImageThumbnail(uri, displayMode)
                        } else {
                            // 视频：使用VideoThumbnailCache
                            VideoThumbnailCache.getThumbnail(appContext, uri, displayMode)
                        }

                        if (thumbnail != null) {
                            launch(mainDispatcher) {
                                onPreviewReady(next.id, uri, thumbnail)
                            }
                        } else {
                            synchronized(this@PreviewProcessor) {
                                failedKeys.add(next.stableKey)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        android.util.Log.e("PreviewProcessor", "Failed to generate preview for $uri", e)
                        synchronized(this@PreviewProcessor) {
                            failedKeys.add(next.stableKey)
                        }
                    }
                }
            } finally {
                synchronized(this@PreviewProcessor) {
                    if (pendingItems.isEmpty()) {
                        workerRunning = false
                    }
                }
            }
        }
    }

    private fun loadImageThumbnail(uri: Uri, displayMode: String): Bitmap? {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                // 计算缩放比例
                val targetWidth = 240
                val targetHeight = 320
                val widthScale = options.outWidth.toFloat() / targetWidth
                val heightScale = options.outHeight.toFloat() / targetHeight
                val scale = maxOf(widthScale, heightScale, 1f).toInt()

                // 重新打开流并解码
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = scale
                    }
                    val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)

                    // 根据显示模式处理图片
                    bitmap?.let {
                        if (displayMode == "fit") {
                            scaleBitmapPreservingAspectRatio(it, targetWidth, targetHeight)
                        } else {
                            scaleBitmapCropToFill(it, targetWidth, targetHeight)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PreviewProcessor", "Failed to load image thumbnail", e)
            null
        }
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

        val scaledWidth = (sourceWidth * scale).toInt()
        val scaledHeight = (sourceHeight * scale).toInt()

        // 先缩放
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        if (scaledBitmap != source) {
            source.recycle()
        }

        // 计算裁剪位置（居中裁剪）
        val xOffset = ((scaledWidth - targetWidth) / 2).coerceAtLeast(0)
        val yOffset = ((scaledHeight - targetHeight) / 2).coerceAtLeast(0)

        // 裁剪到目标尺寸
        return Bitmap.createBitmap(
            scaledBitmap,
            xOffset,
            yOffset,
            targetWidth.coerceAtMost(scaledWidth),
            targetHeight.coerceAtMost(scaledHeight)
        ).also {
            if (it != scaledBitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    fun clear() {
        synchronized(this) {
            pendingItems.clear()
            failedKeys.clear()
        }
    }

    fun cancel() {
        clear()
        scope.cancel()
    }

    data class PreviewRequest(
        val id: Int,
        val uri: Uri?,
        val hasPreview: Boolean,
        val mediaType: MediaType = MediaType.VIDEO
    ) {
        val stableKey: String = "$id|${uri?.toString().orEmpty()}"
    }
}
