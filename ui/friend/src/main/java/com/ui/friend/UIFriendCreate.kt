package com.ui.friend

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.theme.UIFriendBarCreateButtonBg
import com.ui.friend.theme.UIFriendBarCreateButtonText
import com.ui.friend.theme.UIFriendBarSearchInputBg
import com.ui.friend.theme.UIFriendBarSubtitleColor
import com.ui.friend.theme.UIFriendBarTitleColor2


@Composable
fun UIFriendCreate(
    modifier: Modifier = Modifier,
    createdCount: Int = 6,
    searchText: String = "",
    onSearchTextChange: (String) -> Unit = {},
    onCreateClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // 使用 spacedBy 设置上下两块的间距
    ) {
        // --- 上半部分：标题区与创建按钮 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题 + 副标题
            Column {
                Text(
                    text = "全部智能体",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarSubtitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$createdCount 个已创建",
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = UIFriendBarSubtitleColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both
                        )
                    )
                )
            }

            // 右侧：“+ 创建”按钮
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(UIFriendBarCreateButtonBg)
                    .clickable { onCreateClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "创建",
                    tint = UIFriendBarCreateButtonText,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "创建",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarCreateButtonText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }

        // --- 下半部分：搜索输入框 ---
        CustomSearchBar(
            value = searchText,
            onValueChange = onSearchTextChange,
            placeholder = "搜索昵称、角色或所属公司"
        )
    }
}

// 自定义圆角搜索输入框组件
@Composable
private fun CustomSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(UIFriendBarSearchInputBg, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = UIFriendBarSubtitleColor,
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview
@Composable
private fun PUIFriendCreate() {
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.background(Color(0xFFF7F9FC))) {
        UIFriendCreate(
            searchText = query,
            onSearchTextChange = { query = it },
            onCreateClick = { /* 点击创建 */ }
        )
    }
}