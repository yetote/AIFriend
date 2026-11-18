package com.core.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.statement.bodyAsText
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import java.net.SocketTimeoutException

class KtorClient {
    // 必须用 OkHttp 引擎：Android 引擎（HttpURLConnection）不支持 WebSocket，
    // 讯飞在线合成的 wss 流式接口会直接抛 "Engine doesn't support WebSocketCapability"。
    val client = HttpClient(OkHttp) {
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
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
        // 讯飞在线合成用 WebSocket；默认 ping/pong 关掉，避免长时间朗读被服务端超时踢掉。
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("KtorNetwork", message)
                }
            }
            // 不要用 LogLevel.ALL：它会把 Authorization 请求头原样打进 logcat
            level = LogLevel.INFO
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                when (cause) {
                    is ClientRequestException -> when (cause.response.status) {
                        HttpStatusCode.Forbidden, HttpStatusCode.Unauthorized ->
                            throw DomainException.UnauthorizedException

                        HttpStatusCode.NotFound ->
                            throw DomainException.NotFoundException

                        else ->
                            throw DomainException.ServerException(cause.response.status.value)
                    }

                    is ServerResponseException ->
                        throw DomainException.ServerException(cause.response.status.value)

                    is RedirectResponseException ->
                        throw DomainException.ServerException(cause.response.status.value)

                    is ConnectTimeoutException,
                    is SocketTimeoutException,
                    is HttpRequestTimeoutException ->
                        throw DomainException.NetworkException(cause)

                    else -> Unit
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

    /**
     * 流式 POST：按行读响应体，读一行回调一行。
     *
     * 不能用 `post(...).body()`：SSE 的响应要等模型把话说完才算「接收完」，
     * 那样流式就退化成了普通请求。`preparePost` + `execute` 才能边到边读。
     *
     * 超时要单独放宽。全局 120 秒是给「一问一答」定的，一段长回复加上思考过程
     * 很容易超过它，用同一个值会把话从中间掐断——而且断开时用户看到的是半句话，
     * 比直接报错更让人困惑。socket 超时仍保留 120 秒：真的卡住不吐字时照样要断。
     *
     * 回调是 suspend 的，调用方可以在里面往 Flow 里 `send`。
     * 这里不吞异常：HTTP 状态错误由 [client] 的校验器统一翻译，读取中断直接往上抛，
     * 上层据此区分「说完了」和「被切断了」。
     */
    suspend fun streamPost(
        url: String,
        body: Any,
        headers: Map<String, String>,
        onLine: suspend (String) -> Unit
    ) {
        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
            headers.forEach { (key, value) ->
                header(key, value)
            }
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 120_000
            }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (true) {
                // 行尾可能是 \n 也可能是 \r\n，readUTF8Line 两种都吃得下
                val line = channel.readUTF8Line() ?: break
                onLine(line)
            }
        }
    }

    /**
     * 取原始文本，用于抓网页。
     *
     * 不能走 `apiGet<String>`：ContentNegotiation 会按 JSON 解析响应体，
     * HTML 一进来就抛序列化异常。这里用 `bodyAsText()` 跳过内容协商。
     *
     * 请求头刻意留空：硅基流动的定价页对普通 GET 就返回完整数据，
     * UA 是浏览器还是 Ktor 拿到的结果完全一致，不需要伪装。
     */
    suspend fun getText(url: String): String = client.get(url).bodyAsText()

}
