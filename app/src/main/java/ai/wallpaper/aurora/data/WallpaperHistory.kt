package ai.wallpaper.aurora.data

import ai.wallpaper.aurora.utils.MediaType
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 壁纸历史记录数据模型
 */
data class WallpaperHistoryItem(
    val id: String,
    val videoUri: String,
    val timestamp: Long,
    val displayName: String? = null,
    val mediaType: MediaType = MediaType.VIDEO
)

/**
 * 壁纸历史记录管理器
 */
object WallpaperHistoryManager {
    private const val HISTORY_FILE = "wallpaper_history.json"
    private const val MAX_HISTORY_SIZE = 50 // 最多保存50条历史记录

    /**
     * 添加壁纸历史记录
     * 如果已存在相同的 URI，则不添加（避免热切换时重复添加）
     */
    fun addHistory(context: Context, videoUri: Uri, displayName: String? = null, mediaType: MediaType = MediaType.VIDEO) {
        val history = loadHistory(context).toMutableList()
        val uriString = videoUri.toString()

        // 检查是否已存在相同的 URI（第一条记录）
        if (history.isNotEmpty() && history[0].videoUri == uriString) {
            // 已存在且是最新的记录，不添加（避免热切换重复）
            return
        }

        // 创建新记录
        val newItem = WallpaperHistoryItem(
            id = System.currentTimeMillis().toString(),
            videoUri = uriString,
            timestamp = System.currentTimeMillis(),
            displayName = displayName,
            mediaType = mediaType
        )

        // 移除旧的相同 URI 记录（如果存在）
        history.removeAll { it.videoUri == uriString }

        // 添加到列表开头
        history.add(0, newItem)

        // 限制历史记录数量
        if (history.size > MAX_HISTORY_SIZE) {
            history.subList(MAX_HISTORY_SIZE, history.size).clear()
        }

        // 保存历史记录
        saveHistory(context, history)
    }

    /**
     * 加载壁纸历史记录
     */
    fun loadHistory(context: Context): List<WallpaperHistoryItem> {
        return try {
            val file = File(context.filesDir, HISTORY_FILE)
            if (!file.exists()) {
                return emptyList()
            }

            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)

            (0 until jsonArray.length()).mapNotNull { index ->
                try {
                    val jsonObject = jsonArray.getJSONObject(index)
                    val mediaTypeString = jsonObject.optString("mediaType", "VIDEO")
                    val mediaType = try {
                        MediaType.valueOf(mediaTypeString)
                    } catch (e: Exception) {
                        MediaType.VIDEO
                    }

                    WallpaperHistoryItem(
                        id = jsonObject.getString("id"),
                        videoUri = jsonObject.getString("videoUri"),
                        timestamp = jsonObject.getLong("timestamp"),
                        displayName = if (jsonObject.has("displayName")) jsonObject.getString("displayName") else null,
                        mediaType = mediaType
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 保存壁纸历史记录
     */
    private fun saveHistory(context: Context, history: List<WallpaperHistoryItem>) {
        try {
            val jsonArray = JSONArray()
            history.forEach { item ->
                val jsonObject = JSONObject().apply {
                    put("id", item.id)
                    put("videoUri", item.videoUri)
                    put("timestamp", item.timestamp)
                    put("mediaType", item.mediaType.name)
                    item.displayName?.let { put("displayName", it) }
                }
                jsonArray.put(jsonObject)
            }

            val file = File(context.filesDir, HISTORY_FILE)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 删除历史记录
     */
    fun deleteHistory(context: Context, id: String) {
        val history = loadHistory(context).toMutableList()
        history.removeAll { it.id == id }
        saveHistory(context, history)
    }

    /**
     * 清空所有历史记录
     */
    fun clearHistory(context: Context) {
        val file = File(context.filesDir, HISTORY_FILE)
        file.delete()
    }
}
