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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 语音朗读（TTS）二级页。
 *
 * 和模型 Key 分开独立成页：语音权限通常要在服务商控制台单独开通，
 * 混在模型 Key 那一页里，用户会以为两把通用，出问题极难自查。
 * 「测试并保存」会真的合成一句并播放——配上几秒就能知道 Key 和音色能不能用，
 * 不用等到开狼人杀才发现没声音。
 */
@Composable
fun UITtsSettingsPage(
    state: UITTSState,
    onBack: () -> Unit,
    onProviderSelect: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onAppIdChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    onTestAndSave: () -> Unit,
    onClearKey: () -> Unit,
    onOpenConsole: (String) -> Unit,
) {
    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageInsetsBottom()
        ) {
            UITopBar(title = "语音朗读（TTS）", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "这里只管凭证和能不能接通：会真的合成一句试给你听。" +
                        "语音权限通常要在服务商控制台单独开通。音色是每个角色的属性，" +
                        "在创建 AI 好友时挑、可逐个试听。",
                    fontSize = 12.sp,
                    color = UIFriendBarItemMetaColor,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(14.dp))
                TtsSection(
                    state = state,
                    onProviderSelect = onProviderSelect,
                    onKeyChange = onKeyChange,
                    onSecretChange = onSecretChange,
                    onAppIdChange = onAppIdChange,
                    onBaseUrlChange = onBaseUrlChange,
                    onToggleVisible = onToggleVisible,
                    onTestAndSave = onTestAndSave,
                    onClearKey = onClearKey,
                    onOpenConsole = onOpenConsole,
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun TtsSection(
    state: UITTSState,
    onProviderSelect: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSecretChange: (String) -> Unit,
    onAppIdChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    onTestAndSave: () -> Unit,
    onClearKey: () -> Unit,
    onOpenConsole: (String) -> Unit,
) {
    SectionLabel("选择服务商")
    Spacer(Modifier.height(8.dp))

    state.providers.forEach { provider ->
        val selected = provider.id == state.selectedId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiCard(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) UIFriendBarTagBgBlue.copy(alpha = 0.7f) else UIGlassSurfaceStrong,
                    borderColor = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.55f) else UIGlassStroke,
                    elevation = 2.dp
                )
                .clickable { onProviderSelect(provider.id) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = UIFriendBarTitleColor
                    )
                    if (provider.hasKey) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "已配置",
                            fontSize = 10.sp,
                            color = UIFriendBarTagTextBlue,
                            modifier = Modifier
                                .background(UIFriendBarTagBgBlue, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = provider.description,
                    fontSize = 11.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
            Spacer(Modifier.width(8.dp))
            SelectionDot(selected)
        }
        Spacer(Modifier.height(8.dp))
    }

    val provider = state.selected
    if (provider != null) {
        Spacer(Modifier.height(4.dp))
        SectionLabel(provider.keyLabel)
        Spacer(Modifier.height(8.dp))
        TtsKeyField(
            value = state.keyInput,
            onValueChange = onKeyChange,
            visible = state.keyVisible,
            onToggleVisible = onToggleVisible,
            placeholder = if (provider.hasKey) {
                "已保存 ${provider.maskedKey}，留空则保持不变"
            } else {
                "粘贴 ${provider.keyLabel}"
            }
        )

        if (provider.appIdLabel != null) {
            Spacer(Modifier.height(10.dp))
            SectionLabel(provider.appIdLabel)
            Spacer(Modifier.height(8.dp))
            TtsKeyField(
                value = state.appIdInput,
                onValueChange = onAppIdChange,
                visible = true,
                onToggleVisible = {},
                placeholder = if (provider.appIdOptional) {
                    "用新版 API Key 时留空即可"
                } else {
                    "填写 ${provider.appIdLabel}"
                },
                password = false
            )
            if (provider.appIdOptional) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "新版控制台只有一把 API Key，这里留空；" +
                        "旧版控制台才需要 AppID + Access Token 两个都填。" +
                        "填了也不会错，会依次试两套鉴权。",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = UIFriendBarItemMetaColor
                )
            }
        }

        if (provider.secretLabel != null) {
            Spacer(Modifier.height(10.dp))
            SectionLabel(provider.secretLabel)
            Spacer(Modifier.height(8.dp))
            TtsKeyField(
                value = state.secretInput,
                onValueChange = onSecretChange,
                visible = state.keyVisible,
                onToggleVisible = onToggleVisible,
                placeholder = if (provider.hasSecret) {
                    "已保存 ${provider.secretLabel}，留空保持不变"
                } else {
                    "填写 ${provider.secretLabel}"
                }
            )
        }

        if (provider.editableBaseUrl) {
            Spacer(Modifier.height(10.dp))
            SectionLabel("Base URL")
            Spacer(Modifier.height(8.dp))
            TtsKeyField(
                value = state.baseUrlInput,
                onValueChange = onBaseUrlChange,
                visible = true,
                onToggleVisible = {},
                placeholder = "https://your-host/v1",
                password = false
            )
        }

        Spacer(Modifier.height(10.dp))
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
            if (provider.name.contains("MiniMax")) {
                Text(
                    text = "去申请语音权限 →",
                    fontSize = 12.sp,
                    color = UIFriendBarCreateButtonText,
                    modifier = Modifier.clickable { onOpenConsole(provider.id) }
                )
            } else if (provider.description.isNotBlank() && provider.name != "自定义（MiniMax 兼容）") {
                Text(
                    text = "去控制台 →",
                    fontSize = 12.sp,
                    color = UIFriendBarCreateButtonText,
                    modifier = Modifier.clickable { onOpenConsole(provider.id) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        TtsSaveButton(
            enabled = state.canSave,
            loading = state.testing,
            onClick = onTestAndSave
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "用该服务的默认音色合成一句并播放，成功才保存；不消耗对话额度。",
            fontSize = 11.sp,
            color = UIFriendBarItemMetaColor
        )

        state.hint?.let { hint ->
            Spacer(Modifier.height(8.dp))
            Text(text = hint, fontSize = 12.sp, color = UIFriendBarItemMetaColor)
        }
        state.testResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            TtsTestBanner(result)
        }
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
private fun TtsTestBanner(result: UITTSTestResult) {
    val (text, color) = when (result) {
        // 只报「出声了」这件事本身：设置页验的是凭证 + 权限，
        // 具体音色能不能用要到建角色页挑的时候才验（那时才知道用的是哪个）
        is UITTSTestResult.Success -> "合成成功（${result.voiceLabel}），凭证与语音权限都通了" to UIFriendBarOnlineGreen
        is UITTSTestResult.Failure -> result.message to UIFriendBarRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusControl))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).background(color = color, shape = CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(text = text, fontSize = 12.sp, color = UIFriendBarTitleColor, lineHeight = 17.sp)
    }
}

/** TTS 用的简单输入框，和模型页的 KeyField 同款但自包含（不耦合那边的 state）。 */
@Composable
private fun TtsKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    placeholder: String,
    password: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(shape = RoundedCornerShape(UIRadiusControl))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = UIFriendBarTitleColor),
            cursorBrush = SolidColor(UIFriendBarCreateButtonText),
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, fontSize = 13.sp, color = UIFriendBarItemMetaColor)
                }
                inner()
            }
        )
        if (password) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (visible) "隐藏" else "显示",
                fontSize = 12.sp,
                color = UIFriendBarTagTextBlue,
                modifier = Modifier.clickable { onToggleVisible() }
            )
        }
    }
}

@Composable
private fun TtsSaveButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(
                brush = if (enabled) UIActionBrush else ttsActionBrushDisabled(),
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
                text = "测试并保存语音凭证",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else UIFriendBarItemSubtitleColor
            )
        }
    }
}

/** 禁用态用一个低饱和的灰蓝渐变，和模型页保持同样的形。 */
@Composable
private fun ttsActionBrushDisabled(): Brush = Brush.linearGradient(
    listOf(UIFriendBarGray, UIFriendBarGray)
)
