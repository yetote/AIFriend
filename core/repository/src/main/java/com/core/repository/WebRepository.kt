package com.core.repository

import com.core.network.KtorClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class WebRepository {
    val httpClient = KtorClient.instance.client

    companion object {
        val instance by lazy {
            WebRepository()
        }
    }

    suspend inline fun <reified T> chat(config: ApiConfig, body: Any): Result<T> =
        runCatching {
            httpClient.post(config.chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(body)
                config.headers.forEach { (key, value) ->
                    header(key, value)
                }
            }.body()
        }

    suspend inline fun <reified T> getModel(config: ApiConfig,): Result<T> = runCatching {
        httpClient.get("${config.baseurl}/models") {
            contentType(ContentType.Application.Json)
            config.headers.forEach { (key, value) ->
                header(key, value)
            }
        }.body()
    }
}