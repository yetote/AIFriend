package com.feature.home.route

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.repository.settings.AIProviderCatalog
import com.core.repository.settings.TTSProviderCatalog
import com.feature.home.vm.SettingsViewModel
import com.ui.home.UIModelSettingsPage
import com.ui.home.UISettingsHomePage
import com.ui.home.UITtsSettingsPage

/** 设置页内部的两级导航：首页只放入口，配置各在二级页。 */
private enum class SettingsPage { Home, Model, Tts }

/**
 * 设置页的路由壳。
 *
 * 只做三件事：把 ViewModel 的状态摊平成 UI 需要的形状、
 * 管理「首页 → 二级页」的内部导航，以及把「去申请 Key」翻译成外部浏览器跳转
 * （这部分需要 Context，不适合放在 ViewModel 里）。
 */
@Composable
fun SettingsRoute(
    onExit: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by remember { mutableStateOf(SettingsPage.Home) }

    // 设置页自己接管返回键：二级页先回首页，首页才退出设置。
    // HomeRoute 的外层 BackHandler 在设置页时已让位，这里唯一生效。
    BackHandler {
        if (page == SettingsPage.Home) onExit() else page = SettingsPage.Home
    }

    when (page) {
        SettingsPage.Home -> UISettingsHomePage(
            state = state,
            onBack = onExit,
            onOpenModel = { page = SettingsPage.Model },
            onOpenTts = { page = SettingsPage.Tts },
        )

        SettingsPage.Model -> UIModelSettingsPage(
            state = state,
            onBack = { page = SettingsPage.Home },
            onProviderSelect = viewModel::selectProvider,
            onKeyChange = viewModel::setKeyInput,
            onBaseUrlChange = viewModel::setBaseUrlInput,
            onModelSelect = viewModel::selectModel,
            onSave = viewModel::verifyAndSave,
            onClearKey = viewModel::clearKey,
            onRefreshPrice = viewModel::refreshPrice,
            onOpenConsole = { providerId ->
                val url = AIProviderCatalog.byId(providerId)?.consoleUrl.orEmpty()
                openUrl(context, url)
            }
        )

        SettingsPage.Tts -> UITtsSettingsPage(
            state = state.tts,
            onBack = { page = SettingsPage.Home },
            onProviderSelect = viewModel::selectTtsProvider,
            onKeyChange = viewModel::setTtsKeyInput,
            onSecretChange = viewModel::setTtsSecretInput,
            onAppIdChange = viewModel::setTtsAppIdInput,
            onBaseUrlChange = viewModel::setTtsBaseUrlInput,
            onToggleVisible = viewModel::toggleTtsKeyVisible,
            onTestAndSave = viewModel::testAndSaveTtsCredential,
            onClearKey = viewModel::clearTtsKey,
            onOpenConsole = { providerId ->
                val url = TTSProviderCatalog.byId(providerId)?.consoleUrl.orEmpty()
                openUrl(context, url)
            }
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isNotBlank()) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
