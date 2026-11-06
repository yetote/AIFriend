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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * iOS 风格弹窗：大圆角、白色毛玻璃卡片、胶囊按钮。
 *
 * 替换 Material 的 AlertDialog，保持和全站一致的圆角与投影。
 */
@Composable
fun UIIosDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissible: Boolean = true,
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
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 24.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = UIFriendBarItemMetaColor
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

/** iOS 风格底部动作面板，用于长按菜单。 */
@Composable
fun UIActionSheet(
    title: String,
    items: List<UIActionSheetItem>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarItemMetaColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (item.destructive) UIFriendBarRed else UIFriendBarTitleColor
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarSubtitleColor
                )
            }
        }
    }
}

/** 弹窗底部按钮行：左灰右彩，等宽。 */@Composable
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
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(UIGlassSurface)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cancelText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarSubtitleColor
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .uiControl(
                    shape = RoundedCornerShape(UIRadiusControl),
                    color = if (confirmEnabled) confirmColor else confirmColor.copy(alpha = 0.35f)
                )
                .clickable(enabled = confirmEnabled) { onConfirm() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = confirmText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
