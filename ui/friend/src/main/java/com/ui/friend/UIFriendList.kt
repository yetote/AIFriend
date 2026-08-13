package com.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ui.friend.data.UIFriendListData

@Composable
fun UIFriendList(
    aiList: List<String>,
    aiModelList: List<String>,
    friendList: List<UIFriendListData>,
    onBrandListSelected: (String) -> Unit,
    onAddListener: (UIFriendListData) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.LightGray)
                .weight(1f),

            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(friendList) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    UIFriendInfo(it.logo, it.nickname, it.aiBrand, it.model, it.aiData?.company ?: "")
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    UICreateFriend(aiList, aiModelList, onBrandListSelected) { brand, model, nickname ->
                        onAddListener.invoke(
                            UIFriendListData(
                                nickname = nickname,
                                aiBrand = brand,
                                logo = "chatgpt",
                                model = model,
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun UIFriendListP() {
    val list = mutableListOf<UIFriendListData>()
    list.add(
        UIFriendListData(
            nickname = "小深度",
            aiBrand = "DeepSeek-V3",
            logo = "deepseek",
            model = "DeepSeek-V3",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "阿查",
            aiBrand = "ChatGPT",
            logo = "chatgpt",
            model = "GPT-4o",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "克劳德",
            aiBrand = "Claude",
            logo = "claude",
            model = "Claude 3.5 Sonnet",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "双子星",
            aiBrand = "Gemini",
            logo = "gemini",
            model = "Gemini 1.5 Pro",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "通义",
            aiBrand = "通义千问",
            logo = "qwen",
            model = "Qwen-2.5-Max",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "小文",
            aiBrand = "文心一言",
            logo = "ernie",
            model = "ERNIE-4.0",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "小火苗",
            aiBrand = "豆包",
            logo = "doubao",
            model = "Doubao-pro-128k",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "Kimi",
            aiBrand = "Kimi 助手",
            logo = "kimi",
            model = "Moonshot-v1",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "智谱小助手",
            aiBrand = "智清言 (智谱清言)",
            logo = "zhipu",
            model = "GLM-4-Plus",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "阶跃",
            aiBrand = "阶跃星辰",
            logo = "stepfun",
            model = "Step-2",
        )
    )
    list.add(
        UIFriendListData(
            nickname = "百小应",
            aiBrand = "百川大模型",
            logo = "baichuan",
            model = "Baichuan 4",
        )
    )
    UIFriendList(mutableListOf(), mutableListOf(), list, {}) {}
}