package com.ui.friend

import android.R.attr.thickness
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.friend.data.getRandomColor

@Composable
fun UIFriendInfo(logo: String, nickname: String, aiName: String, model: String, companyName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .height(120.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center

    ) {
        Card(
            modifier = Modifier
                .height(100.dp)
                .aspectRatio(1f),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = getRandomColor(nickname)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .wrapContentHeight()
                        .align(CenterHorizontally)
                ) {
                    Image(
                        painter = painterResource(R.mipmap.deepseek),
                        contentDescription = null,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp,6.dp,6.dp,0.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Text(nickname, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding( 6.dp,4.dp,6.dp,4.dp))

                HorizontalDivider(thickness = 1.dp, color = Color.LightGray)

                Text(aiName, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp))

                Text(model, fontSize = 4.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 3.dp, horizontal = 10.dp))

                Text(companyName, fontSize = 4.sp, textAlign = TextAlign.Center, color = Color.Gray,modifier = Modifier.padding(horizontal = 15.dp))

            }
        }
    }
}

@Preview
@Composable
private fun FriendInfoP() {
    UIFriendInfo("", "xiaoD", "Deepseek", "Deepseek-V2", "杭州深度求索人工智能基础技术研究有限公司")
}