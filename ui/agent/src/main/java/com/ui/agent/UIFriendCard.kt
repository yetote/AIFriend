package com.ui.agent

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.ext.toKString
import com.core.common.theme.zeroPaddingStyle
import com.ui.common.theme.UIAtelierBrass
import com.ui.common.theme.UIAtelierJade
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard

/**
 * 精炼的单行工坊状态脉搏条（Studio Telemetry Ribbon）。
 * 完成探活后第一栏自动切为「探活可用 X/N」，未探活前展示「活跃 / N」。
 */
@Composable
fun UIFriendCrewStatusCard(
    onlineCount: Int = 0,
    title: Int = 0,
    activeCount: Int = 0,
    messageCount: Int = 0,
    mostActive: String = "-",
    probedCount: Int = 0,
    aliveCount: Int = 0,
    failedCount: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .uiCard(
                shape = RoundedCornerShape(UIRadiusControl + 2.dp),
                color = UIGlassSurfaceStrong,
                borderColor = UIGlassStroke,
                elevation = 2.dp
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (probedCount > 0) {
            TelemetryStat(
                dotColor = if (aliveCount > 0) UIAtelierJade else UIFriendBarRed,
                value = "$aliveCount / $title",
                label = if (failedCount > 0) "可用 · $failedCount 异常" else "探活可用",
                modifier = Modifier.weight(1.1f)
            )
        } else {
            TelemetryStat(
                dotColor = UIAtelierJade,
                value = activeCount.toKString(),
                label = "活跃 / ${title.toKString()}",
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(UISeparator)
        )
        TelemetryStat(
            dotColor = Color(0xFFC84B2A),
            value = messageCount.toKString(),
            label = "累计对话",
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(UISeparator)
        )
        TelemetryStat(
            dotColor = UIAtelierBrass,
            value = mostActive,
            label = "最常聊",
            modifier = Modifier.weight(1.05f)
        )
    }
}

@Composable
private fun TelemetryStat(
    dotColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = zeroPaddingStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor
                )
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = zeroPaddingStyle(
                fontSize = 10.sp,
                color = UIFriendBarItemMetaColor
            )
        )
    }
}

@Preview
@Composable
private fun UIFriendCrewStatusCardPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        UIFriendCrewStatusCard(
            onlineCount = 8,
            title = 8,
            activeCount = 5,
            messageCount = 1240,
            mostActive = "小谋",
            probedCount = 8,
            aliveCount = 6,
            failedCount = 2
        )
    }
}
