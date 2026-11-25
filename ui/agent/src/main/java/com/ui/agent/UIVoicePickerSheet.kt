package com.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.ai.model.VoiceCatalog
import com.core.ai.model.VoiceGroup
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISeparator
import com.ui.common.theme.UISheetDialog
import com.ui.common.theme.uiControl

/**
 * 音色选择面板：统一使用全站 [UISheetDialog] 骨架，搜索 + 分类 + 逐条试听。
 */
@Composable
fun UIVoicePickerSheet(
    providerId: String?,
    currentVoiceId: String?,
    isPreviewing: Boolean,
    previewingVoiceId: String?,
    previewError: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
    onPreview: (String?) -> Unit,
) {
    var query by rememberSaveable(providerId) { mutableStateOf("") }
    var groupName by rememberSaveable(providerId) { mutableStateOf<String?>(null) }
    var customOpen by rememberSaveable(providerId) { mutableStateOf(false) }
    var customText by rememberSaveable(providerId) { mutableStateOf("") }

    val groups = remember(providerId) { VoiceCatalog.grouped(providerId) }
    val selectedGroup = groupName?.let { name -> VoiceGroup.entries.firstOrNull { it.name == name } }
    val effectiveGroup = if (query.isBlank()) selectedGroup else null
    val options = remember(providerId, effectiveGroup, query) {
        VoiceCatalog.search(providerId, query)
            .filter { it.id != null && (effectiveGroup == null || it.group == effectiveGroup) }
    }
    val showDefault = query.isBlank() && effectiveGroup == null
    val total = remember(providerId) { VoiceCatalog.forProvider(providerId).count { it.id != null } }

    UISheetDialog(
        title = "选择音色",
        subtitle = "点右侧试听，未开通的音色会当场报错",
        onDismiss = onDismiss,
        maxHeight = 590.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIAtelierSand)
                .border(0.8.dp, UIGlassStroke, RoundedCornerShape(UIRadiusControl))
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
                            color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
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
                .height(0.75.dp)
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
                color = UIFriendBarRed
            )
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) UIActionBrush else SolidColor(UIAtelierSand))
            .then(if (!selected) Modifier.border(0.8.dp, UIGlassStroke, shape) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else UIFriendBarTitleColor
        )
    }
}

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
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) UIFriendBarTagBgBlue.copy(alpha = 0.65f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    val builtIn = VoiceCatalog.isBuiltIn(providerId, currentVoiceId)
    val expanded = open || (!builtIn && !currentVoiceId.isNullOrBlank())

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
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
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .background(UIAtelierSand)
                        .border(0.8.dp, UIGlassStroke, RoundedCornerShape(UIRadiusControl))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "例如 longxiaochun_v2",
                            fontSize = 13.5.sp,
                            color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
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
                        .clip(RoundedCornerShape(UIRadiusControl))
                        .background(UIActionBrush)
                        .clickable { onConfirm() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "使用",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
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

@Composable
internal fun UIVoicePreviewButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .uiControl(shape = RoundedCornerShape(10.dp), color = UIFriendBarTagBgBlue)
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
