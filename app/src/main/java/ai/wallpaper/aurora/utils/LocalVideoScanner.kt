package ai.wallpaper.aurora.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地视频扫描器
 * 用于扫描设备上的视频文件
 */
object LocalVideoScanner {

    // 支持的视频格式
    private val SUPPORTED_VIDEO_FORMATS = arrayOf(
        "video/mp4",
        "video/3gpp",
        "video/avi",
        "video/x-matroska", // mkv
        "video/quicktime",  // mov
        "video/x-msvideo",  // avi
        "video/webm"
    )

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
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val selection = buildString {
            append("${MediaStore.Video.Media.MIME_TYPE} IN (")
            append(SUPPORTED_VIDEO_FORMATS.joinToString(",") { "'$it'" })
            append(")")
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC LIMIT $limit OFFSET $offset"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val duration = it.getLong(durationColumn)
                    val size = it.getLong(sizeColumn)
                    val mimeType = it.getString(mimeTypeColumn)

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
                            mimeType = mimeType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        videos
    }
}

/**
 * 本地视频数据模型
 */
data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val size: Long,
    val mimeType: String
)
