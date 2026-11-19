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

/**
 * 全应用的时间上限，集中在这里改，别散落在各个调用点。
 *
 * 为什么一次性请求要给到 8 分钟：开了深度思考的模型收到长提示词后，会先闷头想
 * 一两分钟才吐第一个字，而且这期间 **一个字节都不会发**。并发跑满时还要排队。
 * 3 分钟那档在实测里是经常被打穿的——用户看到的是「网络异常」，网络却好得很，
 * 真正的死因是「思考时间算进了超时」。
 *
 * 流式请求要分开看：
 * - 整段时长（[STREAM_REQUEST_MS]）可以很长，一场发言本来就没有上限；
 * - 静默时长（[STREAM_IDLE_MS]）才是「死了没」的判断依据。会边思考边吐
 *   reasoning 的厂商在思考期间一直有包，只有真的断了才会静默这么久。
 */
object NetTimeouts {
    /** 建连。这一条保持不变：连不上就是连不上，等再久也没意义。 */
    const val CONNECT_MS = 15_000L

    /**
     * 一次性请求（`chat`、结构化决策、拉模型列表）：从发出到收完的总时长。
     *
     * 一次性请求在服务端出结果之前是彻底静默的，所以这里的「总时长」同时也是
     * socket 的静默上限——两个值必须一起给，只放宽其中一个等于没放宽。
     */
    const val ONESHOT_MS = 480_000L

    /** 流式请求的总时长：一场发言、一次长回复都不该被这个值掐断。 */
    const val STREAM_REQUEST_MS = 1_800_000L

    /**
     * 流式请求两次数据之间最多能静默多久。
     *
     * 比一次性请求小得多是有道理的：流式本来就有包在走，思考期会持续吐 reasoning，
     * 真的五分钟一个字节都没有，基本可以认定对面已经断了。
     */
    const val STREAM_IDLE_MS = 300_000L
}

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
            connectTimeoutMillis = NetTimeouts.CONNECT_MS
            // 这两个值必须成对放宽：一次性请求在结果出来之前一个字节都不发，
            // 所以 socket 的静默上限实际就是整段的等待上限。见 [NetTimeouts]。
            requestTimeoutMillis = NetTimeouts.ONESHOT_MS
            socketTimeoutMillis = NetTimeouts.ONESHOT_MS
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
     * 超时单独放宽，见 [NetTimeouts]：总时长按「一场发言」给，
     * 静默时长才是判断「对面是不是死了」的依据。用一次性请求那套 3 分钟，
     * 会把还在思考的请求从中间掐断——而断开时用户看到的是半句话，比报错更让人困惑。
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
                requestTimeoutMillis = NetTimeouts.STREAM_REQUEST_MS
                // socket 超时是「两个数据包之间最长等多久」。深度思考模型在流式下
                // 常常先沉默着思考完再吐第一个字，这段沉默也算「没吐字」——
                // 原来 120 秒会把还在思考的请求直接掐断，用户看到的是半句话。
                socketTimeoutMillis = NetTimeouts.STREAM_IDLE_MS
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
