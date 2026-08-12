package com.ui.friend

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog

@Composable
fun UIFriendCreateDialogWrapper(
    onDismissRequest: () -> Unit, // 点击外部或返回键关闭
    aiBrandList: List<String>,
    aiModelList: List<String>,
    onSubmit: (Int, String, String) -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // 使用 Card 或 Surface 作为 Dialog 的白色背景卡片底座
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            UIFriendCreateDialog(
                aiBrandList = aiBrandList,
                aiModelList = aiModelList,
                onSubmit = { i, s, s2 ->
                    onSubmit(i, s, s2)
                    onDismissRequest() // 点击提交后自动关闭弹窗
                }
            )
        }
    }
}