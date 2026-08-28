package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.UIFriendListData
import com.ui.friend.theme.UIFriendBarDividerColor
import com.ui.friend.theme.UIFriendBarItemMetaColor
import com.ui.friend.theme.UIFriendBarItemSubtitleColor
import com.ui.friend.theme.UIFriendBarItemTitleColor
import com.ui.friend.theme.UIFriendBarListCardBg
import com.ui.friend.theme.UIFriendBarOnlineGreen
import com.ui.friend.theme.UIFriendBarTabSelectedBg
import com.ui.friend.theme.UIFriendBarTabSelectedText
import com.ui.friend.theme.UIFriendBarTabUnselectedBg
import com.ui.friend.theme.UIFriendBarTabUnselectedText
import com.ui.friend.theme.UIFriendBarTagBgBlue
import com.ui.friend.theme.UIFriendBarTagTextBlue




@Composable
fun UIFriendCrewList(data: List<UIFriendListData>) {

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- 1. 顶部 Tab 切换组件 ---
        AgentTabRow(
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )

        // --- 2. 智能体列表卡片容器 ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = UIFriendBarListCardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                data.forEachIndexed { index, agent ->
                    AgentListItem(data = agent)
                    // 如果不是最后一项，添加分割线
                    if (index < data.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = UIFriendBarDividerColor,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

// 顶部 Tab 栏
@Composable
private fun AgentTabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("全部 6", "启用中 5", "草稿 1")

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) UIFriendBarTabSelectedBg else UIFriendBarTabUnselectedBg)
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) UIFriendBarTabSelectedText else UIFriendBarTabUnselectedText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
    }
}

// 单个智能体列表项组件
@Composable
private fun AgentListItem(
    data: UIFriendListData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { /* 点击列表项事件 */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 左侧带有在线绿点的圆角头像
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // 头像主体
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(data.avatarBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.name.first().toString(),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = data.avatarTextColor
                    )
                )
            }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = data.name,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarItemTitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )

                // 模型 Tag (如 GPT-4o)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(UIFriendBarTagBgBlue)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = data.modelTag,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTagTextBlue,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
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
private fun UIFriendCrewListPreview() {
    // 假数据列表
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