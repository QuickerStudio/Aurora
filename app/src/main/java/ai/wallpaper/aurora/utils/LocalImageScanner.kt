package ai.wallpaper.aurora.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地图片扫描器
 * 用于扫描设备上的图片文件
 */
object LocalImageScanner {

    /**
     * 扫描本地图片文件
     * @param context Context
     * @param offset 偏移量，用于分页加载
     * @param limit 每次加载的数量
     * @return 图片列表
     */
    suspend fun scanImages(
        context: Context,
        offset: Int = 0,
        limit: Int = 20
    ): List<LocalVideo> = withContext(Dispatchers.IO) {
        val images = mutableListOf<LocalVideo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var index = 0
                while (cursor.moveToNext()) {
                    if (index++ < offset) continue
                    if (images.size >= limit) break

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val size = cursor.getLong(sizeColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    images.add(
                        LocalVideo(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            duration = 0L,
                            size = size,
                            mimeType = mimeType,
                            mediaType = MediaType.IMAGE,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        images
    }
}
