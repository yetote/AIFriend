package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ui.friend.data.UIFriendListData
import com.ui.friend.theme.UIFriendBarGray

@Composable
fun UIFriendPage(list: List<UIFriendListData>) {
    Column(Modifier.background(color = UIFriendBarGray)) {
        UIFriendBar(true)
        Spacer(Modifier.height(16.dp))
        UIFriendCrewStatusCard()
        Spacer(Modifier.height(16.dp))
        UIFriendCreate()
        Spacer(Modifier.height(16.dp))
        UIFriendCrewList(list)
    }
}

@Preview
@Composable
private fun PUIFriendPage() {
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
    UIFriendPage(agentList)
}