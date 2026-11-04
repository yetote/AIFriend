package com.ui.friend

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.getRandomColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一头像组件：有自定义图片就显示图片，否则显示名字首字 + 稳定配色。
 */
@Composable
fun UIAvatar(
    name: String,
    avatarPath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.25f),
    fallbackColor: Color? = null,
    imageBitmap: ImageBitmap? = null
) {
    val loaded = rememberAvatarBitmap(avatarPath)
    val bitmap = imageBitmap ?: loaded
    val background = fallbackColor ?: getRandomColor(name.ifBlank { "?" })

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap == null) background else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.trim().take(1).ifEmpty { "?" },
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF374151)
            )
        }
    }
}

/** 从本地文件路径读取头像，切换路径时自动重新加载。 */
@Composable
fun rememberAvatarBitmap(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return bitmap
}

/** 创建页选择图片后立即预览用（采样解码，避免大图 OOM）。 */
@Composable
fun rememberUriBitmap(uri: Uri?, maxSize: Int = 512): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = uri, key2 = maxSize) {
        value = uri?.let { source ->
            withContext(Dispatchers.IO) {
                runCatching { decodeSampled(context, source, maxSize) }.getOrNull()
            }
        }
    }
    return bitmap
}

/** 裁剪结果预览用。 */
@Composable
fun rememberBytesBitmap(bytes: ByteArray?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = bytes) {
        value = bytes?.let { data ->
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

private fun decodeSampled(
    context: android.content.Context,
    uri: Uri,
    maxSize: Int = 512
): ImageBitmap? {
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
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
    }
}
