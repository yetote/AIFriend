package com.ui.friend
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.ext.toKString
import com.core.common.theme.zeroPaddingStyle
import com.ui.friend.theme.UIFriendBarBlue
import com.ui.friend.theme.UIFriendBarDecorativeWaveColor
import com.ui.friend.theme.UIFriendBarDividerLineColor
import com.ui.friend.theme.UIFriendBarHeaderTagGreenColor

@Composable
fun UIFriendCrewStatusCard(
    onlineCount: Int = 15,
    title: Int = 6,
    todayCount: Int = 48,
    monthCount: Int = 12800,
    mostActive: String = "Echo"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = UIFriendBarBlue
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // --- 1. 右下角层叠圆角装饰背景 ---
            DecorativeWaves(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 20.dp)
            )

            // --- 2. 主卡片内容区 ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
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
                                letterSpacing = 0.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 主标题
                        Text(
                            text = "$title 位智能体，正在为你协同\n工作",
                            style = zeroPaddingStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 24.sp
                            )
                        )
                    }

                    // 右上角在线人数 Badge
                    OnlineBadge(onlineCount = onlineCount.toKString())
                }

                // 中间分割线
                HorizontalDivider(
                    thickness = 1.dp,
                    color = UIFriendBarDividerLineColor
                )

                // 下半部分：3列统计数据
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    StatItem(value = todayCount.toKString(), label = "今日对话", modifier = Modifier.weight(1f))
                    StatItem(value = monthCount.toKString(), label = "本月消息", modifier = Modifier.weight(1f))
                    StatItem(value = mostActive, label = "最活跃", modifier = Modifier.weight(1f))
                }
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
            .width(52.dp)
            .height(52.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
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