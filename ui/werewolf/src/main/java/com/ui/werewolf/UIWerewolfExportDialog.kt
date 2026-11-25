package com.ui.werewolf

// 导出格式的选择框。复盘页和列表页共用。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusControl

/**
 * 「导出什么」的选择框。
 *
 * 两种导出的读法完全不同：Markdown 是拿给别人读的文档，对局文件只有 App 认得。
 * 挤成一个按钮上写两个名字，用户只能靠猜；分成两个按钮又太占地方。
 *
 * 复盘页和列表页共用这一个：同一个动作在两处长得不一样，用户会以为是两件事。
 * 使用全站统一的 [UIIosDialog] 皮肤，避免 Material 原生对话框割裂感。
 *
 * @param onMarkdown null 表示这一局没有 Markdown 可导（列表里没打完的局就是这种情况：
 *   复盘要基于「谁赢了」，没有赢家就生不出一份不误导人的文档）。
 * @param onGame null 表示不提供对局文件导出。
 */
@Composable
internal fun UIWerewolfExportDialog(
    onDismiss: () -> Unit,
    onMarkdown: (() -> Unit)?,
    onGame: (() -> Unit)?,
) {
    UIIosDialog(
        title = "导出这一局",
        subtitle = "选择导出格式，生成后会调起系统分享面板",
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = "取消",
                confirmColor = Color(0xFF8C8277),
                onConfirm = onDismiss,
                cancelText = null,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onMarkdown != null) {
                ExportOption(
                    badge = "MD",
                    title = "复盘文档 · Markdown",
                    subtitle = "一份能直接读、能贴进笔记软件的复盘",
                    onClick = onMarkdown,
                )
            }
            if (onGame != null) {
                ExportOption(
                    badge = "WWG",
                    title = "对局文件 · .wwg",
                    subtitle = "导回 App 就能重看、重演，换台手机也能带走",
                    onClick = onGame,
                )
            }
        }
    }
}

@Composable
private fun ExportOption(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(UIFieldFill)
            .border(0.7.dp, UIGlassStroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = badge,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(UIFriendBarTagTextBlue.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTagTextBlue,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ExportTitle)
            Text(text = subtitle, fontSize = 11.5.sp, lineHeight = 16.sp, color = ExportDim)
        }
    }
}

// 和复盘页同一套灰阶（那边是 private 常量，两处改一边就会看起来像两个应用）
private val ExportTitle = Color(0xFF171411)
private val ExportDim = Color(0xFF7B7166)
