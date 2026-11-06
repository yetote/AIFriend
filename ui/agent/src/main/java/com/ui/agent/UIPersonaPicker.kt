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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.ui.agent.data.UIPersona
import com.ui.agent.data.filterPersonas
import com.ui.agent.data.personaCategories
import com.ui.common.UIAvatar
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
 * 人格模板选择面板。
 *
 * 分类 + 关键词两层过滤，选中即回填昵称 / 角色 / 角色设定，
 * 回填之后所有字段依旧可以自由修改。
 */
@Composable
fun UIPersonaPickerSheet(
    personas: List<UIPersona>,
    onDismiss: () -> Unit,
    onPick: (UIPersona) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(personas) { personas.personaCategories() }
    val filtered = remember(personas, category, query) { personas.filterPersonas(category, query) }

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
                        text = "人格模板",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UIFriendBarTitleColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "套用后仍可自由修改",
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
                        color = Color(0xFFF4F6FA)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = UIFriendBarItemMetaColor,
                        modifier = Modifier.width(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜昵称、角色或标签",
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
                    CategoryChip(
                        label = "全部",
                        selected = category == null,
                        onClick = { category = null }
                    )
                }
                items(categories) { (key, label) ->
                    CategoryChip(
                        label = label,
                        selected = category == key,
                        onClick = { category = if (category == key) null else key }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(UISeparator)
            )

            if (filtered.isEmpty()) {
                Text(
                    text = "没找到匹配的模板，换个词试试",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    fontSize = 13.sp,
                    color = UIFriendBarItemMetaColor
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(filtered, key = { it.id }) { persona ->
                        PersonaRow(persona = persona, onClick = { onPick(persona) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .uiControl(
                shape = RoundedCornerShape(50),
                color = if (selected) UIFriendBarTagBgBlue else Color.White.copy(alpha = 0.92f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun PersonaRow(persona: UIPersona, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UIAvatar(name = persona.name, avatarPath = null, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = persona.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = persona.role,
                    fontSize = 12.sp,
                    color = UIFriendBarTagTextBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = persona.prompt,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = UIFriendBarItemMetaColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
