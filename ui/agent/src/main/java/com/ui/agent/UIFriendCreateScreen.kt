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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.ui.agent.data.UIModelGroup
import com.ui.agent.data.UIModelOption
import com.ui.agent.data.UIProvider
import com.ui.agent.data.UIPriceTone
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIFriendBarCreateButtonText
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
    personaToggleLabel: String = "从模板开始",
    /** 当前厂商是不是聚合平台（决定模型列表要不要分组、标价）。 */
    isGateway: Boolean = false,
    /** 聚合平台的模型分组，官方厂商为空。 */
    modelGroups: List<UIModelGroup> = emptyList(),
    /** 价格来自内置快照时会加一句「参考价」的注脚。 */
    priceIsSnapshot: Boolean = false,
    priceSnapshotDate: String? = null,
    /** 这个平台能不能手动刷新价格（只有抓得到定价页的聚合平台可以）。 */
    canRefreshPrice: Boolean = false,
    /** 刷新进行中，按钮转圈并禁用。 */
    isRefreshingPrice: Boolean = false,
    /** 点「刷新价格」。为空表示当前平台不支持刷新。 */
    onRefreshPrice: () -> Unit = {},
    /**
     * 一个服务商都没配时，引导用户去设置页填 Key。
     *
     * 不设默认值：漏传应该是编译错误，而不是点了没反应。
     */
    onOpenSettings: () -> Unit
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

                /*
                 * 深度思考放在选模型之前。
                 *
                 * 这是好友级设置，和选哪家厂商无关，但放在模型列表后面会被
                 * 68 个模型埋掉 —— 滚动几屏才看得到，等于没有。
                 * 放在这一屏的位置既能一眼看到，也不会像之前那样紧贴
                 * 「聚合平台」网格而被误读成聚合平台专属。
                 */
                if (thinkingSwitchable) {
                    SectionLabel(text = "对话选项")
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
                                text = "对所有厂商的模型都生效，关闭后响应更快、更省 token",
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

                if (!editing) {
                    // 官方厂商和聚合平台分开摆。混在一起的话，用户会把
                    // 「硅基流动」当成又一个模型厂商，而不是一个能调很多家模型的平台。
                    val official = providers.filterNot { it.isGateway }
                    val gateways = providers.filter { it.isGateway }

                    SectionLabel(text = "模型提供方")

                    if (official.isEmpty() && gateways.isEmpty()) {
                        // Key 全由用户自己填，一个都没配时这里不能空着：
                        // 空网格看起来像加载失败，用户不知道下一步该去哪。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 6.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "还没有可用的模型服务",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = UIFriendBarTitleColor
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "App 不内置任何 API Key。去设置页填一个自己的 Key，就能开始创建好友。",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = UIFriendBarItemMetaColor
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "去设置",
                                modifier = Modifier.clickable(onClick = onOpenSettings),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarCreateButtonText
                            )
                        }
                    } else {
                        ProviderGrid(
                            providers = official,
                            selectedBrand = selectedBrand,
                            onBrandSelect = onBrandSelect
                        )
                    }

                    if (gateways.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "聚合平台",
                                modifier = Modifier.padding(start = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UIFriendBarItemMetaColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "一个 Key 调多家模型",
                                fontSize = 11.sp,
                                color = UIFriendBarItemMetaColor.copy(alpha = 0.7f)
                            )
                        }
                        ProviderGrid(
                            providers = gateways,
                            selectedBrand = selectedBrand,
                            onBrandSelect = onBrandSelect
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

                    if (modelGroups.isNotEmpty()) {
                        // 有分组就按牌子摆：聚合平台（硅基流动…）和官方厂商里
                        // 代理了别家模型的那两家（百炼、方舟）都走这里。
                        // 后者没有价格，组标题上方不会出现价格说明。
                        ModelGroupBrowser(
                            groups = modelGroups,
                            selectedModel = selectedModel,
                            onModelSelect = onModelSelect,
                            canRefreshPrice = canRefreshPrice,
                            isRefreshingPrice = isRefreshingPrice,
                            onRefreshPrice = onRefreshPrice
                        )
                    } else {
                        // 纯官方厂商：一个牌子一个列表，不分组也不标价
                        models.forEach { model ->
                            ModelOption(
                                model = UIModelOption(id = model, label = model),
                                selected = model == selectedModel,
                                onClick = { onModelSelect(model) }
                            )
                        }
                    }

                    // 只有真的拿到价格时才解释价格；自定义网关可能一个价都查不到，
                    // 那时候再说「价格按每百万 token 计」就成了空话
                    val hasAnyPrice = modelGroups.any { group -> group.models.any { it.price != null } }
                    if (hasAnyPrice) {
                        PriceFootnote(
                            isSnapshot = priceIsSnapshot,
                            capturedAt = priceSnapshotDate
                        )
                    } else if (!isGateway) {
                        Text(
                            text = "由 ${selectedProvider?.company ?: selectedBrand} 提供支持",
                            modifier = Modifier.padding(start = 4.dp),
                            fontSize = 12.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
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
private fun ProviderGrid(
    providers: List<UIProvider>,
    selectedBrand: String,
    onBrandSelect: (String) -> Unit
) {
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

/**
 * 聚合平台的模型浏览区：厂商横向滑动，选中厂商的模型竖向列出。
 *
 * 之前是每个厂商一整段竖着堆，硅基流动十几家品牌加起来几十屏，
 * 想找「Qwen」得一路滑到底。改成标签栏之后，横向翻牌子、竖向看模型，
 * 两个方向各管一件事，找起来快很多。
 */
@Composable
private fun ModelGroupBrowser(
    groups: List<UIModelGroup>,
    selectedModel: String,
    onModelSelect: (String) -> Unit,
    canRefreshPrice: Boolean = false,
    isRefreshingPrice: Boolean = false,
    onRefreshPrice: () -> Unit = {}
) {
    // 默认落在第一个分组（也就是「全部」）：不确定选哪家时，先看全局最实用
    var selectedVendor by rememberSaveable { mutableStateOf(groups.firstOrNull()?.vendor) }
    // 换厂商后旧的分组可能已经不存在了，回落到第一个；空列表直接不画
    val current = groups.firstOrNull { it.vendor == selectedVendor } ?: groups.firstOrNull() ?: return

    // 「全部」里可以切换升序 / 降序；切到具体厂商时保留状态，
    // 但那边本来就只有一种顺序，不显示这个开关
    var ascending by rememberSaveable { mutableStateOf(true) }
    val models = remember(current, ascending) {
        if (current.isAll && !ascending) {
            current.models.sortedWith(
                compareBy<UIModelOption> { it.sortWeight == null }
                    .thenByDescending { it.sortWeight ?: 0.0 }
                    .thenBy { it.id.lowercase() }
            )
        } else {
            current.models
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(groups, key = { it.vendor }) { group ->
                ModelVendorChip(
                    group = group,
                    selected = group.vendor == current.vendor,
                    onClick = { selectedVendor = group.vendor }
                )
            }
        }

        Row(
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "共 ${models.size} 个",
                fontSize = 11.sp,
                color = UIFriendBarItemMetaColor.copy(alpha = 0.7f)
            )
            val from = current.from
            if (from != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    // 「免费」后面再加「起」不通，所以免费时不加
                    text = if (from == "免费") from else "$from 起",
                    fontSize = 11.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
            Spacer(Modifier.weight(1f))
            if (current.isAll) {
                PriceOrderToggle(ascending = ascending, onToggle = { ascending = !ascending })
            }
            // 只有价目从网页抓的平台才给刷新按钮，OpenRouter 的价格跟着模型列表一起回来
            if (canRefreshPrice) {
                Spacer(Modifier.width(8.dp))
                PriceRefreshButton(
                    refreshing = isRefreshingPrice,
                    onClick = onRefreshPrice
                )
            }
        }

        models.forEach { model ->
            ModelOption(
                model = model,
                selected = model.id == selectedModel,
                onClick = { onModelSelect(model.id) },
                // 「全部」里混着各家厂商，不标来源就分不清两个同名模型
                showVendor = current.isAll
            )
        }
    }
}

/** 厂商标签：名字 + 起价，选中时染成品牌蓝。 */
@Composable
private fun ModelVendorChip(
    group: UIModelGroup,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .uiControl(
                shape = RoundedCornerShape(50),
                color = if (selected) UIFriendBarTagBgBlue else Color.White.copy(alpha = 0.9f)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.vendor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor
        )
        Text(
            text = " ${group.models.size}",
            fontSize = 10.sp,
            color = UIFriendBarItemMetaColor.copy(alpha = 0.75f)
        )
    }
}

/** 「价格升序 / 降序」的小开关，只在「全部」里出现。 */
@Composable
private fun PriceOrderToggle(ascending: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .uiControl(
                shape = RoundedCornerShape(50),
                color = UIFriendBarTagBgBlue.copy(alpha = 0.7f)
            )
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ascending) "价格从低到高" else "价格从高到低",
            fontSize = 11.sp,
            color = UIFriendBarTagTextBlue
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = if (ascending) "↑" else "↓",
            fontSize = 11.sp,
            color = UIFriendBarTagTextBlue
        )
    }
}

/**
 * 手动刷新价格的小按钮。
 *
 * 聚合平台的价格是从定价页抓的，可能都过去一天了，用户想核实时不用
 * 专门跑一趟设置页。刷新期间转圈并禁用，避免连点发出多次请求。
 */
@Composable
private fun PriceRefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .uiControl(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.9f)
            )
            .clickable(enabled = !refreshing) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = UIFriendBarTagTextBlue
            )
            Spacer(Modifier.width(5.dp))
            Text(text = "刷新中", fontSize = 11.sp, color = UIFriendBarTagTextBlue)
        } else {
            Text(text = "刷新价格", fontSize = 11.sp, color = UIFriendBarTagTextBlue)
        }
    }
}

@Composable
private fun ModelOption(
    model: UIModelOption,
    selected: Boolean,
    onClick: () -> Unit,
    /** 跨厂商列表里补一行来源厂商。 */
    showVendor: Boolean = false
) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = UIFriendBarTitleColor
            )
            // 「全部」里同名模型不止一个，跟一行小字说明出处
            val vendorName = model.vendorName
            if (showVendor && vendorName != null) {
                Text(
                    text = vendorName,
                    fontSize = 10.sp,
                    color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
                )
            }
            val price = model.price
            if (price != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = price,
                        fontSize = 11.sp,
                        color = priceToneColor(model.tone)
                    )
                    // 分档模型标一句「分段计价」，否则用户会以为这就是全部价格
                    if (model.bands.isNotEmpty()) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "分段计价",
                            fontSize = 10.sp,
                            color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
                        )
                    }
                }
                // 贵的那几档用小字排在下面，不抢主价的视线
                model.bands.forEach { band ->
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = band,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = UIFriendBarItemMetaColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * 价格说明。
 *
 * 快照价格必须标注来源日期：一句话能避免用户拿三个月前的价去对账单。
 */
@Composable
private fun PriceFootnote(
    isSnapshot: Boolean,
    capturedAt: String?
) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
        Text(
            text = "价格按每百万 token 计（输入 / 输出），便宜的排在前面",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = UIFriendBarItemMetaColor
        )
        if (isSnapshot && capturedAt != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = "该平台接口不返回价格，此处取自 $capturedAt 的定价页，实际以平台账单为准",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = UIFriendBarItemMetaColor.copy(alpha = 0.75f)
            )
        }
    }
}

/** 按价格档位给文字上色，不用比数字也能看出便宜还是贵。 */
private fun priceToneColor(tone: UIPriceTone?): Color = when (tone) {
    UIPriceTone.CHEAP -> Color(0xFF0F9D58)
    UIPriceTone.NORMAL -> UIFriendBarItemMetaColor
    UIPriceTone.EXPENSIVE -> Color(0xFFD97706)
    null -> UIFriendBarItemMetaColor
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
