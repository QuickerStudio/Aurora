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

class HistoryPreviewProcessor(
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
                    val next = synchronized(this@HistoryPreviewProcessor) {
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
                            loadImageThumbnail(uri)
                        } else {
                            // 视频：使用VideoThumbnailCache
                            VideoThumbnailCache.getThumbnail(appContext, uri, displayMode)
                        }

                        if (thumbnail != null) {
                            launch(mainDispatcher) {
                                onPreviewReady(next.id, uri, thumbnail)
                            }
                        } else {
                            synchronized(this@HistoryPreviewProcessor) {
                                failedKeys.add(next.stableKey)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        android.util.Log.e("HistoryPreviewProcessor", "Failed to generate preview for $uri", e)
                        synchronized(this@HistoryPreviewProcessor) {
                            failedKeys.add(next.stableKey)
                        }
                    }
                }
            } finally {
                synchronized(this@HistoryPreviewProcessor) {
                    if (pendingItems.isEmpty()) {
                        workerRunning = false
                    }
                }
            }
        }
    }

    private fun loadImageThumbnail(uri: Uri): Bitmap? {
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
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HistoryPreviewProcessor", "Failed to load image thumbnail", e)
            null
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
