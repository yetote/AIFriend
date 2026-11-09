@file:OptIn(ExperimentalFoundationApi::class)

package com.ui.agent
import com.ui.common.UIAvatar

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.agent.data.UIFriendListData
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarItemTitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarTabSelectedBg
import com.ui.common.theme.UIFriendBarTabSelectedText
import com.ui.common.theme.UIFriendBarTabUnselectedBg
import com.ui.common.theme.UIFriendBarTabUnselectedText
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusTab
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard




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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. 顶部 Tab 切换组件 ---
        AgentTabRow(
            data = data,
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )

        // --- 2. 智能体列表卡片容器 ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusCard)),
        ) {
            val visible = when (selectedTabIndex) {
                1 -> data.filter { it.isOnline }
                2 -> data.filter { !it.isOnline }
                else -> data
            }
            visible.forEachIndexed { index, agent ->
                AgentListItem(
                    data = agent,
                    onClick = { onItemClick(agent) },
                    onLongClick = { onItemLongClick(agent) }
                )
                // 如果不是最后一项，添加分割线
                if (index < visible.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 76.dp, end = 16.dp)
                            .height(0.5.dp)
                            .background(UISeparator)
                    )
                }
            }
        }
    }
}

// 顶部 Tab 栏
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
            .background(UIFriendBarTabUnselectedBg.copy(alpha = 0.6f))
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
                            Modifier
                                .shadow(3.dp, RoundedCornerShape(UIRadiusTab - 3.dp), clip = false)
                                .background(UIGlassSurfaceStrong)
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
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) UIFriendBarTitleColor else UIFriendBarTabUnselectedText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

/**
 * 角色名的宽度上限。
 *
 * 正常名字（`小咪`、`Echo`）远够不到这个值，它只是防止超长名字
 * 把模型标签整个顶掉。名字本身仍然用省略号收尾，不会被压变形。
 */
private val NAME_MAX_WIDTH = 160.dp

/**
 * 把模型 id 压成一段能认得出的短标签。
 *
 * 官方厂商的 id 本来就短（`deepseek-flash`、`glm-4.6`）；
 * 聚合平台的常常是 `Pro/deepseek-ai/DeepSeek-V3.2-Exp` 或
 * `anthropic/claude-sonnet-4.5`，取最后一段才不至于把整行撑满。
 */
internal fun shortModelTag(model: String): String {
    val trimmed = model.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substringAfterLast('/').ifBlank { trimmed }
}

// 单个智能体列表项组件
@Composable
private fun AgentListItem(    data: UIFriendListData,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 左侧带有在线绿点的圆角头像
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.TopStart
        ) {
            UIAvatar(
                name = data.name,
                avatarPath = data.avatarPath,
                size = 44.dp,
                fallbackColor = data.avatarBgColor
            )

            // 右下角在线状态绿点
            if (data.isOnline) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .background(UIFriendBarOnlineGreen, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 2. 右侧文本信息区
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 第一行：名字 + 模型 Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.name,
                    // 名字先测：不加 weight，Row 会先按内容量它，
                    // 再把手里的剩余空间分给后面带 weight 的标签。
                    // 加个上限只是防超长角色名独占整行，正常名字碰不到。
                    modifier = Modifier.widthIn(max = NAME_MAX_WIDTH),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarItemTitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )

                // 模型 Tag：只放最后一段模型名，并吃掉剩下的宽度。
                // 聚合平台的 id 常常是 Pro/deepseek-ai/DeepSeek-V3.2-Exp 这种，
                // 原样摆进来会把角色名压没。
                //
                // weight(fill = false)：空间够就完整显示、不撑满整行；
                // 不够才省略。名字已经量完，所以标签再长也挤不动它。
                val tag = shortModelTag(data.modelTag)
                if (tag.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(50))
                            .background(UIFriendBarTagBgBlue)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tag,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTagTextBlue,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }
                }
            }

            // 第二行：职业描述
            Text(
                text = data.role,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = UIFriendBarItemSubtitleColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )

            // 第三行：来源与使用频次
            Text(
                text = "${data.source}  ·  ${data.usageCount}",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = UIFriendBarItemMetaColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Preview
@Composable
private fun UIFriendCrewListPreview() {    // 假数据列表
    val agentList = remember {
        listOf(
            UIFriendListData(
                "Echo",
                "GPT-4o",
                "产品策略顾问",
                "墨刀",
                "本月 3,841 次",
                Color(0xFFF3F4F6),
                Color(0xFF4B5563)
            ),
            UIFriendListData(
                "Mir",
                "Qwen-Max",
                "创意灵感助手",
                "阿里云",
                "本月 2,970 次",
                Color(0xFFDBEAFE),
                Color(0xFF1E40AF)
            ),
            UIFriendListData(
                "Nova",
                "GPT-4o",
                "用户研究分析师",
                "OpenAI",
                "本月 2,412 次",
                Color(0xFFD1FAE5),
                Color(0xFF065F46)
            ),
            UIFriendListData(
                "Kora",
                "Gemini 1.5",
                "品牌文案专家",
                "Google",
                "本月 1,901 次",
                Color(0xFFECFDF5),
                Color(0xFF047857)
            ),
            UIFriendListData(
                "Atlas",
                "Claude 3.5",
                "项目执行搭档",
                "Anthropic",
                "本月 1,286 次",
                Color(0xFFFEF3C7),
                Color(0xFF92400E)
            ),
            UIFriendListData(
                "Lumen",
                "Doubao",
                "内容校对助手",
                "字节跳动",
                "尚未启用",
                Color(0xFFE5E7EB),
                Color(0xFF374151)
            )
        )
    }

    Box(modifier = Modifier.background(Color(0xFFF7F9FC))) {
        UIFriendCrewList(agentList)
    }
}
