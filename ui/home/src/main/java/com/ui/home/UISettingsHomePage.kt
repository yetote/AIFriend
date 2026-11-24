package com.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * 设置首页：只放功能入口，具体配置进二级页。
 *
 * 原先模型 Key 和 TTS 两套凭证堆在同一个滚动页里，服务商一多（模型有十家）
 * 就要滚很久才能摸到语音设置，用户还容易以为两把 Key 通用。
 * 这里按功能拆成两张卡片，各自进各自的页面。
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
            UITopBar(title = "设置", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "按功能分别配置，凭证只保存在本机。",
                    fontSize = 12.sp,
                    color = UIFriendBarItemMetaColor
                )
                Spacer(Modifier.height(14.dp))

                val model = state.selectedProvider
                val modelSummary = when {
                    model == null -> "选择服务商，填入 API Key 并挑模型"
                    state.selectedModel.isBlank() -> model.name
                    else -> "${model.name} · ${state.selectedModel}"
                }
                SettingsEntry(
                    badge = "模",
                    title = "模型与密钥",
                    subtitle = modelSummary,
                    configured = model?.hasKey == true,
                    onClick = onOpenModel
                )

                Spacer(Modifier.height(10.dp))

                val tts = state.tts.selected
                SettingsEntry(
                    badge = "声",
                    title = "语音朗读（TTS）",
                    subtitle = if (tts == null) {
                        "配置云端朗读的凭证与试听音色"
                    } else {
                        "${tts.name} · 狼人杀自动朗读"
                    },
                    configured = tts?.hasKey == true,
                    onClick = onOpenTts
                )

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    badge: String,
    title: String,
    subtitle: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(UIFriendBarTagBgBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTagTextBlue
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
                if (configured) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x1610B981))
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
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF047857)
                        )
                    }
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
