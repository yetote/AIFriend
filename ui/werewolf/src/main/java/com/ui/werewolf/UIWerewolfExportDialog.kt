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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIDialogDismissButton
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusControl

/**
 * 「导出什么」的选择框：统一采用全站 [UIIosDialog] 与 [UIDialogDismissButton]。
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
            UIDialogDismissButton(text = "取消", onClick = onDismiss)
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
            .background(UIAtelierSand)
            .border(0.9.dp, UIGlassStroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = badge,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(UIFriendBarTagBgBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTagTextBlue,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = UIFriendBarTitleColor)
            Text(text = subtitle, fontSize = 11.5.sp, lineHeight = 16.sp, color = UIFriendBarItemSubtitleColor)
        }
    }
}
