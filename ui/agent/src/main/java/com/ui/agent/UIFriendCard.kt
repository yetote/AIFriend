package com.ui.agent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.ext.toKString
import com.core.common.theme.zeroPaddingStyle
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIHeroBrush
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.UIFriendBarDecorativeWaveColor
import com.ui.common.theme.UIFriendBarHeaderTagGreenColor

@Composable
fun UIFriendCrewStatusCard(
    onlineCount: Int = 0,
    title: Int = 0,
    activeCount: Int = 0,
    messageCount: Int = 0,
    mostActive: String = "-"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(194.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(UIRadiusSheet),
                clip = false,
                ambientColor = Color(0x331E294E),
                spotColor = Color(0x331E294E)
            )
            .clip(RoundedCornerShape(UIRadiusSheet))
            .background(UIHeroBrush)
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(UIRadiusSheet))
    ) {
        // --- 1. 星辉极光径向光晕（替代旧版暗色波浪，通透明亮） ---
        CelestialAuroraGlow(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 28.dp, y = 26.dp)
        )

        // --- 2. 顶部微光反光带 ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        // --- 3. 主卡片内容区 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 上半部分：标题和右上角在线胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(UIFriendBarOnlineGreen, CircleShape)
                        )
                        Text(
                            text = "YOUR AI CREW",
                            style = zeroPaddingStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = UIFriendBarHeaderTagGreenColor,
                                letterSpacing = 1.4.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$title 位智能体，正在为你协同\n工作",
                        style = zeroPaddingStyle(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 26.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
                OnlineBadge(onlineCount = onlineCount.toKString())
            }

            // 下半部分：半透明玻璃托盘承载 3 列统计数据
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(value = activeCount.toKString(), label = "活跃好友", modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(0.6.dp)
                        .height(26.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )
                StatItem(value = messageCount.toKString(), label = "消息总数", modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(0.6.dp)
                        .height(26.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )
                StatItem(value = mostActive, label = "最活跃", modifier = Modifier.weight(1.1f))
            }
        }
    }
}

// 统计项子组件
@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = zeroPaddingStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            maxLines = 1,
            style = zeroPaddingStyle(
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.64f)
            )
        )
    }
}

// 右上角在线人数标签
@Composable
private fun OnlineBadge(onlineCount: String) {
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.11f))
            .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = onlineCount,
                style = zeroPaddingStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "在线",
                style = zeroPaddingStyle(
                    fontSize = 10.sp,
                    color = UIFriendBarHeaderTagGreenColor
                )
            )
        }
    }
}

// 极光星晕装饰
@Composable
private fun CelestialAuroraGlow(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(180.dp)) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0x406366F1),
                            UIFriendBarDecorativeWaveColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(110.dp)
                .border(0.8.dp, Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(164.dp)
                .border(0.6.dp, Color.White.copy(alpha = 0.06f), CircleShape)
        )
    }
}

@Preview
@Composable
private fun UIFriendCrewStatusCardPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        UIFriendCrewStatusCard()
    }
}