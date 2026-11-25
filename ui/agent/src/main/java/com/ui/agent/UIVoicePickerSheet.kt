package com.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.core.ai.model.VoiceCatalog
import com.core.ai.model.VoiceGroup
import com.core.ai.model.VoiceOption
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.UISeparator
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl

/**
 * 音色选择面板：搜索 + 分类 + 逐条试听。
 *
 * 为什么不是一排横向 chip：腾讯云一家就 21 个音色，阿里 19 个，横着划根本找不着。
 * 更重要的是**试听**——音色能不能出声取决于账号权限（腾讯精品音色、火山渠道音色
 * 都要在控制台单独开通），名字再好听也可能合成失败。在选的时候当场响一声，
 * 用户才知道这个音色在他这台机器上是可用的；等配到角色里、开一局狼人杀才发现
 * 没声音，就完全看不出问题出在音色上。
 *
 * 分类优先于搜索：用户最先想的通常是「来个女声」，不会先知道音色叫什么。
 */
@Composable
fun UIVoicePickerSheet(
    /** 当前角色选的 TTS 服务；音色 id 各家不通用，列表必须按服务过滤。 */
    providerId: String?,
    currentVoiceId: String?,
    /** 试听请求在飞。 */
    isPreviewing: Boolean,
    /** 正在试听的那个音色；null 表示听的是「默认音色」那条。 */
    previewingVoiceId: String?,
    /** 上一次试听失败的原因，成功或换音色后由调用方清空。 */
    previewError: String?,
    onDismiss: () -> Unit,
    /** 选中音色；null = 跟随该服务的默认音色。 */
    onPick: (String?) -> Unit,
    onPreview: (String?) -> Unit,
) {
    var query by rememberSaveable(providerId) { mutableStateOf("") }
    var groupName by rememberSaveable(providerId) { mutableStateOf<String?>(null) }
    var customOpen by rememberSaveable(providerId) { mutableStateOf(false) }
    var customText by rememberSaveable(providerId) { mutableStateOf("") }

    val groups = remember(providerId) { VoiceCatalog.grouped(providerId) }
    val selectedGroup = groupName?.let { name -> VoiceGroup.entries.firstOrNull { it.name == name } }
    // 搜到东西时分类就不该再拦着：用户已经说出名字了，还要再点一次「全部」很烦。
    val effectiveGroup = if (query.isBlank()) selectedGroup else null
    val options = remember(providerId, effectiveGroup, query) {
        VoiceCatalog.search(providerId, query)
            .filter { it.id != null && (effectiveGroup == null || it.group == effectiveGroup) }
    }
    // 「默认音色」只在没搜索、没筛分类时露出：它不是一个具体音色，
    // 混在搜索结果里会挤掉用户真正在找的那条。
    val showDefault = query.isBlank() && effectiveGroup == null
    val total = remember(providerId) { VoiceCatalog.forProvider(providerId).count { it.id != null } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 26.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择音色",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "点右侧试听，未开通的音色会当场报错",
                        fontSize = 12.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
                Text(
                    text = "取消",
                    modifier = Modifier.clickable { onDismiss() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = UIFriendBarTagTextBlue
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .uiControl(
                        shape = RoundedCornerShape(UIRadiusControl),
                        color = Color(0xFFEFEAE0)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = UIFriendBarItemMetaColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜名字或音色 ID",
                                fontSize = 14.sp,
                                color = UIFriendBarItemMetaColor.copy(alpha = 0.75f)
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = UIFriendBarTitleColor),
                            cursorBrush = SolidColor(UIFriendBarTagTextBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GroupChip(
                        label = "全部 $total",
                        selected = selectedGroup == null && query.isBlank(),
                        onClick = { groupName = null }
                    )
                }
                items(groups) { (group, items) ->
                    GroupChip(
                        label = "${group.label} ${items.size}",
                        selected = selectedGroup == group && query.isBlank(),
                        onClick = { groupName = if (groupName == group.name) null else group.name }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(UISeparator)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (showDefault) {
                    item(key = "default") {
                        VoiceRow(
                            label = VoiceCatalog.defaultOption(providerId).label,
                            id = "跟随该服务的默认音色",
                            selected = currentVoiceId.isNullOrBlank(),
                            playing = isPreviewing && previewingVoiceId == null,
                            onClick = { onPick(null) },
                            onPreview = { onPreview(null) }
                        )
                    }
                }
                items(options, key = { it.id ?: "default" }) { option ->
                    VoiceRow(
                        label = option.label,
                        id = option.id.orEmpty(),
                        selected = option.id != null && option.id == currentVoiceId,
                        playing = isPreviewing && option.id == previewingVoiceId,
                        onClick = { onPick(option.id) },
                        onPreview = { onPreview(option.id) }
                    )
                }
                if (options.isEmpty() && !showDefault) {
                    item(key = "empty") {
                        Text(
                            text = "没有匹配的音色。官方音色表里有的都可以直接填 ID —— 点下面的「自定义音色 ID」",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }
                item(key = "custom") {
                    CustomVoiceRow(
                        providerId = providerId,
                        currentVoiceId = currentVoiceId,
                        open = customOpen,
                        text = customText,
                        onOpenChange = { open ->
                            customOpen = open
                            if (open && customText.isBlank() && !currentVoiceId.isNullOrBlank()) {
                                // 把当前值带过来，改一个字符比重打一遍强
                                customText = currentVoiceId
                            }
                        },
                        onTextChange = { customText = it },
                        onConfirm = { onPick(customText.trim().ifBlank { null }) },
                        onPreview = { onPreview(customText.trim().ifBlank { null }) },
                        isPreviewing = isPreviewing,
                        previewingVoiceId = previewingVoiceId
                    )
                }
            }

            if (previewError != null) {
                Text(
                    text = previewError,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFD14343)
                )
            }
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .uiControl(
                shape = RoundedCornerShape(50),
                color = if (selected) UIFriendBarTagBgBlue else Color.White.copy(alpha = 0.92f)
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor
        )
    }
}

/**
 * 一行音色。
 *
 * ID 常驻显示而不是收进折叠里：音色名是各家起的，ID 才是用户能在官方文档、
 * 控制台里对上的东西；抄错一个字符合成就是失败，得让他边选边核对。
 */
@Composable
private fun VoiceRow(
    label: String,
    id: String,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选中态用实心圆点：这一行整块都可点，勾选框那种形态反而让人以为要点才能选
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    if (selected) UIFriendBarTagTextBlue else Color(0xFFE4DDD0),
                    CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = id,
                fontSize = 11.sp,
                color = UIFriendBarItemMetaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        UIVoicePreviewButton(playing = playing, onClick = onPreview)
    }
}

@Composable
private fun CustomVoiceRow(
    providerId: String?,
    currentVoiceId: String?,
    open: Boolean,
    text: String,
    onOpenChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onPreview: () -> Unit,
    isPreviewing: Boolean,
    previewingVoiceId: String?,
) {
    // 不在内置列表里的音色（复刻音色、国际站音色）本来就是「自定义」，
    // 打开面板时直接展开，否则用户看到自己的复刻音色没被选中会以为没保存
    val builtIn = VoiceCatalog.isBuiltIn(providerId, currentVoiceId)
    val expanded = open || (!builtIn && !currentVoiceId.isNullOrBlank())

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "自定义音色 ID" else "其他音色？手填 ID…",
                modifier = Modifier.weight(1f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTagTextBlue
            )
            if (expanded) {
                UIVoicePreviewButton(
                    playing = isPreviewing && text.isNotBlank() &&
                        previewingVoiceId == text.trim().ifBlank { null },
                    onClick = onPreview
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .uiControl(
                            shape = RoundedCornerShape(UIRadiusControl),
                            color = Color(0xFFEFEAE0)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "例如 longxiaochun_v2",
                            fontSize = 13.5.sp,
                            color = UIFriendBarItemMetaColor.copy(alpha = 0.75f)
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.5.sp, color = UIFriendBarTitleColor),
                        cursorBrush = SolidColor(UIFriendBarTagTextBlue),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .uiControl(shape = RoundedCornerShape(50), color = UIFriendBarTagBgBlue)
                        .clickable { onConfirm() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "使用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTagTextBlue
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "复刻/克隆音色、国际站音色、需要单独开通的精品音色都填在这里；留空等于跟随默认音色。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = UIFriendBarItemMetaColor
            )
        }
    }
}

/** 试听按钮：播放中转圈，其余时候一个小三角。建角色页音色那一行也用这个，形状要一致。 */
@Composable
internal fun UIVoicePreviewButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .uiControl(shape = CircleShape, color = UIFriendBarTagBgBlue)
            .clickable(enabled = !playing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (playing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.8.dp,
                color = UIFriendBarTagTextBlue
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "试听",
                tint = UIFriendBarTagTextBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
