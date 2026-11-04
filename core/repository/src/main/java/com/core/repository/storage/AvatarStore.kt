package com.core.repository.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 头像文件存储：接收裁剪页产出的正方形 JPEG，缩放到 256x256 后存到应用私有目录。
 * 文件名带时间戳，换头像后路径会变，Compose 才会重新加载图片。
 */
class AvatarStore(private val context: Context) {

    suspend fun save(bytes: ByteArray, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, AVATAR_DIR).apply { mkdirs() }
            val target = File(dir, "${id}_${System.currentTimeMillis()}.jpg")

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching null
            val scaled = Bitmap.createScaledBitmap(bitmap, AVATAR_SIZE, AVATAR_SIZE, true)

            FileOutputStream(target).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }

            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            // 清理这个 id 的历史头像，避免文件越攒越多
            dir.listFiles()?.forEach { file ->
                if (file != target && (file.name == "$id.jpg" || file.name.startsWith("${id}_"))) {
                    file.delete()
                }
            }

            target.absolutePath
        }.getOrNull()
    }

    private companion object {
        const val AVATAR_DIR = "avatars"
        const val AVATAR_SIZE = 256
    }
}
