package com.ui.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 宣纸编辑风格弹窗：暖象牙实底卡片 + 1dp 暖石细框 + 高对比操作按钮。
 */
@Composable
fun UIIosDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissible: Boolean = true,
    maxWidth: Dp = 344.dp,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 18.dp)
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = UIFriendBarTitleColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = UIFriendBarSubtitleColor
                    )
                }
            }
            content()
            actions()
        }
    }
}

/** 弹窗里的一个动作项。 */
data class UIActionSheetItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/** 宣纸风格动作菜单，用于长按快捷操作。 */
@Composable
fun UIActionSheet(
    title: String,
    items: List<UIActionSheetItem>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 336.dp)
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UIAtelierSand.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarSubtitleColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.75.dp)
                    .background(UISeparator)
            )
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            item.onClick()
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.destructive) UIFriendBarRed else UIFriendBarTitleColor
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.75.dp)
                        .background(UISeparator)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarItemMetaColor
                )
            }
        }
    }
}

/** 弹窗底部按钮行：左暖砂取消、右朱砂或语义实心确认，等宽利落。 */
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
