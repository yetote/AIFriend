package com.ui.friend

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIFriendCreateDialog(
    // 1. 外部传入的 Model / 网络数据源列表
    aiBrandList: List<String>,
    aiModelList: List<String>,
    onSubmit: (Int, String, String) -> Unit,
) {
    // 菜单展开/收起的纯 UI 状态
    var isTypeExpanded by remember { mutableStateOf(false) }
    var isNameExpanded by remember { mutableStateOf(false) }

    var aiBrandIndex by remember { mutableStateOf(0) }
    var aiBrandModel by remember { mutableStateOf("") }
    var aiBrandNickname by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .wrapContentHeight()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部图片
            Image(
                painter = painterResource(R.drawable.ui_friend_create),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            // 下拉框 1：选择 AI 类型
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "请选择要创建的AI",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = isTypeExpanded,
                    onExpandedChange = { isTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = aiBrandList[aiBrandIndex],
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isTypeExpanded) },
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isTypeExpanded,
                        onDismissRequest = { isTypeExpanded = false }
                    ) {
                        aiBrandList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    aiBrandIndex = aiBrandList.indexOf(item)
                                    isTypeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 下拉框 2：选择 AI 名称预设
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "模型",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = isNameExpanded,
                    onExpandedChange = { isNameExpanded = it }
                ) {
                    OutlinedTextField(
                        value = aiBrandModel,
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("请选择预设名称") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNameExpanded) },
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isNameExpanded,
                        onDismissRequest = { isNameExpanded = false }
                    ) {
                        aiModelList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    aiBrandModel = item
                                    isNameExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 昵称输入框
            OutlinedTextField(
                value = aiBrandNickname,
                onValueChange = { aiBrandNickname = it },
                placeholder = { Text("请给你的朋友起一个好听的昵称", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 确认按钮
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    onSubmit.invoke(aiBrandIndex, aiBrandModel, aiBrandNickname)
                }
            ) {
                Text("确认创建")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UIFriendCreateDialogP() {
    var selectedType by remember { mutableStateOf("Deepseek") }
    var selectedPresetName by remember { mutableStateOf("预设名称 A") }
    var nickName by remember { mutableStateOf("") }

    UIFriendCreateDialog(
        aiBrandList = listOf("Deepseek", "ChatGPT", "Claude"),
        aiModelList = listOf("预设名称 A", "预设名称 B", "预设名称 C"),
        onSubmit = { _, _, _ ->
        }
    )
}
