package com.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.core.persona.Persona
import com.core.persona.PersonaCategory
import com.ui.agent.data.UIProvider
import com.ui.agent.data.UIReadyTts
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusSheet
import com.ui.common.theme.uiCard

/** 一次最多建几个。再多就不是「凑一桌」，而是一屏列表都放不下了。 */
private const val MAX_BATCH = 12

/** 默认建几个：一桌四人局刚好，用户想改也只是一下。 */
private const val DEFAULT_BATCH = 4

/**
 * 批量创建面板：一次建若干个角色，人设、模型、音色各自随机搭配。
 *
 * 为什么需要它：单个创建要填名字、人设、挑模型、挑音色，凑一桌八个人要重复八遍。
 * 而「从已有的东西里搭一套」其实不需要用户决策——他配了哪几家模型、哪几家语音，
 * 这里就在那几家里面随机分，建完再单独微调。
 *
 * 面板里**只列已配置的选项**：没配 Key 的厂商不出现，因为选它必然建出
 * 一个一开口就报错的角色，而错误发生在建好之后，用户根本不会怪到选择上。
 */
@Composable
fun UIBatchCreateSheet(
    /** 已配好 Key 的模型厂商。 */
    providers: List<UIProvider>,
    /** 已配好凭证的语音服务；为空表示这次不能分配音色。 */
    readyTts: List<UIReadyTts>,
    personas: List<Persona>,
    onDismiss: () -> Unit,
    /** 执行创建，返回实际建出来的数量（0 表示什么都没建，面板不该关）。 */
    onCreate: (
        count: Int,
        categories: Set<PersonaCategory>,
        brands: Set<String>,
        ttsIds: Set<String>,
        assignVoice: Boolean
    ) -> Int,
) {
    val allCategories = remember(personas) { personas.map { it.category }.distinct() }
    var count by remember { mutableStateOf(DEFAULT_BATCH) }
    var categories by remember { mutableStateOf(allCategories.toSet()) }
    var brands by remember(providers) { mutableStateOf(providers.map { it.brand }.toSet()) }
    var ttsIds by remember(readyTts) { mutableStateOf(readyTts.map { it.id }.toSet()) }
    // 没配语音时开关默认关着：给角色分配一个用不了的音色，比不分配更糟
    var assignVoice by remember(readyTts) { mutableStateOf(readyTts.isNotEmpty()) }

    val matchCount = remember(personas, categories) {
        personas.count { it.category in categories }
    }
    val canCreate = count > 0 && matchCount > 0 && brands.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .uiCard(shape = RoundedCornerShape(UIRadiusSheet), elevation = 26.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "批量创建",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "每个角色从已配置的模型和语音里随机搭一套，建完还能单独改。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
                Text(
                    text = "取消",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    color = UIFriendBarTagTextBlue
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ---- 数量 -------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "创建数量",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTitleColor
                    )
                    StepButton("−", enabled = count > 1) { count -= 1 }
                    Text(
                        text = "$count",
                        modifier = Modifier.width(44.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = UIFriendBarTitleColor
                    )
                    StepButton("+", enabled = count < MAX_BATCH) { count += 1 }
                }

                // ---- 人设 -------------------------------------------------
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

                // ---- 模型 -------------------------------------------------
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

                // ---- 音色 -------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "随机分配音色",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
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
                    Switch(
                        checked = assignVoice && readyTts.isNotEmpty(),
                        onCheckedChange = { assignVoice = it },
                        enabled = readyTts.isNotEmpty(),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SwitchDefaults.colors().checkedTrackColor,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFC3CBD9)
                        )
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

            // ---- 提交 -----------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (canCreate) UIActionBrush else SolidColor(Color(0xFFC3CBD9)))
                    .clickable(enabled = canCreate) {
                        val created = onCreate(
                            count,
                            categories,
                            brands,
                            if (assignVoice) ttsIds else emptySet(),
                            assignVoice && readyTts.isNotEmpty()
                        )
                        // 建成功了才关；一个都没建（比如凭证刚被删）时别让用户以为已经建好
                        if (created > 0) onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (canCreate) "创建 $count 个智能体" else "至少选一个分类和一家模型",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionHint(title: String, hint: String) {
    Column {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
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

/** 数量加减：14dp 的字在圆形按钮里才按得准。 */
@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFFEEF1F6) else Color(0xFFF4F6F9))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) UIFriendBarTitleColor else Color(0xFFC3CBD9)
        )
    }
}

/** 多选 chip：和音色面板的分类条保持一致，用户不用学第二套交互。 */
@Composable
private fun BatchChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Color.White else UIFriendBarTitleColor,
        modifier = Modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIActionBrush else SolidColor(Color(0xFFEEF1F6)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
