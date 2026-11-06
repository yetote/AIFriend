package com.ui.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.agent.data.UIPriceTone
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarGray
import com.ui.common.theme.UIFriendBarItemMetaColor
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarOnlineGreen
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTagBgBlue
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl

/**
 * 模型与密钥设置页。
 *
 * 设计取舍：
 * - 入口放在首页右上角齿轮，不新开 Tab —— 这是低频配置，不该占主导航。
 * - 服务商用「卡片列表 + 单选」而不是下拉菜单：一共就三个以内，
 *   平铺比藏进菜单更容易一眼看懂各自要填什么。
 * - Key 输入默认打码，配一个「显示」切换，方便用户核对粘贴是否完整。
 * - 「测试并保存」一步完成：先真的请求一次 /models，
 *   通了才落盘，避免用户存了个错的 Key 之后在聊天页才发现。
 */
@Composable
fun UIModelSettingsPage(
    state: UIModelSettingsState,
    onBack: () -> Unit,
    onProviderSelect: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelSelect: (String) -> Unit,
    onSave: () -> Unit,
    onOpenConsole: (String) -> Unit = {},
    onClearKey: () -> Unit = {},
    onRefreshPrice: () -> Unit = {}
) {
    var keyVisible by remember { mutableStateOf(false) }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageInsetsBottom()
        ) {
            UITopBar(title = "模型与密钥", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                IntroCard()

                Spacer(Modifier.height(14.dp))
                SectionLabel("选择服务商")
                Spacer(Modifier.height(8.dp))
                state.providers.forEach { provider ->
                    ProviderRow(
                        option = provider,
                        selected = provider.id == state.selectedProviderId,
                        onClick = { onProviderSelect(provider.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(6.dp))
                val provider = state.selectedProvider
                if (provider != null) {
                    SectionLabel("API Key")
                    Spacer(Modifier.height(8.dp))
                    KeyField(
                        value = state.keyInput,
                        onValueChange = onKeyChange,
                        visible = keyVisible,
                        onToggleVisible = { keyVisible = !keyVisible },
                        placeholder = if (provider.hasKey) {
                            "已保存 ${provider.maskedKey}，留空则保持不变"
                        } else {
                            "粘贴你的 API Key"
                        }
                    )

                    if (provider.editableBaseUrl) {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel("Base URL")
                        Spacer(Modifier.height(8.dp))
                        PlainField(
                            value = state.baseUrlInput,
                            onValueChange = onBaseUrlChange,
                            placeholder = "https://your-host/v1"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "填到 /v1 为止，不要带 /chat/completions",
                            fontSize = 11.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (provider.hasKey) {
                            Text(
                                text = "清除已保存的 Key",
                                fontSize = 12.sp,
                                color = UIFriendBarRed,
                                modifier = Modifier.clickable { onClearKey() }
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        if (state.selectedProvider?.id != null) {
                            Text(
                                text = "去申请 →",
                                fontSize = 12.sp,
                                color = UIFriendBarCreateButtonText,
                                modifier = Modifier.clickable {
                                    onOpenConsole(state.selectedProviderId)
                                }
                            )
                        }
                    }
                }

                state.verifyResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    VerifyBanner(result)
                }

                if (state.models.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    ModelSectionHeader(
                        count = state.models.size,
                        canRefresh = state.canRefreshPrice,
                        isRefreshing = state.isRefreshingPrice,
                        hint = state.priceRefreshHint,
                        onRefresh = onRefreshPrice
                    )
                    Spacer(Modifier.height(8.dp))
                    ModelList(
                        models = state.pricedModels.ifEmpty {
                            state.models.map { UISettingsModel(id = it) }
                        },
                        selected = state.selectedModel,
                        onSelect = onModelSelect
                    )
                    if (state.priceIsSnapshot && state.priceSnapshotDate != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "价格为 ${
                                state.priceSnapshotDate
                            } 从官方定价页取到的标价（每百万 token），实际以平台账单为准",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = UIFriendBarItemMetaColor
                        )
                    }
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = UIFriendBarRed
                    )
                }

                Spacer(Modifier.height(20.dp))
                SaveButton(
                    enabled = state.canSave,
                    loading = state.isVerifying || state.isLoadingModels,
                    onClick = onSave
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Key 只保存在本机，不会上传到任何服务器。",
                    fontSize = 11.sp,
                    color = UIFriendBarItemMetaColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .padding(16.dp)
    ) {
        Text(
            text = "一把 Key 用遍所有模型",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "填一次就能创建使用不同模型的 AI 好友，不用为每个模型单独注册。",
            fontSize = 12.sp,
            color = UIFriendBarItemSubtitleColor,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = UIFriendBarItemSubtitleColor
    )
}

/**
 * 模型区标题，右侧带「刷新价格」。
 *
 * 价格一天自动更新一次，按钮是给想立刻看当天价的用户准备的 ——
 * 比如刚要挑一个便宜模型，不想等明天。
 *
 * 刷新只在有定价页可抓的平台出现（目前只有硅基流动）；
 * OpenRouter 的价格跟着「测试并保存」一起回来，不需要单独刷。
 */
@Composable
private fun ModelSectionHeader(
    count: Int,
    canRefresh: Boolean,
    isRefreshing: Boolean,
    hint: String?,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("选择模型（$count）")
            Spacer(Modifier.weight(1f))
            if (canRefresh) {
                Row(
                    modifier = Modifier
                        .uiControl(
                            shape = RoundedCornerShape(UIRadiusControl),
                            color = UIFriendBarTagBgBlue
                        )
                        .clickable(enabled = !isRefreshing, onClick = onRefresh)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = UIFriendBarTagTextBlue
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "更新中",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTagTextBlue
                        )
                    } else {
                        Text(
                            text = "刷新价格",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = UIFriendBarTagTextBlue
                        )
                    }
                }
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = hint,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = if (hint.startsWith("刷新失败")) UIFriendBarRed
                else UIFriendBarOnlineGreen
            )
        }
    }
}

@Composable
private fun ProviderRow(
    option: UIProviderOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(
                shape = RoundedCornerShape(UIRadiusCard),
                borderColor = if (selected) {
                    UIFriendBarCreateButtonText.copy(alpha = 0.35f)
                } else {
                    UIGlassStroke
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UIFriendBarTitleColor
                )
                if (option.hasKey) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "已配置",
                        fontSize = 10.sp,
                        color = UIFriendBarTagTextBlue,
                        modifier = Modifier
                            .background(
                                color = UIFriendBarTagBgBlue,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = option.description,
                fontSize = 11.sp,
                color = UIFriendBarItemMetaColor,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        SelectionDot(selected)
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .uiControl(
                shape = CircleShape,
                color = if (selected) UIFriendBarCreateButtonText else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(text = "✓", fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
private fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(
                shape = RoundedCornerShape(UIRadiusControl),
                color = UIGlassSurfaceStrong
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 13.sp,
                    color = UIFriendBarItemSubtitleColor.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = UIFriendBarTitleColor),
                singleLine = true,
                cursorBrush = SolidColor(UIFriendBarCreateButtonText),
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (visible) "隐藏" else "显示",
            fontSize = 11.sp,
            color = UIFriendBarCreateButtonText,
            modifier = Modifier.clickable { onToggleVisible() }
        )
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(
                shape = RoundedCornerShape(UIRadiusControl),
                color = UIGlassSurfaceStrong
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 13.sp,
                color = UIFriendBarItemSubtitleColor.copy(alpha = 0.65f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 13.sp, color = UIFriendBarTitleColor),
            singleLine = true,
            cursorBrush = SolidColor(UIFriendBarCreateButtonText),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )
    }
}

@Composable
private fun VerifyBanner(result: UIVerifyResult) {
    val (text, color) = when (result) {
        is UIVerifyResult.Success -> "连接成功，拉取到 ${result.modelCount} 个可用模型" to UIFriendBarOnlineGreen
        is UIVerifyResult.Failure -> result.message to UIFriendBarRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusControl))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = UIFriendBarTitleColor,
            lineHeight = 17.sp
        )
    }
}

/**
 * 模型列表。
 *
 * 单独给一个带最大高度的滚动区：硅基流动有 50+ 个模型，
 * 让它跟着整页一起滚会把下面的保存按钮推得很远，
 * 用户填完 Key 找不到「保存」在哪。
 *
 * 聚合平台会带价格，官方厂商的 [UISettingsModel.price] 是 null，只显示名字。
 */
@Composable
private fun ModelList(
    models: List<UISettingsModel>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .uiCard(
                shape = RoundedCornerShape(UIRadiusCard),
                color = UIGlassSurfaceStrong
            )
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(models) { model ->
                val isSelected = model.id == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(model.id) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.id,
                            fontSize = 12.sp,
                            color = if (isSelected) {
                                UIFriendBarCreateButtonText
                            } else {
                                UIFriendBarTitleColor
                            },
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val price = model.price
                        if (price != null) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = price,
                                    fontSize = 11.sp,
                                    color = settingsPriceToneColor(model.tone)
                                )
                                if (model.bands.isNotEmpty()) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "分段计价",
                                        fontSize = 10.sp,
                                        color = UIFriendBarItemMetaColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            // 超出常规上下文后的价格，小字附在下面
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
                    if (isSelected) {
                        Text(text = "✓", fontSize = 12.sp, color = UIFriendBarCreateButtonText)
                    }
                }
            }
        }
    }
}

/** 价格档位对应的颜色，和创建页保持一致。 */
private fun settingsPriceToneColor(tone: UIPriceTone?): Color = when (tone) {
    UIPriceTone.CHEAP -> Color(0xFF0F9D58)
    UIPriceTone.NORMAL -> UIFriendBarItemMetaColor
    UIPriceTone.EXPENSIVE -> Color(0xFFD97706)
    null -> UIFriendBarItemMetaColor
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                brush = if (enabled) UIActionBrush else UIActionBrushDisabled,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(
                text = "测试并保存",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else UIFriendBarItemSubtitleColor
            )
        }
    }
}

/** 禁用态用一个低饱和的灰蓝渐变，和可用态保持同样的形，只是不发光。 */
private val UIActionBrushDisabled = Brush.linearGradient(
    listOf(UIFriendBarGray, UIFriendBarGray)
)
