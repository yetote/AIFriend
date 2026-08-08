package com.core.network

sealed class DomainException(override val message: String) : Exception(message) {
    object UnauthorizedException : DomainException("登录已过期或未授权，请重新登录")
    object NotFoundException : DomainException("请求的资源不存在")
    class ServerException(code: Int) : DomainException("服务器开小差了 ($code)")
    class NetworkException(e: Throwable) : DomainException("网络连接异常，请检查网络")


}

fun getReadableMessage(throwable: Throwable): String {
    return when (throwable) {
        is DomainException.UnauthorizedException -> "登录已过期，请重新登录"
        is DomainException.NotFoundException -> "您访问的内容不存在或已被删除"
        is DomainException.ServerException -> "服务器繁忙，请稍后再试"
        is DomainException.NetworkException -> "网络开小差了，请检查网络连接"
        else -> "发生未知错误，请稍重试"
    }
}