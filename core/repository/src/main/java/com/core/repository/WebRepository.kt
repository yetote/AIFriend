package com.core.repository

import com.core.network.KtorClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WebRepository {
    val httpClient = KtorClient.instance

    companion object {
        val instance by lazy {
            WebRepository()
        }
    }

    suspend inline fun <reified T> chat(config: ApiConfig, body: Any): Result<T> = runCatching {
        httpClient.apiPost<T>(config.chatUrl, body, config.headers)
    }

    suspend inline fun <reified T> getModel(config: ApiConfig): Result<T> = runCatching {
        httpClient.apiGet<T>("${config.baseUrl}/models", config.headers)
    }

    /**
     * 流式对话：把响应体按行交给 [onLine]。
     *
     * 不做 `Result` 包装。流式失败可能发生在已经吐出半段之后，
     * 那时候「成功/失败」的二分已经没有意义了——上层需要的是异常在哪里中断的，
     * 好把已经收到的内容留下并给出重试入口。
     */
    suspend fun streamChat(config: ApiConfig, body: Any, onLine: suspend (String) -> Unit) {
        httpClient.streamPost(config.chatUrl, body, config.headers, onLine)
    }

    /** 清理底层的空闲连接池，超时后避免复用已经半开僵死的 TCP/HTTP2 连接。 */
    fun evictIdleConnections() {
        httpClient.evictIdleConnections()
    }

    /**
     * 抓一个网页的原始文本。
     *
     * 走 [KtorClient.getText] 而不是 `apiGet<String>`：后者会经过
     * ContentNegotiation 把响应体当 JSON 反序列化，页面 HTML 会直接解析失败。
     */
    suspend fun getPage(url: String): Result<String> = runCatching {
        httpClient.getText(url)
    }

    /** 短超时拉取原始文本，专供中转站名称探测等尽力而为的接口使用。 */
    suspend fun getTextShort(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 2_500L,
    ): Result<String> = runCatching {
        httpClient.getTextShort(url, headers, timeoutMs)
    }
}