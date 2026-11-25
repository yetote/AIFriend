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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.theme.AIFriendTheme
import com.ui.common.UIAvatar
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitle2Color
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.uiControl

/**
 * 首页刊头顶栏：左侧朱砂印记徽标 + 杂志感品牌字标，右侧方圆触感设置键与个人头像。
 */
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
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(UIActionBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "友",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFFDF9)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                text = "AIFriend",
                color = UIFriendBarTitleColor
            )
            Spacer(Modifier.height(1.dp))
            Text(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                text = "ATELIER · 智能体工坊",
                color = UIFriendBarTitle2Color
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(badge = {
                if (isNotification) {
                    Badge(
                        containerColor = UIFriendBarRed,
                        modifier = Modifier
                            .size(8.dp)
                            .offset(0.dp, (-4).dp)
                    )
                }
            }) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .uiControl(shape = RoundedCornerShape(12.dp), color = UIGlassSurfaceStrong)
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "模型与密钥设置",
                        tint = UIFriendBarTitleColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, UIGlassStroke, RoundedCornerShape(12.dp))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                UIAvatar(
                    name = "我",
                    avatarPath = userAvatarPath,
                    size = 36.dp,
                    shape = RoundedCornerShape(11.dp)
                )
            }
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
