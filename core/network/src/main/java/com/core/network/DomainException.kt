package com.core.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException

sealed class DomainException(override val message: String) : Exception(message) {
    object UnauthorizedException : DomainException("API Key 无效或已过期，请检查配置")
    object NotFoundException : DomainException("接口或模型不存在（404），请检查模型 ID 或开通状态")
    class ServerException(val code: Int) : DomainException("请求失败（$code）")
    class NetworkException(e: Throwable) : DomainException("网络连接异常，请检查网络连接")


}

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
        is DomainException.NetworkException -> "网络开小差了，请检查网络连接"
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException ->
            // 说清楚是「模型那边没回话」，而不是「网络坏了」——
            // 开了深度思考的模型光思考就要一两分钟，这时候网络好得很，
            // 但用户看到「网络连接超时」只会去折腾自己的网。
            "模型响应超时（开了深度思考时最容易碰上）。可以稍后重试，" +
                "或者先关掉深度思考、换个更快的模型"
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "发生未知错误"
    }
}
