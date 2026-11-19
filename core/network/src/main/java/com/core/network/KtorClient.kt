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
            // 120 秒是给「一问一答」定的，但开了深度思考的模型光思考就要一两分钟，
            // 再算上输入长、排队慢，120 秒很容易被打穿——用户看到的是「网络连接超时」，
            // 而网络其实好得很。放到 180 秒：够深度思考跑完，又不至于无底洞。
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
        // 讯飞在线合成用 WebSocket；默认 ping/pong 关掉，避免长时间朗读被服务端超时踢掉。
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(LOG_TAG, redact(message))
                }
            }
            // 全量日志（URL / 方法 / 头 / 请求体 / 响应体）。
            // 判 4xx 时只看一行状态码根本不够——`{"code":45000010,...}` 这种原因全在响应体里。
            // 以前写 INFO 是为了不把 Authorization 打进 logcat，现在改由 [redact] 统一掩码：
            // Key 只留头尾（够核对「填的是新版 UUID 还是旧版 Token」），超长内容截断。
            // 注意：流式请求（SSE）要单独降级，见 [streamPost]——ALL 会把整段响应读完才打，
            // 等于让流式退化成一次性。
            level = LogLevel.ALL
            // 流式请求排除在外，见 [SkipCallLogging]
            filter { !it.attributes.contains(SkipCallLogging) }
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
            // 这个请求不能被 Logging 插件接管：BODY/ALL 级别下插件会把响应体读完之后
            // 才写日志，等于等模型说完才把第一行交给我们——流式当场退化成一次性。
            // 首尾状态由下面自己打。
            attributes.put(SkipCallLogging, Unit)
            timeout {
                requestTimeoutMillis = 600_000
                // socket 超时是「两个数据包之间最长等多久」。深度思考模型在流式下
                // 常常先沉默着思考完再吐第一个字，这段沉默也算「没吐字」——
                // 原来 120 秒会把还在思考的请求直接掐断，用户看到的是半句话。
                socketTimeoutMillis = 180_000
            }
        }.execute { response ->
            Log.d(LOG_TAG, "SSE 开始 ${response.status.value} $url")
            val channel = response.bodyAsChannel()
            var lines = 0
            while (true) {
                // 行尾可能是 \n 也可能是 \r\n，readUTF8Line 两种都吃得下
                val line = channel.readUTF8Line() ?: break
                lines++
                onLine(line)
            }
            Log.d(LOG_TAG, "SSE 结束 共 $lines 行 $url")
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

private const val LOG_TAG = "KtorNetwork"

/**
 * 打在这个 attribute 上的请求不进 [Logging] 插件。
 *
 * 只给流式（SSE）用：插件的日志是在响应读完之后才写的，对一条要读几分钟的流来说
 * 等于把「边到边显示」变成「等全部说完再显示」。
 */
internal val SkipCallLogging = io.ktor.util.AttributeKey<Unit>("AIFriendSkipCallLogging")

/** logcat 单条消息上限约 4KB，超了会被切成乱序多行，反而更难读。 */
private const val LOG_MAX_CHARS = 3_500

/**
 * 日志脱敏 + 截断，[Logging] 的输出全部经过这里。
 *
 * 开 [LogLevel.ALL] 之后请求头会原样进 logcat，而 TTS 的 Key / Secret、模型的
 * Authorization 都在头里。不能靠 `sanitizeHeader` 兜底：它只认 Authorization 一个名字，
 * 火山的 `X-Api-Key`、腾讯的 `X-TC-*` 都会漏出去。
 *
 * 掩码留头尾 4 位是为了排查「Key 是哪种形态」——火山新版是 UUID、旧版不是，
 * 混着填会 401 且报错看不出哪一步错（见 TTSProtocols 的注释）。全打 `***` 就没法分辨了。
 */
internal fun redact(message: String): String {
    var out = SECRET_PATTERN.replace(message) { m ->
        m.groupValues[1] + m.groupValues[2] + maskTail(m.groupValues[3])
    }
    // Authorization 单独走一条：`Bearer xxx` 的值里有空格，通用规则会在 Bearer 处断掉，
    // 而腾讯的 `TC3-HMAC-SHA256 Credential=...` 同理。先认掉可选的前缀再打码。
    out = AUTH_PATTERN.replace(out) { m ->
        val prefix = m.groupValues[1]
        val raw = m.groupValues[2]
        val bearer = raw.startsWith("Bearer ", ignoreCase = true)
        val token = if (bearer) raw.substring(7) else raw
        prefix + (if (bearer) "Bearer " else "") + maskTail(token)
    }
    return if (out.length <= LOG_MAX_CHARS) {
        out
    } else {
        out.take(LOG_MAX_CHARS) + "…（已截断 ${out.length - LOG_MAX_CHARS} 字符，完整内容见服务商控制台日志）"
    }
}

private fun maskTail(value: String): String = when {
    value.length <= 8 -> "***"
    else -> value.take(4) + "…" + value.takeLast(4)
}

/** `Authorization: Bearer xxx` / `authorization=<base64>`，值里允许一个 Bearer 前缀。 */
private val AUTH_PATTERN =
    Regex("""(?i)(authorization\s*[:=]\s*"?)((?:bearer\s+)?[^\s"&,]+)""")

/**
 * 通用敏感字段。值里排除空白、引号、`&`、`,`、`}`：
 * 前三个是为了不把后面的 query 参数一起吃进掩码，后两个是为了 JSON 里只替换到值的结尾。
 * authorization 不在这里——它单独处理，见 [AUTH_PATTERN]。
 */
private val SECRET_PATTERN = Regex(
    """(?i)\b(x-api-key|x-api-access-key|x-api-app-id|api[_-]?key|api[_-]?secret|access[_-]?key)\b("?\s*[:=]\s*"?)([^\s"}&,]+)"""
)
