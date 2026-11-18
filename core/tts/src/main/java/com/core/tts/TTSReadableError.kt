package com.core.tts

import com.core.network.getReadableMessage

/**
 * 把试听失败翻成一句用户能照着改的话。
 *
 * 设置页（验凭证）和建角色页（挑音色）都要报同一类错误，文案不能一边说
 * 「Key 不对」另一边把原始堆栈摔给用户——所以放在这里共用。
 *
 * 最要紧的是把「401/403/签名」和「音色没开通」分开：前者要改配置，
 * 后者换一个音色就行，混成一句「试听失败」等于什么都没说。
 */
object TTSReadableError {

    fun of(throwable: Throwable, providerName: String): String {
        val message = runCatching { getReadableMessage(throwable) }.getOrDefault("")
        val lower = message.lowercase()
        // 协议层已经诊断过的（火山会把两套鉴权的失败原因都带上、带真实错误码），
        // 原样给用户——再套一层「请确认 Key 填对」只会把这唯一有用的信息盖掉
        val diagnosed = message.contains("两套鉴权") || message.contains("火山合成失败")
        return when {
            diagnosed -> message
            lower.contains("401") || lower.contains("403") ||
                lower.contains("unauthorized") || lower.contains("invalid") ||
                lower.contains("签名") || lower.contains("鉴权") ->
                "$providerName 拒绝了这次请求。请确认 Key / AppID / Secret 填对，且账号已开通语音合成权限。"
            // 服务端明确说「这个音色不可用 / 没开通」，这是换音色能解决的
            lower.contains("音色") || lower.contains("voice") || lower.contains("resource") ->
                "$providerName 用这个音色合成失败：$message\n换成别的音色试试，或在控制台开通它。"
            lower.contains("合成失败") || lower.contains("播放失败") || lower.contains("空音频") -> message
            lower.contains("timeout") || lower.contains("超时") ->
                "连接超时。检查网络，或该语音接口在当前网络下不可达。"
            lower.contains("余额") || lower.contains("quota") || lower.contains("欠费") ->
                "$providerName 的语音合成额度不足：$message"
            else -> message.ifBlank { "试听失败，请稍后重试" }
        }
    }
}
