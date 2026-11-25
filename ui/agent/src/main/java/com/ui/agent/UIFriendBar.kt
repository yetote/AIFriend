package com.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * 首页刊头顶栏：左侧与桌面图标同源的「朱砂双席」微缩徽标 + 杂志感品牌字标，右侧方圆触感设置键与个人头像。
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
        BrandSealMark(modifier = Modifier.size(36.dp))
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

/** 与桌面启动器图标同源的双席对话微缩徽标：朱砂釉面 + 炭墨/暖宣双气泡 + 三枚矿物点。 */
@Composable
private fun BrandSealMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(UIActionBrush),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // 后排炭墨伙伴气泡（右上）
            val backPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = w * 0.42f,
                        top = h * 0.21f,
                        right = w * 0.82f,
                        bottom = h * 0.57f,
                        topLeftCornerRadius = CornerRadius(w * 0.16f),
                        topRightCornerRadius = CornerRadius(w * 0.16f),
                        bottomRightCornerRadius = CornerRadius(w * 0.05f),
                        bottomLeftCornerRadius = CornerRadius(w * 0.16f)
                    )
                )
            }
            drawPath(backPath, color = Color(0xFF1E1815))

            // 切缝外轮廓（用朱砂底色在前后气泡之间刻出呼吸缝）
            val haloPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = w * 0.15f,
                        top = h * 0.34f,
                        right = w * 0.72f,
                        bottom = h * 0.84f,
                        topLeftCornerRadius = CornerRadius(w * 0.21f),
                        topRightCornerRadius = CornerRadius(w * 0.21f),
                        bottomRightCornerRadius = CornerRadius(w * 0.21f),
                        bottomLeftCornerRadius = CornerRadius(w * 0.08f)
                    )
                )
            }
            drawPath(haloPath, color = Color(0xFFC84B2A))

            // 前排暖宣主对话气泡（左下）
            val frontPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = w * 0.19f,
                        top = h * 0.39f,
                        right = w * 0.67f,
                        bottom = h * 0.80f,
                        topLeftCornerRadius = CornerRadius(w * 0.18f),
                        topRightCornerRadius = CornerRadius(w * 0.18f),
                        bottomRightCornerRadius = CornerRadius(w * 0.18f),
                        bottomLeftCornerRadius = CornerRadius(w * 0.06f)
                    )
                )
            }
            drawPath(frontPath, color = Color(0xFFFFFDF9))

            // 三枚矿物节奏点：浓墨 / 朱砂 / 古铜金
            val cy = h * 0.595f
            val r = w * 0.038f
            val cx = w * 0.43f
            val sp = w * 0.105f
            drawCircle(color = Color(0xFF1C1714), radius = r, center = Offset(cx - sp, cy))
            drawCircle(color = Color(0xFFC84B2A), radius = r, center = Offset(cx, cy))
            drawCircle(color = Color(0xFFD49830), radius = r, center = Offset(cx + sp, cy))
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
