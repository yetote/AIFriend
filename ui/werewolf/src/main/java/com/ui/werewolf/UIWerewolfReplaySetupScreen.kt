package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIIosDialog
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMissingSeat

/**
 * 演出前「补人」页。
 *
 * 什么时候会出现：存档里记的是座位号和当时的名字，头像和音色却来自好友列表。
 * 那一局用过的角色被删掉之后，这一格就既没有声音也没有脸——牌桌会缺一个人。
 *
 * 为什么必须**手动**指定：替身的名字和声音会直接出现在牌桌上，自动挑一个塞进去，
 * 用户根本不知道那个说着陌生口音的人是谁。也不能只按音色凑：几个缺失座位都用
 * 同一个替身，听起来就像同一个人在替全桌说话。
 *
 * 候选里**排除这一局已经出现过的角色**：撞名、撞音色会让牌桌分不清谁是谁。
 */
@Composable
fun UIWerewolfReplaySetupScreen(
    missing: List<UIWerewolfMissingSeat>,
    /** 可以当替身的好友：已经排除这一局里出现过的角色。 */
    candidates: List<UIWerewolfFriend>,
    onCancel: () -> Unit,
    /** 座位 → 替身好友 id；null 表示这个座位静音（只显示文字，不出声）。 */
    onConfirm: (Map<Int, String?>) -> Unit,
) {
    // null = 还没选；MUTE = 明确选了静音。两者必须分开：
    // 没选就不该允许开始演出，而「静音」是一个有效的决定。
    var picks by remember(missing) {
        mutableStateOf<Map<Int, String?>>(
            missing.filter { it.muted }.associate { it.seat to null }
        )
    }
    var picking by remember { mutableStateOf<UIWerewolfMissingSeat?>(null) }
    val ready = missing.all { picks.containsKey(it.seat) }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(title = "补齐演出替身", onBack = onCancel)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "这局有几位角色已不在好友列表里",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarTitleColor
                        )
                        Text(
                            text = if (candidates.isEmpty()) {
                                "好友里已经没有可用的替身了（这一局的人把好友占满了），只能让这些座位静音。"
                            } else {
                                "给每个空座位挑一位替身，或设为只显示文字。替身仅影响头像和声音，不改变存档剧情。"
                            },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }
                items(missing, key = { it.seat }) { seat ->
                    MissingSeatCard(
                        seat = seat,
                        pickedName = picks[seat.seat]?.let { id ->
                            candidates.firstOrNull { it.id == id }?.name
                        },
                        muted = picks.containsKey(seat.seat) && picks[seat.seat] == null,
                        onPick = { picking = seat }
                    )
                }
                item {
                    Text(
                        text = "全部静音",
                        modifier = Modifier
                            .clip(RoundedCornerShape(UIRadiusControl))
                            .clickable { picks = missing.associate { it.seat to null } }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTagTextBlue
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .pageInsetsBottom()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(if (ready) UIActionBrush else Brush.linearGradient(
                        listOf(UIFriendBarItemMetaColor.copy(alpha = 0.3f), UIFriendBarItemMetaColor.copy(alpha = 0.3f))
                    ))
                    .clickable(enabled = ready) { onConfirm(picks) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (ready) "开始演出" else "还有 ${missing.count { !picks.containsKey(it.seat) }} 位没选",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }

    picking?.let { seat ->
        SubstitutePicker(
            seat = seat,
            candidates = candidates,
            onDismiss = { picking = null },
            onPick = { friendId ->
                picks = picks + (seat.seat to friendId)
                picking = null
            }
        )
    }
}

@Composable
private fun MissingSeatCard(
    seat: UIWerewolfMissingSeat,
    pickedName: String?,
    muted: Boolean,
    onPick: () -> Unit,
) {
    val label = when {
        pickedName != null -> "替身：$pickedName"
        muted -> "静音（只显示文字）"
        else -> "点这里挑一个替身"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard()
            .clickable { onPick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(UIFriendBarTagBgBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${seat.seat}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = UIFriendBarTagTextBlue)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = seat.name.ifBlank { "${seat.seat} 号" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${seat.seat} 号 · 原来的角色已经不在了",
                fontSize = 11.sp,
                color = UIFriendBarItemMetaColor
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (pickedName != null || muted) UIFriendBarTitleColor else UIFriendBarTagTextBlue,
            maxLines = 1
        )
    }
}

@Composable
private fun SubstitutePicker(
    seat: UIWerewolfMissingSeat,
    candidates: List<UIWerewolfFriend>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    UIIosDialog(
        title = "用谁来演 ${seat.seat} 号",
        subtitle = "候选里不含这一局已经出现过的角色，避免撞脸撞声音",
        onDismiss = onDismiss,
        actions = {
            UIDialogButtons(
                confirmText = "设为静音",
                onConfirm = { onPick(null) },
                cancelText = "取消",
                onCancel = onDismiss
            )
        }
    ) {
        if (candidates.isEmpty()) {
            Text(
                text = "当前没有其他可用好友，可直接将该座位设为静音。",
                fontSize = 13.sp,
                color = UIFriendBarItemMetaColor
            )
        } else {
            LazyColumn(modifier = Modifier.height(260.dp)) {
                items(candidates, key = { it.id }) { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(friend.id) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UIAvatar(
                            name = friend.name,
                            avatarPath = friend.avatarPath,
                            size = 34.dp,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = friend.model,
                                fontSize = 11.sp,
                                color = UIFriendBarItemMetaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(UISeparator)
                    )
                }
            }
        }
    }
}
