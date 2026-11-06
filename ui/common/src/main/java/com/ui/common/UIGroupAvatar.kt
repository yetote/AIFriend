package com.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.data.getRandomColor

/** 群头像里的一个成员：只需要画图要用的两个字段。 */
data class UIGroupMember(
    val name: String,
    val avatarPath: String? = null
)

/**
 * 群头像：把成员头像拼成一张。
 *
 * 聊天室取名字首字的话，字数再多也只显示一个字，
 * 「瞎聊」「瞎搞」「瞎想」看起来完全一样；拼成员头像才能一眼认出是哪个房间，
 * 这也是微信 / Telegram 群聊的既有做法。
 *
 * 按人数排布：
 * - 1 人：整个圆
 * - 2 人：左右两半
 * - 3 人：左边一个高的，右边两个叠着
 * - 4 人及以上：2x2，只取前四个（多出来的不影响识别，反而更花）
 */
@Composable
fun UIGroupAvatar(
    members: List<UIGroupMember>,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val cells = members.take(4)
    // 分隔缝用底色透出来实现，不额外画线，圆角裁切后边缘也干净
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(UIGroupAvatarGap),
        contentAlignment = Alignment.Center
    ) {
        when (cells.size) {
            0 -> EmptyGroupCell(size)
            1 -> GroupCell(cells[0], Modifier.fillMaxSize(), size * 0.40f)
            2 -> Row(Modifier.fillMaxSize()) {
                GroupCell(cells[0], Modifier.weight(1f).fillMaxHeight(), size * 0.30f)
                Spacer(Modifier.width(UIGroupAvatarGapWidth))
                GroupCell(cells[1], Modifier.weight(1f).fillMaxHeight(), size * 0.30f)
            }
            3 -> Row(Modifier.fillMaxSize()) {
                GroupCell(cells[0], Modifier.weight(1f).fillMaxHeight(), size * 0.28f)
                Spacer(Modifier.width(UIGroupAvatarGapWidth))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    GroupCell(cells[1], Modifier.weight(1f).fillMaxWidth(), size * 0.24f)
                    Spacer(Modifier.height(UIGroupAvatarGapWidth))
                    GroupCell(cells[2], Modifier.weight(1f).fillMaxWidth(), size * 0.24f)
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    GroupCell(cells[0], Modifier.weight(1f).fillMaxHeight(), size * 0.24f)
                    Spacer(Modifier.width(UIGroupAvatarGapWidth))
                    GroupCell(cells[1], Modifier.weight(1f).fillMaxHeight(), size * 0.24f)
                }
                Spacer(Modifier.height(UIGroupAvatarGapWidth))
                Row(Modifier.weight(1f)) {
                    GroupCell(cells[2], Modifier.weight(1f).fillMaxHeight(), size * 0.24f)
                    Spacer(Modifier.width(UIGroupAvatarGapWidth))
                    GroupCell(cells[3], Modifier.weight(1f).fillMaxHeight(), size * 0.24f)
                }
            }
        }
    }
}

/** 一格：有图放图，没有就用自己的配色铺底 + 首字，和单个头像的兜底保持一致。 */
@Composable
private fun GroupCell(
    member: UIGroupMember,
    modifier: Modifier,
    fontSize: Dp
) {
    val bitmap = rememberAvatarBitmap(member.avatarPath)
    Box(
        modifier = modifier.background(
            if (bitmap == null) getRandomColor(member.name.ifBlank { "?" }) else Color.Transparent
        ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = member.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = member.name.trim().take(1).ifEmpty { "?" },
                fontSize = fontSize.value.sp,
                fontWeight = FontWeight.Bold,
                color = UIGroupAvatarTextColor
            )
        }
    }
}

/** 成员还没解析出来时的占位，别让卡片位置塌掉。 */
@Composable
private fun EmptyGroupCell(size: Dp) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "群",
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            color = UIGroupAvatarTextColor
        )
    }
}

/** 成员之间的缝，透出来的就是这层颜色。 */
private val UIGroupAvatarGap = Color(0xFFFDFDFF)
private val UIGroupAvatarGapWidth = 1.5.dp
private val UIGroupAvatarTextColor = Color(0xFF374151)
