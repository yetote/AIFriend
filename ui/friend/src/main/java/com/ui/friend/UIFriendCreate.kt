package com.ui.friend

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun UICreateFriend() {
    var selectedType by remember { mutableStateOf("Deepseek") }
    var selectedPresetName by remember { mutableStateOf("预设名称 A") }
    var nickName by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(100.dp)
                .aspectRatio(1f),
        ) {
            Image(
                painterResource(R.drawable.ui_friend_add), contentScale = ContentScale.Fit, modifier = Modifier
                    .fillMaxSize()
                    .clickable(true) {
                        showDialog = true
                    }, alignment = Alignment.Center, contentDescription = "添加朋友"
            )
        }
    }
    if (showDialog) {
        UIFriendCreateDialogWrapper(
            onDismissRequest = { showDialog = false },
            aiTypeList = listOf("Deepseek", "ChatGPT", "Claude"),
            presetNameList = listOf("预设名称 A", "预设名称 B", "预设名称 C"),
            selectedType = selectedType,
            selectedPresetName = selectedPresetName,
            nickName = nickName,
            onTypeSelected = { selectedType = it },
            onPresetNameSelected = { selectedPresetName = it },
            onNickNameChange = { nickName = it },
            onSubmit = {}
        )
    }
}


@Preview
@Composable
private fun UICreateFriendP() {
    UICreateFriend()
}