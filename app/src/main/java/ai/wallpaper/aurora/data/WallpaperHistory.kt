package ai.wallpaper.aurora.data

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
    val displayName: String? = null
)

/**
 * 壁纸历史记录管理器
 */
object WallpaperHistoryManager {
    private const val HISTORY_FILE = "wallpaper_history.json"
    private const val MAX_HISTORY_SIZE = 50 // 最多保存50条历史记录

    /**
     * 添加壁纸历史记录
     */
    fun addHistory(context: Context, videoUri: Uri, displayName: String? = null) {
        val history = loadHistory(context).toMutableList()

        // 创建新记录
        val newItem = WallpaperHistoryItem(
            id = System.currentTimeMillis().toString(),
            videoUri = videoUri.toString(),
            timestamp = System.currentTimeMillis(),
            displayName = displayName
        )

        // 检查是否已存在相同的URI，如果存在则移除旧记录
        history.removeAll { it.videoUri == videoUri.toString() }

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
                    WallpaperHistoryItem(
                        id = jsonObject.getString("id"),
                        videoUri = jsonObject.getString("videoUri"),
                        timestamp = jsonObject.getLong("timestamp"),
                        displayName = jsonObject.optString("displayName", null)
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
