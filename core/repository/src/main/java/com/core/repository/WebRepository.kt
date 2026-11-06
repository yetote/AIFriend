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
     * 抓一个网页的原始文本。
     *
     * 走 [KtorClient.getText] 而不是 `apiGet<String>`：后者会经过
     * ContentNegotiation 把响应体当 JSON 反序列化，页面 HTML 会直接解析失败。
     */
    suspend fun getPage(url: String): Result<String> = runCatching {
        httpClient.getText(url)
    }
}