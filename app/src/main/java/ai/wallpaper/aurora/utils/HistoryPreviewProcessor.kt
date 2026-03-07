package ai.wallpaper.aurora.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

class HistoryPreviewProcessor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingItems = LinkedHashMap<Int, Uri>()
    private val failedIds = mutableSetOf<Int>()
    private var workerRunning = false

    fun submit(items: List<PreviewRequest>, onPreviewReady: (Int, Uri, Bitmap) -> Unit) {
        synchronized(this) {
            val activeIds = items.mapTo(linkedSetOf()) { it.id }
            pendingItems.keys.retainAll(activeIds)
            failedIds.retainAll(activeIds)

            items.forEach { item ->
                if (item.hasPreview || item.id in failedIds) return@forEach
                val uri = item.uri ?: return@forEach
                pendingItems.putIfAbsent(item.id, uri)
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
                            entry.key to entry.value
                        } else {
                            workerRunning = false
                            null
                        }
                    } ?: break

                    val (id, uri) = next
                    try {
                        val thumbnail = VideoThumbnailCache.getThumbnail(appContext, uri)
                        if (thumbnail != null) {
                            launch(Dispatchers.Main.immediate) {
                                onPreviewReady(id, uri, thumbnail)
                            }
                        } else {
                            synchronized(this@HistoryPreviewProcessor) {
                                failedIds.add(id)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        android.util.Log.e("HistoryPreviewProcessor", "Failed to generate preview for $uri", e)
                        synchronized(this@HistoryPreviewProcessor) {
                            failedIds.add(id)
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

    fun clear() {
        synchronized(this) {
            pendingItems.clear()
            failedIds.clear()
        }
    }

    fun cancel() {
        clear()
        scope.cancel()
    }

    data class PreviewRequest(
        val id: Int,
        val uri: Uri?,
        val hasPreview: Boolean
    )
}
