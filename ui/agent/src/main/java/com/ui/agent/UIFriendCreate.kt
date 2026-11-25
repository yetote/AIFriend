package com.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIAtelierBrass
import com.ui.common.theme.UIAtelierBrassBg
import com.ui.common.theme.UIAtelierJade
import com.ui.common.theme.UIAtelierJadeBg
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor2
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.uiControl

/**
 * 首页搜索栏（独立可复用）：置顶后用户无需翻过半屏即可快速检索好友、聊天室或模型。
 */
@Composable
fun UIFriendSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "搜索好友、角色、聊天室或模型…",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .uiControl(
                shape = RoundedCornerShape(14.dp),
                color = UIGlassSurfaceStrong,
                borderColor = UIGlassStroke
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = UIFriendBarItemMetaColor,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        color = UIFriendBarItemMetaColor.copy(alpha = 0.85f),
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = UIFriendBarTitleColor2,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                cursorBrush = SolidColor(UIFriendBarTagTextBlue),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(UIAtelierSand)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清空搜索",
                    tint = UIFriendBarItemMetaColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * 全部智能体区块头：标题计数 + 一键探活按钮 + 新建好友按钮，以及紧凑的探活状态提示条。
 */
@Composable
fun UIFriendCreate(
    modifier: Modifier = Modifier,
    createdCount: Int = 0,
    searchText: String = "",
    onSearchTextChange: (String) -> Unit = {},
    onCreateClick: () -> Unit = {},
    showSearchBar: Boolean = true,
    isProbing: Boolean = false,
    probeCompleted: Int = 0,
    probeTotal: Int = 0,
    probedCount: Int = 0,
    aliveCount: Int = 0,
    failedCount: Int = 0,
    onProbeClick: (() -> Unit)? = null,
    onCancelProbe: (() -> Unit)? = null,
    onRetryFailedClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "我的智能体",
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor2,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
                Text(
                    text = "$createdCount",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(UIAtelierSand)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarItemMetaColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onProbeClick != null && createdCount > 0) {
                    ProbeAllButton(
                        isProbing = isProbing,
                        probeCompleted = probeCompleted,
                        probeTotal = probeTotal,
                        probedCount = probedCount,
                        failedCount = failedCount,
                        onProbe = onProbeClick,
                        onCancel = onCancelProbe,
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .background(UIFriendBarCreateButtonBg)
                        .clickable { onCreateClick() }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "创建",
                        tint = UIFriendBarCreateButtonText,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "新建",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarCreateButtonText,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }

        if (createdCount > 0 && (isProbing || probedCount > 0)) {
            ProbeSummaryStrip(
                isProbing = isProbing,
                probeCompleted = probeCompleted,
                probeTotal = probeTotal,
                createdCount = createdCount,
                probedCount = probedCount,
                aliveCount = aliveCount,
                failedCount = failedCount,
                onCancelProbe = onCancelProbe,
                onRetryFailedClick = onRetryFailedClick,
            )
        }

        if (showSearchBar) {
            UIFriendSearchBar(
                value = searchText,
                onValueChange = onSearchTextChange
            )
        }
    }
}

@Composable
private fun ProbeAllButton(
    isProbing: Boolean,
    probeCompleted: Int,
    probeTotal: Int,
    probedCount: Int,
    failedCount: Int,
    onProbe: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    val bg: Color
    val borderColor: Color
    val contentColor: Color

    when {
        isProbing -> {
            bg = UIAtelierBrassBg
            borderColor = UIAtelierBrass.copy(alpha = 0.4f)
            contentColor = UIAtelierBrass
        }

        failedCount > 0 -> {
            bg = UIFriendBarRed.copy(alpha = 0.09f)
            borderColor = UIFriendBarRed.copy(alpha = 0.28f)
            contentColor = UIFriendBarRed
        }

        probedCount > 0 -> {
            bg = UIAtelierJadeBg
            borderColor = UIAtelierJade.copy(alpha = 0.30f)
            contentColor = UIAtelierJade
        }

        else -> {
            bg = UIAtelierSand
            borderColor = UIGlassStroke
            contentColor = UIFriendBarTitleColor2
        }
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable {
                if (isProbing) onCancel?.invoke() else onProbe()
            }
            .padding(horizontal = 10.dp, vertical = 6.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isProbing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = contentColor,
            )
            val progressText = if (probeTotal > 0) "探活 $probeCompleted/$probeTotal" else "探活中"
            Text(
                text = progressText,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "全部探活",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (probedCount > 0) "重测" else "探活",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun ProbeSummaryStrip(
    isProbing: Boolean,
    probeCompleted: Int,
    probeTotal: Int,
    createdCount: Int,
    probedCount: Int,
    aliveCount: Int,
    failedCount: Int,
    onCancelProbe: (() -> Unit)?,
    onRetryFailedClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(11.dp)
    val dotColor = when {
        isProbing -> UIAtelierBrass
        failedCount > 0 -> UIFriendBarRed
        else -> UIAtelierJade
    }
    val summary = when {
        isProbing && probeTotal > 0 ->
            "正在按模型去重探活（$probeCompleted / $probeTotal 组），开局将自动复用"
        isProbing ->
            "正在探活中，结果将实时点亮并在开局时自动复用"
        failedCount > 0 ->
            "已测 $probedCount/$createdCount · $aliveCount 可用 · $failedCount 异常（开局自动复用）"
        else ->
            "已测 $probedCount/$createdCount · 全部 $aliveCount 位可用（开局自动复用）"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UIGlassSurfaceStrong)
            .border(1.dp, UIGlassStroke, shape)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 11.5.sp,
                color = UIFriendBarItemSubtitleColor,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        when {
            isProbing && onCancelProbe != null -> {
                Text(
                    text = "停止",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(UIAtelierBrassBg)
                        .clickable { onCancelProbe() }
                        .padding(horizontal = 8.dp, vertical = 3.5.dp),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIAtelierBrass,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            !isProbing && failedCount > 0 && onRetryFailedClick != null -> {
                Text(
                    text = "重测异常",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(UIFriendBarRed.copy(alpha = 0.10f))
                        .clickable { onRetryFailedClick() }
                        .padding(horizontal = 8.dp, vertical = 3.5.dp),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarRed,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun PUIFriendCreate() {
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.background(Color(0xFFF4EFE6))) {
        UIFriendCreate(
            createdCount = 8,
            searchText = query,
            onSearchTextChange = { query = it },
            onCreateClick = {},
            probedCount = 8,
            aliveCount = 6,
            failedCount = 2,
            onProbeClick = {},
            onRetryFailedClick = {},
        )
    }
}
