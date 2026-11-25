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
import com.ui.agent.data.groupByVendor
import com.ui.common.UIAvatar
import com.ui.common.theme.UIAtelierInk
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarItemTitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarTabUnselectedText
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusTab
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard

/**
 * 智能体名录：分段墨色筛选条 + 按服务商独立分卡展示，告别臃肿的单一大白墙。
 */
@Composable
fun UIFriendCrewList(
    data: List<UIFriendListData>,
    onItemClick: (UIFriendListData) -> Unit = {},
    onItemLongClick: (UIFriendListData) -> Unit = {}
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
            1 -> data.filter { it.isOnline }
            2 -> data.filter { !it.isOnline }
            else -> data
        }
        val groups = visible.groupByVendor()

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前筛选下暂无智能体",
                    fontSize = 13.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
        } else {
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FriendGroupHeader(name = group.name, count = group.friends.size)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
                    ) {
                        group.friends.forEachIndexed { index, agent ->
                            AgentListItem(
                                data = agent,
                                onClick = { onItemClick(agent) },
                                onLongClick = { onItemLongClick(agent) }
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

/** 浓墨高亮分段切换条：暖砂底轨 + 浓墨黑棕当前胶囊。 */
@Composable
private fun AgentTabRow(
    data: List<UIFriendListData>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        "全部 ${data.size}",
        "启用中 ${data.count { it.isOnline }}",
        "草稿 ${data.count { !it.isOnline }}"
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
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            Box(
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
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) UIGlassSurfaceStrong else UIFriendBarTabUnselectedText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/** 杂志微标签风格的厂商分组标题：置于卡片外部，轻盈透气。 */
@Composable
private fun FriendGroupHeader(name: String, count: Int) {
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
                .background(UIFriendBarTagTextBlue)
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
            text = "$count",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarItemMetaColor,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

private val NAME_MAX_WIDTH = 160.dp

internal fun shortModelTag(model: String): String {
    val trimmed = model.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substringAfterLast('/').ifBlank { trimmed }
}

@Composable
private fun AgentListItem(
    data: UIFriendListData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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

            if (data.isOnline) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.BottomEnd)
                        .background(UIFriendBarOnlineGreen, CircleShape)
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
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = UIFriendBarItemMetaColor.copy(alpha = 0.65f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Preview
@Composable
private fun UIFriendCrewListPreview() {
    val agentList = remember {
        listOf(
            UIFriendListData(
                "Echo",
                "GPT-4o",
                "产品策略顾问",
                "OpenAI",
                "本月 3,841 次",
                Color(0xFFEED9B7),
                Color(0xFF4B5563)
            ),
            UIFriendListData(
                "Mir",
                "Qwen-Max",
                "创意灵感助手",
                "阿里云",
                "本月 2,970 次",
                Color(0xFFC8E0D4),
                Color(0xFF1E40AF)
            )
        )
    }

    Box(modifier = Modifier.background(Color(0xFFF4EFE6))) {
        UIFriendCrewList(agentList)
    }
}
