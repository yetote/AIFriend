@file:OptIn(ExperimentalFoundationApi::class)

package com.ui.agent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.agent.data.UIFriendListData
import com.ui.agent.data.UIFriendProbeState
import com.ui.agent.data.groupByVendor
import com.ui.common.UIAvatar
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIAtelierBrass
import com.ui.common.theme.UIAtelierBrassBg
import com.ui.common.theme.UIAtelierInk
import com.ui.common.theme.UIAtelierJade
import com.ui.common.theme.UIAtelierJadeBg
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarItemTitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTabUnselectedText
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusTab
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard

/**
 * 智能体名录：按真实探活状态分段筛选（全部 / 可用 / 异常 / 未测）+ 按服务商独立分卡展示。
 */
@Composable
fun UIFriendCrewList(
    data: List<UIFriendListData>,
    onItemClick: (UIFriendListData) -> Unit = {},
    onItemLongClick: (UIFriendListData) -> Unit = {},
    isProbing: Boolean = false,
    onProbeFriend: ((UIFriendListData) -> Unit)? = null,
    onProbeAll: (() -> Unit)? = null,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AgentTabRow(
            data = data,
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )

        val visible = when (selectedTabIndex) {
            1 -> data.filter { it.probeState == UIFriendProbeState.OK }
            2 -> data.filter { it.probeState == UIFriendProbeState.FAILED }
            3 -> data.filter {
                it.probeState == UIFriendProbeState.IDLE || it.probeState == UIFriendProbeState.CHECKING
            }
            else -> data
        }
        val groups = visible.groupByVendor()

        if (groups.isEmpty()) {
            EmptyFilteredFriends(
                selectedTabIndex = selectedTabIndex,
                hasAnyProbed = data.any {
                    it.probeState == UIFriendProbeState.OK || it.probeState == UIFriendProbeState.FAILED
                },
                isProbing = isProbing,
                onProbeAll = onProbeAll,
            )
        } else {
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FriendGroupHeader(
                        name = group.name,
                        friends = group.friends,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
                    ) {
                        group.friends.forEachIndexed { index, agent ->
                            AgentListItem(
                                data = agent,
                                isBatchProbing = isProbing,
                                onClick = { onItemClick(agent) },
                                onLongClick = { onItemLongClick(agent) },
                                onProbeClick = onProbeFriend?.let { cb -> { cb(agent) } }
                            )
                            if (index < group.friends.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 74.dp, end = 16.dp)
                                        .height(0.75.dp)
                                        .background(UISeparator)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFilteredFriends(
    selectedTabIndex: Int,
    hasAnyProbed: Boolean,
    isProbing: Boolean,
    onProbeAll: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val message = when (selectedTabIndex) {
            1 -> if (hasAnyProbed) "暂无探活可用的智能体" else "尚未进行探活检测，测完即可一眼看清谁在线"
            2 -> if (hasAnyProbed) "太棒了，当前没有不可用的智能体" else "尚未进行探活检测"
            3 -> "全部智能体都已完成探活"
            else -> "当前筛选下暂无智能体"
        }
        Text(
            text = message,
            fontSize = 13.sp,
            color = UIFriendBarItemMetaColor
        )
        if (!hasAnyProbed && (selectedTabIndex == 1 || selectedTabIndex == 2) && onProbeAll != null) {
            Text(
                text = if (isProbing) "探活进行中…" else "立即一键探活",
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIActionBrush)
                    .clickable(enabled = !isProbing) { onProbeAll() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/** 浓墨高亮分段切换条：全部 / 可用 / 异常 / 未测。 */
@Composable
private fun AgentTabRow(
    data: List<UIFriendListData>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val okCount = data.count { it.probeState == UIFriendProbeState.OK }
    val failedCount = data.count { it.probeState == UIFriendProbeState.FAILED }
    val untestedCount = data.count {
        it.probeState == UIFriendProbeState.IDLE || it.probeState == UIFriendProbeState.CHECKING
    }

    data class TabSpec(val label: String, val dotColor: Color? = null)

    val tabs = listOf(
        TabSpec("全部 ${data.size}"),
        TabSpec("可用 $okCount", dotColor = if (okCount > 0) UIAtelierJade else null),
        TabSpec("异常 $failedCount", dotColor = if (failedCount > 0) UIFriendBarRed else null),
        TabSpec("未测 $untestedCount"),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusTab))
            .background(UIAtelierSand)
            .border(1.dp, UIGlassStroke, RoundedCornerShape(UIRadiusTab))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = selectedIndex == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(UIRadiusTab - 3.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(UIAtelierInk)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab.dotColor != null && !isSelected) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(tab.dotColor)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = tab.label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) UIGlassSurfaceStrong else UIFriendBarTabUnselectedText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/** 杂志微标签风格的厂商分组标题：已探活时顺手标出该厂商「X/Y 可用」。 */
@Composable
private fun FriendGroupHeader(name: String, friends: List<UIFriendListData>) {
    val probed = friends.count {
        it.probeState == UIFriendProbeState.OK || it.probeState == UIFriendProbeState.FAILED
    }
    val ok = friends.count { it.probeState == UIFriendProbeState.OK }
    val failed = friends.count { it.probeState == UIFriendProbeState.FAILED }
    val dotColor = when {
        probed == 0 -> UIFriendBarTagTextBlue
        failed == 0 -> UIAtelierJade
        ok == 0 -> UIFriendBarRed
        else -> UIAtelierBrass
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color = UIFriendBarItemSubtitleColor,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (probed > 0) "$ok/${friends.size} 可用" else "${friends.size}",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (probed > 0 && failed > 0 && ok == 0) UIFriendBarRed else UIFriendBarItemMetaColor,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

private val NAME_MAX_WIDTH = 152.dp

internal fun shortModelTag(model: String): String {
    val trimmed = model.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substringAfterLast('/').ifBlank { trimmed }
}

@Composable
private fun AgentListItem(
    data: UIFriendListData,
    isBatchProbing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProbeClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            UIAvatar(
                name = data.name,
                avatarPath = data.avatarPath,
                size = 46.dp,
                shape = RoundedCornerShape(14.dp),
                fallbackColor = data.avatarBgColor,
                modifier = Modifier.border(1.dp, UIGlassStroke, RoundedCornerShape(14.dp))
            )

            // 头像右下角状态圆点严格反映真实探活结果，未检测时不画假绿点
            val dotColor = when (data.probeState) {
                UIFriendProbeState.OK -> UIFriendBarOnlineGreen
                UIFriendProbeState.FAILED -> UIFriendBarRed
                UIFriendProbeState.CHECKING -> UIAtelierBrass
                UIFriendProbeState.IDLE -> null
            }
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.BottomEnd)
                        .background(dotColor, CircleShape)
                        .border(2.dp, UIGlassSurfaceStrong, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.name,
                    modifier = Modifier.widthIn(max = NAME_MAX_WIDTH),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarItemTitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )

                val tag = shortModelTag(data.modelTag)
                if (tag.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(7.dp))
                            .background(UIFriendBarTagBgBlue)
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = tag,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTagTextBlue,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }

            if (data.role.isNotBlank()) {
                Text(
                    text = data.role,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = UIFriendBarItemSubtitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }

            Text(
                text = "${data.source} · ${data.usageCount}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    color = UIFriendBarItemMetaColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )

            if (data.probeState == UIFriendProbeState.FAILED && !data.probeError.isNullOrBlank()) {
                Text(
                    text = data.probeError,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = UIFriendBarRed.copy(alpha = 0.90f),
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (onProbeClick != null || data.probeState != UIFriendProbeState.IDLE) {
            FriendProbeBadge(
                state = data.probeState,
                latency = data.probeLatency,
                enabled = onProbeClick != null && !isBatchProbing && data.probeState != UIFriendProbeState.CHECKING,
                onClick = { onProbeClick?.invoke() }
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = UIFriendBarItemMetaColor.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 好友行右侧的可点击探活徽章：未测可单点测、已连通显示耗时、失败可单点重试。 */
@Composable
private fun FriendProbeBadge(
    state: UIFriendProbeState,
    latency: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val bg: Color
    val borderColor: Color
    val fg: Color
    val label: String

    when (state) {
        UIFriendProbeState.OK -> {
            bg = UIAtelierJadeBg
            borderColor = UIAtelierJade.copy(alpha = 0.25f)
            fg = UIAtelierJade
            label = latency ?: "可用"
        }

        UIFriendProbeState.CHECKING -> {
            bg = UIAtelierBrassBg
            borderColor = UIAtelierBrass.copy(alpha = 0.30f)
            fg = UIAtelierBrass
            label = "检测中"
        }

        UIFriendProbeState.FAILED -> {
            bg = UIFriendBarRed.copy(alpha = 0.10f)
            borderColor = UIFriendBarRed.copy(alpha = 0.28f)
            fg = UIFriendBarRed
            label = "不可用"
        }

        UIFriendProbeState.IDLE -> {
            bg = UIAtelierSand
            borderColor = UIGlassStroke
            fg = UIFriendBarItemMetaColor
            label = "探活"
        }
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(0.8.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (state) {
            UIFriendProbeState.CHECKING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.4.dp,
                    color = fg,
                )
            }

            UIFriendProbeState.OK, UIFriendProbeState.FAILED -> {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(fg)
                )
            }

            UIFriendProbeState.IDLE -> Unit
        }
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

@Preview
@Composable
private fun UIFriendCrewListPreview() {
    val agentList = remember {
        listOf(
            UIFriendListData(
                name = "Echo",
                modelTag = "GPT-4o",
                role = "产品策略顾问",
                source = "OpenAI",
                usageCount = "12 条消息",
                avatarBgColor = Color(0xFFEED9B7),
                avatarTextColor = Color(0xFF4B5563),
                probeState = UIFriendProbeState.OK,
                probeLatency = "520ms",
            ),
            UIFriendListData(
                name = "Mir",
                modelTag = "Qwen-Max",
                role = "创意灵感助手",
                source = "阿里云",
                usageCount = "8 条消息",
                avatarBgColor = Color(0xFFC8E0D4),
                avatarTextColor = Color(0xFF1E40AF),
                probeState = UIFriendProbeState.FAILED,
                probeError = "还没有配置这个厂商的 API Key",
            )
        )
    }

    Box(modifier = Modifier.background(Color(0xFFF4EFE6))) {
        UIFriendCrewList(agentList, onProbeFriend = {}, onProbeAll = {})
    }
}
