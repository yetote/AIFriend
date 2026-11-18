package com.feature.home

import com.ui.home.UITTSProviderOption
import com.ui.home.UITTSState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设置页「测试并保存」的启用条件：已保存的 Secret/AppID 复用时不能被误判为没填。 */
class TtsCredentialValidationTest {

    private fun state(provider: UITTSProviderOption, testing: Boolean = false) = UITTSState(
        providers = listOf(provider),
        selectedId = provider.id,
        testing = testing,
    )

    @Test
    fun `普通服务商已保存 Key 时可以留空直接测试`() {
        val provider = UITTSProviderOption(id = "minimax", name = "MiniMax", description = "", maskedKey = "abcd••••wxyz", hasKey = true)
        assertTrue(state(provider).canSave)
    }

    @Test
    fun `腾讯云已保存 SecretKey 时不用重填`() {
        val provider = UITTSProviderOption(
            id = "tencent", name = "腾讯云", description = "", maskedKey = "abcd••••wxyz", hasKey = true,
            secretLabel = "SecretKey", hasSecret = true,
        )
        assertTrue(state(provider).canSave)
        assertFalse(state(provider.copy(hasSecret = false)).canSave)
    }

    @Test
    fun `讯飞已保存 Secret 与 AppID 时不用重填`() {
        val provider = UITTSProviderOption(
            id = "iflytek", name = "讯飞", description = "", maskedKey = "abcd••••wxyz", hasKey = true,
            secretLabel = "API Secret", hasSecret = true,
            appIdLabel = "App ID", hasAppId = true,
        )
        assertTrue(state(provider).canSave)
        // 两个第二凭证缺任意一个都不能测，否则真机只会报鉴权失败。
        assertFalse(state(provider.copy(hasSecret = false)).canSave)
        assertFalse(state(provider.copy(hasAppId = false)).canSave)
    }

    @Test
    fun `试听进行中不能连点`() {
        val provider = UITTSProviderOption(id = "minimax", name = "MiniMax", description = "", maskedKey = "abcd••••wxyz", hasKey = true)
        assertFalse(state(provider, testing = true).canSave)
    }
}
