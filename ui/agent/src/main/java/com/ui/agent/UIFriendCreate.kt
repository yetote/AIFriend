package com.ui.agent

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarCreateButtonBg
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemMetaColor
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
 * 全部智能体区块头（保留原签名兼容外部调用；首页现已将搜索栏上提，此处可选隐藏搜索框）。
 */
@Composable
fun UIFriendCreate(
    modifier: Modifier = Modifier,
    createdCount: Int = 0,
    searchText: String = "",
    onSearchTextChange: (String) -> Unit = {},
    onCreateClick: () -> Unit = {},
    showSearchBar: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIFriendBarCreateButtonBg)
                    .clickable { onCreateClick() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
                    text = "新建好友",
                    style = TextStyle(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarCreateButtonText,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }

        if (showSearchBar) {
            UIFriendSearchBar(
                value = searchText,
                onValueChange = onSearchTextChange
            )
        }
    }
}

@Preview
@Composable
private fun PUIFriendCreate() {
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.background(Color(0xFFF4EFE6))) {
        UIFriendCreate(
            createdCount = 6,
            searchText = query,
            onSearchTextChange = { query = it },
            onCreateClick = {}
        )
    }
}
