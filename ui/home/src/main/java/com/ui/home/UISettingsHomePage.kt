package com.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIAtelierJade
import com.ui.common.theme.UIAtelierJadeBg
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard

/**
 * 设置首页：宣纸建筑风格分组入口。
 */
@Composable
fun UISettingsHomePage(
    state: UIModelSettingsState,
    onBack: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenTts: () -> Unit,
) {
    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageInsetsBottom()
        ) {
            UITopBar(title = "工坊设置", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "服务与凭证",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                val model = state.selectedProvider
                val modelSummary = when {
                    model == null -> "选择服务商，填入 API Key 并挑默认模型"
                    state.selectedModel.isBlank() -> model.name
                    else -> "${model.name} · ${state.selectedModel}"
                }
                SettingsEntry(
                    badge = "模",
                    badgeBg = UIFriendBarTagBgBlue,
                    badgeTint = UIFriendBarTagTextBlue,
                    title = "模型与密钥",
                    subtitle = modelSummary,
                    configured = model?.hasKey == true,
                    onClick = onOpenModel
                )

                val tts = state.tts.selected
                SettingsEntry(
                    badge = "声",
                    badgeBg = UIAtelierJadeBg,
                    badgeTint = UIAtelierJade,
                    title = "语音朗读（TTS）",
                    subtitle = if (tts == null) {
                        "配置云端朗读凭证并试听角色音色"
                    } else {
                        "${tts.name} · 狼人杀自动朗读"
                    },
                    configured = tts?.hasKey == true,
                    onClick = onOpenTts
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(UIAtelierSand.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(UIAtelierJade)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "所有 API Key 与语音凭证仅保存在本机，直接请求所选服务商，不经任何中转服务器。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    badge: String,
    badgeBg: Color,
    badgeTint: Color,
    title: String,
    subtitle: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = badgeTint
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = UIFriendBarTitleColor
                )
                Spacer(Modifier.width(8.dp))
                if (configured) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(UIAtelierJadeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(UIFriendBarOnlineGreen, CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "已配置",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIAtelierJade
                        )
                    }
                } else {
                    Text(
                        text = "待配置",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarItemMetaColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(UIAtelierSand)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = UIFriendBarItemSubtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = UIFriendBarItemMetaColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
