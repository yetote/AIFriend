package com.ui.werewolf

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.werewolf.data.UIWerewolfLive

/**
 * 对局进行中的进度页。
 *
 * 这是牌桌落地前的最小可用版本，职责只有一个：让「开局」之后不至于是一片空白。
 * 8 人局 60–100 次请求，一个夜晚可能几十秒没有输出，必须让人看见「在等第 3 个人说话」，
 * 否则会以为卡死而退出——虽然局面不会丢，但体验上等于失败。
 */
@Composable
fun UIWerewolfLiveScreen(
    live: UIWerewolfLive,
    onExit: () -> Unit,
) {
    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(
                title = if (live.finished) "对局结束" else "第 ${live.day} 天",
                onBack = onExit
            ) {
                Text(
                    text = "狼人杀",
                    modifier = Modifier.padding(end = 12.dp),
                    fontSize = 13.sp,
                    color = UIFriendBarItemSubtitleColor
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhaseCard(live = live)

                if (live.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UIFriendBarRed.copy(alpha = 0.08f), RoundedCornerShape(UIRadiusControl))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${live.error}\n这一局的进度已经存档，下次进来可以接着打。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = UIFriendBarRed
                        )
                    }
                }

                Text(
                    text = "上帝播报",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarItemSubtitleColor
                )

                BroadcastList(broadcasts = live.broadcasts, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.pageInsetsBottom())
        }
    }
}

@Composable
private fun PhaseCard(live: UIWerewolfLive) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (live.finished) (live.winnerLabel ?: "已结束") else live.phaseLabel,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor
            )
            if (live.running) {
                ThinkingDot()
            }
        }

        Text(
            text = when {
                live.finished -> "全部身份已公开，这一局到这里"
                live.progressLabel != null -> live.progressLabel
                else -> "准备开局"
            },
            fontSize = 12.sp,
            color = UIFriendBarItemSubtitleColor
        )

        ProgressTrack(fraction = live.progressFraction, active = live.running)
    }
}

/** 等待模型时的一枚呼吸点。静止的界面在几十秒的等待里会被当成卡死。 */
@Composable
private fun ThinkingDot() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(UIFriendBarCreateButtonText.copy(alpha = alpha), CircleShape)
    )
}

/**
 * 进度条。
 *
 * [fraction] 为 null 时画不确定态（呼吸的整体条），而不是画成 0%——
 * 狼人商议、投票是并发的，本来就不知道「第几个」，硬凑一个数字是假信息。
 */
@Composable
private fun ProgressTrack(fraction: Float?, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "track")
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(UIFriendBarItemSubtitleColor.copy(alpha = 0.12f), RoundedCornerShape(50))
    ) {
        when {
            fraction != null -> Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(UIActionBrush, RoundedCornerShape(50))
            )

            active -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        UIFriendBarCreateButtonText.copy(alpha = breath * 0.5f),
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun BroadcastList(broadcasts: List<String>, modifier: Modifier = Modifier) {
    if (broadcasts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没开始，等第一位开口",
                fontSize = 12.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        return
    }

    val scroll = rememberScrollState()
    // 新播报进来就贴到底部：中途找「刚刚发生了什么」必须看最后一条
    LaunchedEffect(broadcasts.size) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), color = UIGlassSurfaceStrong)
            .verticalScroll(scroll)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        broadcasts.forEach { line ->
            Text(
                text = line,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = UIFriendBarTitleColor.copy(alpha = 0.85f)
            )
        }
    }
}
