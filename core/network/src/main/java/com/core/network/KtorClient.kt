package com.core.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException
import kotlin.collections.component1
import kotlin.collections.component2

class KtorClient {
    val client = HttpClient(Android) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
                prettyPrint = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KtorNetwork", message)
                }
            }
            level = LogLevel.ALL
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause is ClientRequestException) {
                    when (cause.response.status) {
                        HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized ->
                            throw DomainException.UnauthorizedException

                        HttpStatusCode.NotFound ->
                            throw DomainException.NotFoundException

                        else ->
                            throw DomainException.ServerException(cause.response.status.value)
                    }
                } else if (cause is ConnectTimeoutException || cause is SocketTimeoutException) {
                    throw DomainException.NetworkException(cause)
                }
            }
        }
    }

    companion object {
        val instance: KtorClient by lazy { KtorClient() }
    }

    suspend inline fun <reified T> apiGet(url: String, headers: Map<String, String>): T =
        client.get(url) {
            contentType(ContentType.Application.Json)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }.body()


    suspend inline fun <reified T> apiPost(url: String, body: Any, headers: Map<String, String>): T =
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }.body()

}
