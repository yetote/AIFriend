package com.ui.friend

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.theme.AIFriendTheme
import com.ui.friend.theme.UIFriendBarGray
import com.ui.friend.theme.UIFriendBarRed
import com.ui.friend.theme.UIFriendBarTitle2Color
import com.ui.friend.theme.UIFriendBarTitleColor

@Composable
fun UIFriendBar(isNotification: Boolean) {
    Row(
        Modifier
            .background(Color.White)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column() {
            Text(
                fontSize = 26.sp,
                text = "AI智能体",
                color = UIFriendBarTitleColor
            )
            Text(
                fontSize = 14.sp,
                text = "MY AGENT",
                color = UIFriendBarTitle2Color
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BadgedBox(badge = {
                if (isNotification) {
                    Badge(
                        containerColor = UIFriendBarRed,
                        modifier = Modifier
                            .size(8.dp)
                            .offset(0.dp, -5.dp)
                    )
                }
            }) {

                Image(
                    painterResource(R.drawable.ui_friend_notification),
                    modifier = Modifier
                        .size(38.dp)
                        .background(UIFriendBarGray, shape = CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape),
                    contentDescription = "add",
                )
            }
            Image(
                painterResource(R.drawable.ui_friend_add),
                modifier = Modifier
                    .size(38.dp)
                    .background(UIFriendBarGray, shape = CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape),
                contentDescription = "add",
            )
        }
    }
}

@Preview
@Composable
private fun PUIFriendBar() {
    AIFriendTheme {
        UIFriendBar(false)
    }
}