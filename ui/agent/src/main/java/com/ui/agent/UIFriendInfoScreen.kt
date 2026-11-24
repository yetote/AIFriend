package com.ui.agent

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.common.theme.AIFriendTheme
import com.ui.agent.data.UIBalanceState
import com.ui.agent.data.UIBalanceStatus
import com.ui.agent.data.UIBalanceTone
import com.ui.common.UIAvatar
import com.ui.common.UIAvatarCropDialog
import com.ui.common.pageInsets
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarItemTitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 智能体信息页。
 *
 * 聊天页顶栏原来点头像会直接弹出系统相册，跳得太快：
 * 用户往往只是想看看「这是个什么角色、用的什么模型」，
 * 却被硬塞了一个换头像的任务，还得先退出相册才能继续。
 *
 * 现在做成一步缓冲：这里先把角色的完整信息摊开，
 * 想换头像再单独点那一块。头像在这页才是可点的。
 */
@Composable
fun UIFriendInfoScreen(
    friendName: String,
    role: String,
    model: String,
    brand: String,
    company: String,
    prompt: String,
    thinkingEnabled: Boolean,
    messageCount: Int,
    avatarPath: String?,
    /** 服务商余额；null 表示还没开始查。 */
    balance: UIBalanceState? = null,
    onBack: () -> Unit,
    onChangeAvatar: (ByteArray) -> Unit,
    onRefreshBalance: () -> Unit = {},
    onEdit: () -> Unit
) {
    // 相册 → 裁剪 → 回传字节，这套流程从聊天页搬到这里：
    // 换头像是「在这个页面上做什么」，不该由聊天页的顶栏直接拉起。
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) cropUri = uri
    }
    val pickAvatar: () -> Unit = {
        avatarPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    UIGradientBackground(modifier = Modifier.pageInsets()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(
                title = "智能体信息",
                onBack = onBack,
                actions = {
                    Text(
                        text = "编辑",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarCreateButtonText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onEdit() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeaderCard(
                    friendName = friendName,
                    role = role,
                    avatarPath = avatarPath,
                    onChangeAvatar = pickAvatar
                )

                SectionCard(title = "模型") {
                    InfoRow(label = "服务商", value = company.ifBlank { brand })
                    if (brand.isNotBlank() && brand != company) {
                        InfoRow(label = "品牌", value = brand)
                    }
                    InfoRow(label = "模型", value = model, mono = true)
                    InfoRow(
                        label = "深度思考",
                        value = if (thinkingEnabled) "已开启" else "已关闭"
                    )
                }

                /*
                 * 服务商额度。
                 *
                 * 放在详情页而不是设置页，是因为这一页回答的是「这个好友还能不能聊」：
                 * 余额见底了、套餐窗口用光了、方舟上的模型压根没开通——
                 * 这些在设置页的厂商列表里看不出来，在「这个好友用哪家、余额多少」这里
                 * 一眼就知道。
                 */
                SectionCard(title = "服务商额度") {
                    BalanceBlock(state = balance, onRefresh = onRefreshBalance)
                }

                SectionCard(title = "对话") {
                    InfoRow(label = "消息数", value = "$messageCount 条")
                    InfoRow(label = "角色", value = role.ifBlank { "AI 好友" })
                }

                if (prompt.isNotBlank()) {
                    SectionCard(title = "详细设定") {
                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = UIFriendBarItemSubtitleColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    val pendingCrop = cropUri
    if (pendingCrop != null) {
        UIAvatarCropDialog(
            uri = pendingCrop,
            onCancel = { cropUri = null },
            onConfirm = { bytes ->
                cropUri = null
                onChangeAvatar(bytes)
            }
        )
    }
}

/** 顶部：大头像 + 名字 + 角色。头像可点，点这里才进换头像流程。 */
@Composable
private fun HeaderCard(
    friendName: String,
    role: String,
    avatarPath: String?,
    onChangeAvatar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UIAvatar(
                name = friendName,
                avatarPath = avatarPath,
                size = 88.dp,
                shape = CircleShape,
                modifier = Modifier.clickable { onChangeAvatar() }
            )
            // 小相机角标：暗示这块能点，否则纯头像看不出来
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(UIActionBrush)
                    .clickable { onChangeAvatar() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "更换头像",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = friendName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = UIFriendBarTitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (role.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = role,
                fontSize = 13.sp,
                color = UIFriendBarItemMetaColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "点头像换头像",
            fontSize = 11.sp,
            color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarItemMetaColor,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
        content()
    }
}

/**
 * 一行「标签 + 值」。
 *
 * 模型 id 会有 `Pro/deepseek-ai/DeepSeek-V3.1-Terminus` 这种长值，
 * 所以值这一侧用 weight 吃满剩余宽度并允许换行 —— 这里是详情页，
 * 宁可换行也别省略号截断，用户来这页就是要看全的。
 */
@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(UIFieldFill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = UIFriendBarItemMetaColor,
            modifier = Modifier.widthIn(min = 52.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (mono) FontWeight.Medium else FontWeight.Normal,
            color = UIFriendBarItemTitleColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 额度区块。
 *
 * 三种「拿不到数」要分开说，因为处置方式完全不同：
 * - 还没查 → 点一下刷新；
 * - 这家压根没开放这个接口 → 是产品事实，不是出错了；
 * - 查失败 → 可能 Key 过期或者网络不通，该去查凭证。
 *
 * 混成一句「查询失败」，用户会去刷新十次，然后发现根本刷新不出来。
 */
@Composable
private fun BalanceBlock(state: UIBalanceState?, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                state == null || state.status == UIBalanceStatus.Idle ->
                    Text(
                        text = "还没查过这个服务商的额度",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )

                state.status == UIBalanceStatus.Loading ->
                    Text(
                        text = "查询中…",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )

                state.status == UIBalanceStatus.Unsupported ->
                    Text(
                        text = state.message ?: "这家没有公开的额度查询接口",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = UIFriendBarItemMetaColor
                    )

                state.status == UIBalanceStatus.Failed ->
                    Text(
                        text = state.message ?: "查询失败",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = UIFriendBarRed
                    )

                else -> {
                    Text(
                        text = state.headline,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = balanceToneColor(state.tone)
                    )
                    state.details.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    if (state.fetchedAt > 0L) {
                        Text(
                            text = "更新于 ${formatBalanceTime(state.fetchedAt)}",
                            fontSize = 11.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "刷新",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarCreateButtonText,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onRefresh() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 余额主数字的颜色：快见底和见底要一眼看出来。 */
@Composable
private fun balanceToneColor(tone: UIBalanceTone): Color = when (tone) {
    UIBalanceTone.Normal -> UIFriendBarTitleColor
    UIBalanceTone.Low -> Color(0xFFD9822B)
    UIBalanceTone.Empty -> UIFriendBarRed
    UIBalanceTone.Unlimited -> Color(0xFF1E8C7F)
}

private fun formatBalanceTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

@Preview
@Composable
private fun PUIFriendInfoScreen() {
    AIFriendTheme {
        UIFriendInfoScreen(
            friendName = "小咪",
            role = "打工猫娘",
            model = "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
            brand = "硅基流动",
            company = "硅基流动",
            prompt = "你是一只住在办公室的猫娘，说话带点懒散。",
            thinkingEnabled = true,
            messageCount = 4,
            avatarPath = null,
            onBack = {},
            onChangeAvatar = {},
            onEdit = {}
        )
    }
}
