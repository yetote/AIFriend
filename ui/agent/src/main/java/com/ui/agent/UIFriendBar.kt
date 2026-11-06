package com.ui.agent

import com.ui.common.UIAvatar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.theme.AIFriendTheme
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.uiControl
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitle2Color
import com.ui.common.theme.UIFriendBarTitleColor

@Composable
fun UIFriendBar(
    isNotification: Boolean,
    userAvatarPath: String? = null,
    onAvatarClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column() {
            Text(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                text = "AI智能体",
                color = UIFriendBarTitleColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                text = "MY AGENT",
                color = UIFriendBarTitle2Color.copy(alpha = 0.75f)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 这里原本是通知按钮，但一直没有绑定点击，是个死控件。
            // 现在换成设置入口：模型和 Key 是当前唯一需要用户手动配置的东西，
            // 放在首页右上角比新开一个 Tab 更省事，也不占主导航的位置。
            BadgedBox(badge = {
                if (isNotification) {
                    Badge(
                        containerColor = UIFriendBarRed,
                        modifier = Modifier
                            .size(8.dp)
                            .offset(0.dp, -5.dp)
                    )
                }
            }) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .uiControl(shape = CircleShape, color = UIGlassSurfaceStrong)
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "模型与密钥设置",
                        tint = UIFriendBarTitleColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            UIAvatar(
                name = "我",
                avatarPath = userAvatarPath,
                size = 38.dp,
                shape = CircleShape,
                modifier = Modifier.clickable { onAvatarClick() }
            )
        }
    }
}

@Preview
@Composable
private fun PUIFriendBar() {
    AIFriendTheme {
        UIFriendBar(false)
    }
}