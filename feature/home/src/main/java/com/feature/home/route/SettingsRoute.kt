package com.feature.home.route

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.core.repository.settings.AIProviderCatalog
import com.feature.home.vm.SettingsViewModel
import com.ui.home.UIModelSettingsPage

/**
 * 设置页的路由壳。
 *
 * 只做两件事：把 ViewModel 的状态摊平成 UI 需要的形状，
 * 以及把「去申请 Key」翻译成一次外部浏览器跳转 ——
 * 这部分需要 Context，不适合放在 ViewModel 里。
 */
@Composable
fun SettingsRoute(
    onExit: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    UIModelSettingsPage(
        state = state,
        onBack = onExit,
        onProviderSelect = viewModel::selectProvider,
        onKeyChange = viewModel::setKeyInput,
        onBaseUrlChange = viewModel::setBaseUrlInput,
        onModelSelect = viewModel::selectModel,
        onSave = viewModel::verifyAndSave,
        onClearKey = viewModel::clearKey,
        onRefreshPrice = viewModel::refreshPrice,
        onOpenConsole = { providerId ->
            val url = AIProviderCatalog.byId(providerId)?.consoleUrl.orEmpty()
            if (url.isNotBlank()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    )
}
