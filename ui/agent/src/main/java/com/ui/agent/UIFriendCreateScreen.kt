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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.agent.data.UIProvider
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarGray
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTitleColor

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
    thinkingSwitchable: Boolean = true
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var role by rememberSaveable(initialRole) { mutableStateOf(initialRole) }
    var prompt by rememberSaveable(initialPrompt) { mutableStateOf(initialPrompt) }
    var thinking by rememberSaveable(initialThinking) { mutableStateOf(initialThinking) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    val avatarBitmap = rememberBytesBitmap(avatarBytes)
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }
    val canSubmit = name.isNotBlank() && (editing || selectedModel.isNotBlank())
    val selectedProvider = providers.firstOrNull { it.brand == selectedBrand }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UIFriendBarGray)
            // 底部 inset 由底部按钮栏处理，避免与窗口 adjustResize 叠加
            .pageInsetsTop()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = UIFriendBarTitleColor
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = UIFriendBarTitleColor
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = name.ifBlank { "新" },
                    avatarPath = initialAvatarPath,
                    size = 72.dp,
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarCreateButtonText,
                        modifier = Modifier.clickable {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "可选，之后在对话页点头像也能换",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("昵称") },
                placeholder = { Text("例如：小谋") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors()
            )

            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色") },
                placeholder = { Text("例如：产品策略顾问") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors()
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色设定（可选）") },
                placeholder = { Text("例如：你有 10 年 B 端产品经验，回答先给结论，再给 2-3 条理由，语气直接。") },
                minLines = 4,
                maxLines = 8,
                colors = fieldColors()
            )

            if (!editing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "模型提供方",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor
                    )
                    providers.chunked(2).forEach { rowProviders ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            if (thinkingSwitchable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "深度思考",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UIFriendBarTitleColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "关闭后响应更快、更省 token；GLM-5 系列只能降档",
                            fontSize = 12.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    Switch(
                        checked = thinking,
                        onCheckedChange = { thinking = it }
                    )
                }
            }

            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "模型",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor
                    )
                    Text(
                        text = modelLabel.orEmpty(),
                        fontSize = 14.sp,
                        color = UIFriendBarSubtitleColor
                    )
                    Text(
                        text = "编辑时暂不支持切换厂商 / 模型",
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "模型",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UIFriendBarTitleColor
                    )
                    if (isLoadingModels) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = UIFriendBarCreateButtonText
                            )
                            Text(
                                text = "正在获取模型列表…",
                                fontSize = 13.sp,
                                color = UIFriendBarSubtitleColor
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
                        fontSize = 12.sp,
                        color = UIFriendBarItemSubtitleColor
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .pageInsetsBottom()
                .padding(16.dp)
        ) {
            Button(
                onClick = { onSubmit(name, role, prompt, avatarBytes, thinking) },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UIFriendBarCreateButtonText
                )
            ) {
                Text(text = submitLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) UIFriendBarTagBgBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = provider.brand,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) UIFriendBarCreateButtonText else UIFriendBarTitleColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = provider.company,
            fontSize = 11.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

@Composable
private fun ModelOption(model: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) UIFriendBarTagBgBlue else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = model,
            fontSize = 14.sp,
            color = UIFriendBarTitleColor
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = UIFriendBarCreateButtonText,
    focusedLabelColor = UIFriendBarCreateButtonText,
    cursorColor = UIFriendBarCreateButtonText
)
