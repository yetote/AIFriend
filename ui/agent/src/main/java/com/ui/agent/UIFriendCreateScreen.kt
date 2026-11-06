package com.ui.agent

import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.UIAvatar
import com.ui.common.UIAvatarCropDialog
import com.ui.common.rememberBytesBitmap
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.core.persona.Persona
import com.ui.agent.data.UIProvider
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.common.theme.UIDialogButtons
import com.ui.common.theme.UIIosDialog

@Composable
fun UIFriendCreateScreen(
    providers: List<UIProvider>,
    selectedBrand: String,
    onBrandSelect: (String) -> Unit,
    models: List<String>,
    selectedModel: String,
    isLoadingModels: Boolean,
    onModelSelect: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: (name: String, role: String, prompt: String, avatarBytes: ByteArray?, thinkingEnabled: Boolean) -> Unit,
    title: String = "创建 AI 好友",
    submitLabel: String = "创建并开始聊天",
    initialName: String = "",
    initialRole: String = "",
    initialPrompt: String = "",
    initialAvatarPath: String? = null,
    initialThinking: Boolean = false,
    editing: Boolean = false,
    modelLabel: String? = null,
    thinkingSwitchable: Boolean = true,
    personas: List<Persona> = emptyList(),
    personaToggleLabel: String = "从模板开始"
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var role by rememberSaveable(initialRole) { mutableStateOf(initialRole) }
    var prompt by rememberSaveable(initialPrompt) { mutableStateOf(initialPrompt) }
    var thinking by rememberSaveable(initialThinking) { mutableStateOf(initialThinking) }
    var pickingPersona by remember { mutableStateOf(false) }
    var appliedPersonaId by rememberSaveable { mutableStateOf("") }
    var pendingPersona by remember { mutableStateOf<Persona?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    val avatarBitmap = rememberBytesBitmap(avatarBytes)
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }
    val canSubmit = name.isNotBlank() && (editing || selectedModel.isNotBlank())
    val selectedProvider = providers.firstOrNull { it.brand == selectedBrand }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            UITopBar(title = title, onBack = onCancel)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UIAvatar(
                        name = name.ifBlank { "新" },
                        avatarPath = initialAvatarPath,
                        size = 68.dp,
                        shape = CircleShape,
                        imageBitmap = avatarBitmap,
                        modifier = Modifier.clickable {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (avatarBytes == null && initialAvatarPath == null) "选择头像" else "更换头像",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTagTextBlue,
                            modifier = Modifier.clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "可选，之后在对话页点头像也能换",
                            fontSize = 12.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }

                if (personas.isNotEmpty()) {
                    val applied = personas.firstOrNull { it.id == appliedPersonaId }
                    val modified = applied != null &&
                        (name != applied.name || role != applied.role || prompt != applied.prompt)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
                            .clickable { pickingPersona = true }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    applied == null -> personaToggleLabel
                                    modified -> "已基于：${applied.name}"
                                    else -> "已套用：${applied.name}"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTitleColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = when {
                                    applied == null -> "内置人格与角色，选一个再改，比从空白开始快"
                                    modified -> "${applied.role} · 已按你的想法改过"
                                    else -> "${applied.role} · 已填好，可继续修改"
                                },
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = UIFriendBarItemMetaColor
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (applied == null) "选择" else "换一个",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UIFriendBarTagTextBlue
                        )
                    }
                }

                LabeledInput(
                    label = "昵称",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "例如：小谋",
                    singleLine = true
                )

                LabeledInput(
                    label = "角色",
                    value = role,
                    onValueChange = { role = it },
                    placeholder = "例如：产品策略顾问",
                    singleLine = true
                )

                LabeledInput(
                    label = "角色设定（可选）",
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "例如：你有 10 年 B 端产品经验，回答先给结论，再给 2-3 条理由，语气直接。",
                    minLines = 4
                )

                if (!editing) {
                    SectionLabel(text = "模型提供方")
                    providers.chunked(2).forEach { rowProviders ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowProviders.forEach { provider ->
                                ProviderChip(
                                    provider = provider,
                                    selected = provider.brand == selectedBrand,
                                    onClick = { onBrandSelect(provider.brand) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowProviders.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (thinkingSwitchable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "深度思考",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarTitleColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "关闭后响应更快、更省 token；GLM-5 系列只能降档",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = UIFriendBarItemMetaColor
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = thinking,
                            onCheckedChange = { thinking = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = UIFriendBarTagTextBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD7DCE5),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                if (editing) {
                    SectionLabel(text = "模型")
                    Text(
                        text = modelLabel.orEmpty(),
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 14.sp,
                        color = UIFriendBarTitleColor
                    )
                    Text(
                        text = "编辑时暂不支持切换厂商 / 模型",
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 12.sp,
                        color = UIFriendBarItemMetaColor
                    )
                } else {
                    SectionLabel(text = "模型")
                    if (isLoadingModels) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = UIFriendBarTagTextBlue
                            )
                            Text(
                                text = "正在获取模型列表…",
                                fontSize = 13.sp,
                                color = UIFriendBarItemMetaColor
                            )
                        }
                    }
                    models.forEach { model ->
                        ModelOption(
                            model = model,
                            selected = model == selectedModel,
                            onClick = { onModelSelect(model) }
                        )
                    }
                    Text(
                        text = "由 ${selectedProvider?.company ?: selectedBrand} 提供支持",
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 12.sp,
                        color = UIFriendBarItemMetaColor
                    )
                }
            }

            // 底部按钮栏同样不用通栏白条：和页面里其他卡片一样浮起来，
            // 靠圆角 + 描边 + 投影和内容分开，而不是一条直角白带贴边。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 12.dp)
                    .pageInsetsBottom()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(
                            elevation = if (canSubmit) 10.dp else 0.dp,
                            shape = RoundedCornerShape(50),
                            clip = false,
                            ambientColor = UIBlobBlue,
                            spotColor = UIBlobBlue
                        )
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (canSubmit) {
                                Modifier.background(UIActionBrush)
                            } else {
                                Modifier.background(Color(0xFFC3CBD9))
                            }
                        )
                        .clickable(enabled = canSubmit) {
                            onSubmit(name, role, prompt, avatarBytes, thinking)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = submitLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
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
                avatarBytes = bytes
                cropUri = null
            }
        )
    }

    if (pickingPersona) {
        UIPersonaPickerSheet(
            personas = personas,
            onDismiss = { pickingPersona = false },
            onPick = { persona ->
                pickingPersona = false
                val unchanged = name == persona.name && role == persona.role && prompt == persona.prompt
                if (unchanged || (name.isBlank() && role.isBlank() && prompt.isBlank())) {
                    name = persona.name
                    role = persona.role
                    prompt = persona.prompt
                    appliedPersonaId = persona.id
                } else {
                    pendingPersona = persona
                }
            }
        )
    }

    val replacing = pendingPersona
    if (replacing != null) {
        UIIosDialog(
            title = "替换现在的内容？",
            subtitle = "套用「${replacing.name}」会覆盖已经填好的昵称、角色和设定。",
            onDismiss = { pendingPersona = null },
            actions = {
                UIDialogButtons(
                    confirmText = "替换",
                    onConfirm = {
                        name = replacing.name
                        role = replacing.role
                        prompt = replacing.prompt
                        appliedPersonaId = replacing.id
                        pendingPersona = null
                    },
                    cancelText = "再想想",
                    onCancel = { pendingPersona = null }
                )
            }
        )
    }
}

@Composable
private fun ProviderChip(
    provider: UIProvider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .uiControl(
                shape = RoundedCornerShape(UIRadiusControl),
                color = if (selected) UIFriendBarTagBgBlue else Color.White.copy(alpha = 0.92f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            text = provider.brand,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = provider.company,
            fontSize = 11.sp,
            color = UIFriendBarItemMetaColor
        )
    }
}

@Composable
private fun ModelOption(model: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarTagBgBlue else Color.White.copy(alpha = 0.9f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = UIFriendBarTagTextBlue)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = model,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = UIFriendBarTitleColor
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = UIFriendBarItemMetaColor
    )
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(text = label)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .uiControl(
                    shape = RoundedCornerShape(if (singleLine) 50.dp else UIRadiusControl),
                    color = Color.White.copy(alpha = 0.92f)
                )
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = UIFriendBarItemMetaColor.copy(alpha = 0.75f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = UIFriendBarTitleColor),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = if (singleLine) 1 else 8,
                cursorBrush = SolidColor(UIFriendBarTagTextBlue),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
                )
            )
        }
    }
}
