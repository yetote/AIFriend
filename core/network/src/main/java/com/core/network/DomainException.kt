package com.core.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException

sealed class DomainException(override val message: String) : Exception(message) {
    object UnauthorizedException : DomainException("API Key 无效或已过期，请检查配置")
    object NotFoundException : DomainException("请求的接口不存在（404），请检查请求地址")
    class ServerException(val code: Int) : DomainException("请求失败（$code）")
    class NetworkException(e: Throwable) : DomainException("网络连接异常，请检查网络连接")


}

fun getReadableMessage(throwable: Throwable): String {
    return when (throwable) {
        is DomainException.UnauthorizedException -> "API Key 无效或已过期，请检查配置"
        is DomainException.NotFoundException -> "请求的接口不存在（404），请检查请求地址"
        is DomainException.ServerException -> when (throwable.code) {
            400 -> "请求参数有误（400）"
            401, 403 -> "API Key 无效或无权限，请检查配置"
            402 -> "账户余额不足，请充值后再试"
            404 -> "请求的接口不存在（404），请检查请求地址"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务端异常（${throwable.code}），请稍后再试"
            else -> "请求失败（${throwable.code}）"
        }
        is DomainException.NetworkException -> "网络开小差了，请检查网络连接"
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is HttpRequestTimeoutException -> "网络连接超时，请稍后重试"
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "发生未知错误"
    }
}
