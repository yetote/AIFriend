package com.core.repository

import com.core.network.KtorClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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

    suspend inline fun <reified T> chat(url: String, body: Any, apikey: String): Result<T> =
        runCatching {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                header("x-goog-api-key", apikey)
                header("content-type", "application/json")
            }.body()
        }
}