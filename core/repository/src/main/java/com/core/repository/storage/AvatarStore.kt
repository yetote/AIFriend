package com.core.repository.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 头像文件存储：把相册里的图片裁成正方形、缩放到 256x256 后存到应用私有目录。
 * 不保存 content Uri，避免授权失效；文件路径写进好友 / 用户档案。
 */
class AvatarStore(private val context: Context) {

    suspend fun save(source: Uri, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, AVATAR_DIR).apply { mkdirs() }
            // 文件名带时间戳：换头像后路径会变，Compose 才会重新加载图片
            val target = File(dir, "${id}_${System.currentTimeMillis()}.jpg")
            val bitmap = decodeSampled(source, MAX_SOURCE_SIZE) ?: return@runCatching null

            val side = minOf(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                side,
                side
            )
            val scaled = Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)

            FileOutputStream(target).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            }

            if (scaled !== square) scaled.recycle()
            if (square !== bitmap) square.recycle()
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

    private fun decodeSampled(uri: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxSize * 2 &&
            bounds.outHeight / sampleSize > maxSize * 2
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private companion object {
        const val AVATAR_DIR = "avatars"
        const val AVATAR_SIZE = 256
        const val MAX_SOURCE_SIZE = 512
    }
}
