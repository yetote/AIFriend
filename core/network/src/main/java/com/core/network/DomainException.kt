package com.core.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException

sealed class DomainException(override val message: String) : Exception(message) {
    object UnauthorizedException : DomainException("API Key 无效或已过期，请检查配置")
    object NotFoundException : DomainException("接口或模型不存在（404），请检查模型 ID 或开通状态")
    class ServerException(val code: Int) : DomainException("请求失败（$code）")

    /**
     * 传输层失败。
     *
     * **超时和断网要分开说**：在 socket 这一层它们长得一样（都是 IO 异常），
     * 但用户该做的事完全不同——「网络开了小差」会让人去折腾路由器，
     * 而实际上多半是他自己开的深度思考把等待额度吃光了，网络好得很。
     * 判据用类名（各家超时类不统一，但都带 Timeout），见 [isTimeoutLike]。
     *
     * 文案写在这里而不是只写在 [getReadableMessage] 里：界面有好几处直接把
     * `t.message` 丢给用户看（比如狼人杀的 `_error`），走不到那个映射函数。
     */
    class NetworkException(e: Throwable) : DomainException(
        if (e.isTimeoutLike()) TIMEOUT_HINT else "网络开小差了，请检查网络连接"
    )
}

/** 超时 / 断连共用的一句解释。 */
private const val TIMEOUT_HINT =
    "模型响应超时（开了深度思考时最容易碰上）。可以稍后重试，或者先关掉深度思考、换个更快的模型"

/**
 * 这次传输失败是不是「等太久了」。
 *
 * 顺着 cause 链找名字里带 Timeout 的类：Ktor 的 SocketTimeout / ConnectTimeout /
 * HttpRequestTimeout、OkHttp 的 InterruptedIOException 的包装、协程的 TimeoutCancellation
 * 都算。按名字判断是因为不同引擎抛的不是同一个类型。
 */
private fun Throwable.isTimeoutLike(): Boolean =
    generateSequence(this) { it.cause }
        .any { cause -> cause::class.simpleName?.contains("Timeout", ignoreCase = true) == true }

/**
 * 404 时把「是哪个模型」补进去。
 *
 * 「接口或模型不存在」在一个模型时够用，但用户手上可能是十几个角色，
 * 光说 404 等于让他一个个去试。而且这类 404 大半不是 ID 写错：
 * 方舟、百炼、TokenHub 的 `/models` 会把代理来的别家模型也列出来，
 * 那些模型要在控制台单独「开通」，没开通就直接 404（方舟报
 * InvalidEndpointOrModel.NotFound，看着像模型根本不存在）。
 * 点名到模型，用户才知道该去开通，还是该换一个。
 */
fun withModelHint(message: String, brand: String?, model: String?): String {
    if (!message.contains("404")) return message
    val modelId = model?.trim().orEmpty()
    if (modelId.isBlank()) return message
    val owner = brand?.trim()?.takeIf { it.isNotBlank() }?.let { "$it 的" }.orEmpty()
    return "$message\n${owner}模型「$modelId」调不通。这类平台要先在控制台「开通管理」里开通该模型，" +
        "没开通就是这个错；也可以在角色编辑页换一个模型。"
}

fun getReadableMessage(throwable: Throwable): String {
    return when (throwable) {
        is DomainException.UnauthorizedException -> "API Key 无效或已过期，请检查配置"
        is DomainException.NotFoundException -> "接口或模型不存在（404），请检查模型 ID 或开通状态"
        is DomainException.ServerException -> when (throwable.code) {
            400 -> "请求参数有误（400）"
            401, 403 -> "API Key 无效或无权限，请检查配置"
            402 -> "账户余额不足，请充值后再试"
            404 -> "接口或模型不存在（404），请检查模型 ID 或开通状态"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务端异常（${throwable.code}），请稍后再试"
            else -> "请求失败（${throwable.code}）"
        }
        is DomainException.NetworkException -> throwable.message
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException -> TIMEOUT_HINT
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "发生未知错误"
    }
}
