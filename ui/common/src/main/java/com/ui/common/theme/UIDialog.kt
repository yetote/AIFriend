package com.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全站统一宣纸弹窗骨架（标准确认 / 轻量选择 / 表单说明）。
 *
 * 视觉语言：暖象牙实底（#FFFDF9）+ 1dp 暖石细框 + 24dp 圆角 + 左侧朱砂竖线标题头 + 12dp 方圆按钮。
 * 统一开启 `usePlatformDefaultWidth = false` + 水平 20dp 留白，彻底消除不同机型与面板间宽度割裂。
 */
@Composable
fun UIIosDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissible: Boolean = true,
    maxWidth: Dp = 368.dp,
    accentColor: Color = UIFriendBarTagTextBlue,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 16.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AtelierDialogHeader(
                title = title,
                subtitle = subtitle,
                accentColor = accentColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(UISeparator)
            )
            content()
            actions()
        }
    }
}

/**
 * 全站统一宣纸选择面板（用于人格模板、音色挑选、批量创建等较长内容的可滚动大弹窗）。
 *
 * 与 [UIIosDialog] 共用完全一致的卡片边界、朱砂竖线标题头、右上角方圆关闭按钮与细分割线。
 */
@Composable
fun UISheetDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissLabel: String = "取消",
    maxWidth: Dp = 400.dp,
    maxHeight: Dp = 620.dp,
    accentColor: Color = UIFriendBarTagTextBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 18.dp)
                .padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AtelierDialogHeader(
                        title = title,
                        subtitle = subtitle,
                        accentColor = accentColor
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(UIAtelierSand)
                        .border(0.8.dp, UIGlassStroke, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dismissLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarSubtitleColor
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(0.75.dp)
                    .background(UISeparator)
            )
            content()
        }
    }
}

/** 统一弹窗标题头：左侧 3.5dp 朱砂竖线锚点 + 浓墨粗标题 + 焦茶副标题。 */
@Composable
fun AtelierDialogHeader(
    title: String,
    subtitle: String? = null,
    accentColor: Color = UIFriendBarTagTextBlue,
    titleColor: Color = UIFriendBarTitleColor,
    subtitleColor: Color = UIFriendBarSubtitleColor
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.5.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = title,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                modifier = Modifier.padding(start = 12.5.dp),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = subtitleColor
            )
        }
    }
}

/** 弹窗里的一个动作项。 */
data class UIActionSheetItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 统一宣纸动作菜单（长按好友 / 聊天室等快捷操作）：
 * 与 [UIIosDialog] 保持同一套卡片语言、朱砂标题头与 12dp 方圆触感按钮，告别旧版 iOS 贴边白条。
 */
@Composable
fun UIActionSheet(
    title: String,
    items: List<UIActionSheetItem>,
    onDismiss: () -> Unit
) {
    UIIosDialog(
        title = title,
        subtitle = "选择要执行的操作",
        onDismiss = onDismiss,
        maxWidth = 344.dp,
        actions = {
            UIDialogDismissButton(text = "取消", onClick = onDismiss)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                val shape = RoundedCornerShape(UIRadiusControl)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(shape)
                        .background(
                            if (item.destructive) UIFriendBarRed.copy(alpha = 0.09f)
                            else UIAtelierSand
                        )
                        .border(
                            0.9.dp,
                            if (item.destructive) UIFriendBarRed.copy(alpha = 0.28f) else UIGlassStroke,
                            shape
                        )
                        .clickable {
                            onDismiss()
                            item.onClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.destructive) UIFriendBarRed else UIFriendBarTitleColor
                    )
                }
            }
        }
    }
}

/** 单个中性关闭 / 取消按钮：暖砂底 + 1dp 细框，避免把「取消 / 关闭」画成主行动红按钮。 */
@Composable
fun UIDialogDismissButton(
    text: String = "关闭",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .uiControl(
                shape = RoundedCornerShape(UIRadiusControl),
                color = UIAtelierSand,
                borderColor = UIGlassStroke
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarSubtitleColor
        )
    }
}

/** 弹窗底部按钮行：左暖砂取消、右朱砂或语义实心确认，等宽 12dp 方圆。 */
@Composable
fun UIDialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String? = "取消",
    onCancel: (() -> Unit)? = null,
    confirmColor: Color = UIFriendBarTagTextBlue,
    confirmEnabled: Boolean = true
) {
    Spacer(Modifier.height(2.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cancelText != null && onCancel != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .uiControl(
                        shape = RoundedCornerShape(UIRadiusControl),
                        color = UIAtelierSand,
                        borderColor = UIGlassStroke
                    )
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cancelText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarSubtitleColor
                )
            }
        }
        val usePrimaryBrush = confirmColor == UIFriendBarTagTextBlue
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(UIRadiusControl))
                .then(
                    if (usePrimaryBrush && confirmEnabled) {
                        Modifier.background(UIActionBrush)
                    } else {
                        Modifier.background(
                            if (confirmEnabled) confirmColor else confirmColor.copy(alpha = 0.35f)
                        )
                    }
                )
                .clickable(enabled = confirmEnabled) { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = confirmText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
