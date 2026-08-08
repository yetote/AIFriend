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
}