package ai.wallpaper.aurora.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地视频扫描器
 * 用于扫描设备上的视频文件
 */
object LocalVideoScanner {

    /**
     * 扫描本地视频文件
     * @param context Context
     * @param offset 偏移量，用于分页加载
     * @param limit 每次加载的数量
     * @return 视频URI列表
     */
    suspend fun scanVideos(
        context: Context,
        offset: Int = 0,
        limit: Int = 20
    ): List<LocalVideo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<LocalVideo>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                var index = 0
                while (cursor.moveToNext()) {
                    if (index++ < offset) continue
                    if (videos.size >= limit) break

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    videos.add(
                        LocalVideo(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            duration = duration,
                            size = size,
                            mimeType = mimeType,
                            mediaType = MediaType.VIDEO,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videos
    }
}

/**
 * 媒体类型
 */
enum class MediaType {
    VIDEO,
    IMAGE
}

/**
 * 本地媒体数据模型
 */
data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val size: Long,
    val mimeType: String,
    val mediaType: MediaType = MediaType.VIDEO,
    val dateAdded: Long = 0L
)
