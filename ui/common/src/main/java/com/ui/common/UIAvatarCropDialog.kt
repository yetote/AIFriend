package com.ui.common

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 头像裁剪：双指缩放 + 拖动选择范围，确认后输出正方形 JPEG。
 * 选图后先经过这里，再交给 AvatarStore 落盘。
 */
@Composable
fun UIAvatarCropDialog(
    uri: Uri,
    onCancel: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    val image = rememberUriBitmap(uri, maxSize = 1024)
    var scale by remember(image) { mutableFloatStateOf(0f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableFloatStateOf(0f) }
    var initialized by remember(image) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调整头像",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(onClick = onCancel) {
                    Text(text = "取消", color = Color.White)
                }
            }
            Text(
                text = "双指缩放，拖动选择显示范围",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight)
                Box(
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { boxSize = it.width.toFloat() }
                        .pointerInput(image, boxSize) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val bitmap = image ?: return@detectTransformGestures
                                if (boxSize <= 0f || scale <= 0f) return@detectTransformGestures

                                val minScale = max(
                                    boxSize / bitmap.width,
                                    boxSize / bitmap.height
                                )
                                val newScale = (scale * zoom).coerceIn(minScale, minScale * 5f)
                                val focus = centroid - offset
                                var newOffset = centroid - focus * (newScale / scale) + pan

                                val drawWidth = bitmap.width * newScale
                                val drawHeight = bitmap.height * newScale
                                newOffset = Offset(
                                    newOffset.x.coerceIn(boxSize - drawWidth, 0f),
                                    newOffset.y.coerceIn(boxSize - drawHeight, 0f)
                                )
                                scale = newScale
                                offset = newOffset
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (image != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (scale <= 0f) return@Canvas
                            drawImage(
                                image = image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(image.width, image.height),
                                dstOffset = IntOffset(
                                    offset.x.roundToInt(),
                                    offset.y.roundToInt()
                                ),
                                dstSize = IntSize(
                                    (image.width * scale).roundToInt(),
                                    (image.height * scale).roundToInt()
                                ),
                                filterQuality = FilterQuality.Medium
                            )

                            // 三分线，帮助构图
                            val stepX = size.width / 3f
                            val stepY = size.height / 3f
                            for (index in 1..2) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = Offset(stepX * index, 0f),
                                    end = Offset(stepX * index, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = Offset(0f, stepY * index),
                                    end = Offset(size.width, stepY * index),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "图片加载中…",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            LaunchedEffect(image, boxSize) {
                val bitmap = image ?: return@LaunchedEffect
                if (boxSize <= 0f || initialized) return@LaunchedEffect
                val cover = max(boxSize / bitmap.width, boxSize / bitmap.height)
                scale = cover
                offset = Offset(
                    (boxSize - bitmap.width * cover) / 2f,
                    (boxSize - bitmap.height * cover) / 2f
                )
                initialized = true
            }

            Button(
                onClick = {
                    val bitmap = image ?: return@Button
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            cropToJpeg(bitmap.asAndroidBitmap(), scale, offset, boxSize)
                        }
                        if (bytes != null) onConfirm(bytes)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B6BFF))
            ) {
                Text(text = "使用这张", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun cropToJpeg(source: Bitmap, scale: Float, offset: Offset, boxSize: Float): ByteArray? {
    if (scale <= 0f || boxSize <= 0f) return null
    return runCatching {
        val side = (boxSize / scale).roundToInt()
            .coerceIn(1, minOf(source.width, source.height))
        val left = (-offset.x / scale).roundToInt()
            .coerceIn(0, source.width - side)
        val top = (-offset.y / scale).roundToInt()
            .coerceIn(0, source.height - side)

        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        val output = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 92, output)
        if (cropped !== source) cropped.recycle()
        output.toByteArray()
    }.getOrNull()
}
