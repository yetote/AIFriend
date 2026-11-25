package com.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.persona.Persona
import com.core.persona.PersonaCategory
import com.ui.agent.data.UIProvider
import com.ui.agent.data.UIReadyTts
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIChatSendDisabled
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UISheetDialog

private const val MAX_BATCH = 12
private const val DEFAULT_BATCH = 4

/**
 * 批量创建面板：统一使用全站 [UISheetDialog] 容器与 12dp 方圆按钮体系。
 */
@Composable
fun UIBatchCreateSheet(
    providers: List<UIProvider>,
    readyTts: List<UIReadyTts>,
    personas: List<Persona>,
    onDismiss: () -> Unit,
    onCreate: (
        count: Int,
        categories: Set<PersonaCategory>,
        brands: Set<String>,
        ttsIds: Set<String>,
        assignVoice: Boolean,
        allowPaid: Boolean
    ) -> Int,
) {
    val allCategories = remember(personas) { personas.map { it.category }.distinct() }
    var count by remember { mutableStateOf(DEFAULT_BATCH) }
    var categories by remember { mutableStateOf(allCategories.toSet()) }
    var brands by remember(providers) { mutableStateOf(providers.map { it.brand }.toSet()) }
    var ttsIds by remember(readyTts) { mutableStateOf(readyTts.map { it.id }.toSet()) }
    var assignVoice by remember(readyTts) { mutableStateOf(readyTts.isNotEmpty()) }
    var allowPaid by remember { mutableStateOf(true) }

    val matchCount = remember(personas, categories) {
        personas.count { it.category in categories }
    }
    val canCreate = count > 0 && matchCount > 0 && brands.isNotEmpty()

    UISheetDialog(
        title = "批量创建",
        subtitle = "每个角色从已配置的模型和语音里随机搭一套，建完还能单独改。",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "创建数量",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
                StepButton("−", enabled = count > 1) { count -= 1 }
                Text(
                    text = "$count",
                    modifier = Modifier.width(44.dp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = UIFriendBarTitleColor
                )
                StepButton("+", enabled = count < MAX_BATCH) { count += 1 }
            }

            SectionHint(
                title = "人设",
                hint = if (matchCount > 0) {
                    "从 $matchCount 个模板里随机，尽量不重复"
                } else {
                    "这些分类里没有模板，换一个分类"
                }
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allCategories, key = { it.name }) { category ->
                    BatchChip(
                        label = category.label,
                        selected = category in categories,
                        onClick = {
                            categories = if (category in categories) {
                                categories - category
                            } else {
                                categories + category
                            }
                        }
                    )
                }
            }

            SectionHint(
                title = "模型",
                hint = "只列你配好 Key 的 ${providers.size} 家，每家的模型随机挑"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers.map { it.brand }, key = { it }) { brand ->
                    BatchChip(
                        label = brand,
                        selected = brand in brands,
                        onClick = {
                            brands = if (brand in brands) brands - brand else brands + brand
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "允许使用付费模型",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (allowPaid) {
                            "每家的模型全部可挑，贵的那档也会被挑到"
                        } else {
                            "只挑 flash / lite / mini 这类轻量档。" +
                                "方舟、百炼、TokenHub 没有公开报价，查不到档位的模型会被跳过"
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
                BatchSwitch(checked = allowPaid, onCheckedChange = { allowPaid = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "随机分配音色",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (readyTts.isEmpty()) {
                            "还没配置语音凭证，这次建的角色先用默认声音"
                        } else if (assignVoice) {
                            "在这 ${ttsIds.size} 家里随机，同批尽量不重复"
                        } else {
                            "不分配音色，角色各自用服务的默认声音"
                        },
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
                BatchSwitch(
                    checked = assignVoice && readyTts.isNotEmpty(),
                    onCheckedChange = { assignVoice = it },
                    enabled = readyTts.isNotEmpty()
                )
            }
            if (assignVoice && readyTts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(readyTts, key = { it.id }) { tts ->
                        BatchChip(
                            label = tts.label,
                            selected = tts.id in ttsIds,
                            onClick = {
                                ttsIds = if (tts.id in ttsIds) ttsIds - tts.id else ttsIds + tts.id
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(if (canCreate) UIActionBrush else SolidColor(UIChatSendDisabled))
                .clickable(enabled = canCreate) {
                    val created = onCreate(
                        count,
                        categories,
                        brands,
                        if (assignVoice) ttsIds else emptySet(),
                        assignVoice && readyTts.isNotEmpty(),
                        allowPaid
                    )
                    if (created > 0) onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (canCreate) "创建 $count 个智能体" else "至少选一个分类和一家模型",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BatchSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = UIFriendBarTagTextBlue,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = UIChatSendDisabled,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SectionHint(title: String, hint: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = hint,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .background(if (enabled) UIAtelierSand else UIAtelierSand.copy(alpha = 0.5f))
            .border(0.8.dp, UIGlassStroke, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) UIFriendBarTitleColor else UIChatSendDisabled
        )
    }
}

@Composable
private fun BatchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Color.White else UIFriendBarTitleColor,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) UIActionBrush else SolidColor(UIAtelierSand))
            .then(if (!selected) Modifier.border(0.8.dp, UIGlassStroke, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
