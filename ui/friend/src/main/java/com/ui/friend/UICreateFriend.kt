package com.ui.friend

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun UICreateFriend(addListener: () -> Unit) {
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
                        addListener.invoke()
                    }, alignment = Alignment.Center, contentDescription = "添加朋友"
            )
        }
    }
}

@Preview
@Composable
private fun UICreateFriendP() {
    UICreateFriend {

    }
}