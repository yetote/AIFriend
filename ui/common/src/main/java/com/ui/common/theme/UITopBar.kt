package com.ui.common.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 二级页面的通栏顶栏。
 *
 * 留空让极光渐变画布透上来，左侧以圆形轻玻璃胶囊承载返回按钮，
 * 底部仅用 0.5dp hairline 交代内容起点，与全站卡片语言保持一致。
 */
@Composable
fun UITopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .uiHairlineBottom()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .uiControl(shape = CircleShape, color = UIGlassSurfaceStrong)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = UIFriendBarTitleColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}
