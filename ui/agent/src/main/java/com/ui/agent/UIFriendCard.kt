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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.ext.toKString
import com.core.common.theme.zeroPaddingStyle
import com.ui.common.theme.UIBlobBlue
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
            .height(184.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(UIRadiusSheet),
                clip = false,
                ambientColor = UIBlobBlue,
                spotColor = UIBlobBlue
            )
            .clip(RoundedCornerShape(UIRadiusSheet))
            .background(UIHeroBrush)
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(UIRadiusSheet))
    ) {
        // --- 1. 右下角层叠圆角装饰背景 ---
        DecorativeWaves(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 20.dp)
        )

        // --- 2. 顶部高光，模拟玻璃反光 ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
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
            // 上半部分：标题和右上角状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Tag: YOUR AI CREW
                    Text(
                        text = "YOUR AI CREW",
                        style = zeroPaddingStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = UIFriendBarHeaderTagGreenColor,
                            letterSpacing = 0.8.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 主标题
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

                // 右上角在线人数 Badge
                OnlineBadge(onlineCount = onlineCount.toKString())
            }

            // 中间分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.14f))
            )

            // 下半部分：3列统计数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                StatItem(value = activeCount.toKString(), label = "活跃好友", modifier = Modifier.weight(1f))
                StatItem(value = messageCount.toKString(), label = "消息总数", modifier = Modifier.weight(1f))
                StatItem(value = mostActive, label = "最活跃", modifier = Modifier.weight(1f))
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
    Column(modifier = modifier) {
        Text(
            text = value,
            style = zeroPaddingStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = zeroPaddingStyle(
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        )
    }
}

// 右上角在线人数标签
@Composable
private fun OnlineBadge(onlineCount: String) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "在线",
                style = zeroPaddingStyle(
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            )
        }
    }
}

// 右下角层叠拱形/波浪装饰背景
@Composable
private fun DecorativeWaves(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(160.dp, 120.dp)) {
        Box(
            modifier = Modifier
                .size(160.dp, 120.dp)
                .border(18.dp, UIFriendBarDecorativeWaveColor, RoundedCornerShape(topStart = 80.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(120.dp, 90.dp)
                .border(18.dp, UIFriendBarDecorativeWaveColor.copy(alpha = 0.7f), RoundedCornerShape(topStart = 60.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(80.dp, 60.dp)
                .border(18.dp, UIFriendBarDecorativeWaveColor.copy(alpha = 0.4f), RoundedCornerShape(topStart = 40.dp))
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