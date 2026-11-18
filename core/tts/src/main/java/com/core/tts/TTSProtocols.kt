package com.core.tts

import android.util.Base64
import com.core.network.KtorClient
import com.core.repository.settings.TTSProtocol
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readRemaining
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.io.readByteArray
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 各家 TTS 协议的请求构造与响应解析。
 *
 * 刻意做成「纯逻辑 + 一次网络调用」，方便单测签名算法。
 * 统一产出音频字节 [ByteArray]，调用方只管写文件播放。
 * 任何失败都抛 [TTSException]，由 [CloudSpeechEngine] catch 后放行朗读队列（不卡显示闸门）。
 */
internal object TTSProtocols {

    private val json = Json { ignoreUnknownKeys = true }

    /** 一次合成的上下文，屏蔽各家的凭证差异。 */
    data class Request(
        val text: String,
        val voiceId: String,
        val apiKey: String,
        val apiSecret: String,
        val appId: String,
        val baseUrl: String,
    )

    suspend fun synthesize(protocol: TTSProtocol, req: Request): ByteArray = withContext(Dispatchers.IO) {
        when (protocol) {
            TTSProtocol.MINIMAX -> minimax(req)
            TTSProtocol.IFLYTEK -> iflytek(req)
            TTSProtocol.VOLCENGINE -> volcengine(req)
            TTSProtocol.ALIBABA -> alibaba(req)
            TTSProtocol.TENCENT -> tencent(req)
        }
    }

    // ---- MiniMax -------------------------------------------------------
    // POST {baseUrl}/t2a_v2 → data.audio 是 hex 编码 mp3
    private suspend fun minimax(req: Request): ByteArray {
        val body = buildJsonObject {
            put("model", "speech-2.8-turbo")
            put("text", req.text)
            put("stream", false)
            put("voice_setting", buildJsonObject {
                put("voice_id", req.voiceId)
                put("speed", 1.0)
                put("vol", 1.0)
                put("pitch", 0)
            })
            put("audio_setting", buildJsonObject {
                put("sample_rate", 32_000)
                put("bitrate", 128_000)
                put("format", "mp3")
                put("channel", 1)
            })
            put("output_format", "hex")
        }
        val text = KtorClient.instance.client.post("${req.baseUrl.trimEnd('/')}/t2a_v2") {
            header("Authorization", "Bearer ${req.apiKey}")
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        val root = parseObject(text)
        val status = obj(root, "base_resp")?.let { int(it, "status_code") } ?: -1
        val hex = obj(root, "data")?.let { str(it, "audio") }
        if (status != 0 || hex.isNullOrBlank()) {
            val msg = obj(root, "base_resp")?.let { str(it, "status_msg") }.orEmpty()
            throw TTSException("MiniMax 合成失败 status=$status msg=$msg")
        }
        return hex.hexToBytes()
    }

    // ---- 科大讯飞 -------------------------------------------------------
    // 官方现行的在线语音合成是 WebSocket 流式接口 wss://tts-api.xfyun.cn/v2/tts，
    // HMAC-SHA256 签名，需要 AppID + APIKey + APISecret（旧的 HTTP 接口已不对外开放）。
    // 只发一帧文本（status=2），随后把服务端推来的音频分片拼成完整 mp3。
    private suspend fun iflytek(req: Request): ByteArray {
        if (req.apiSecret.isBlank()) {
            throw TTSException("讯飞缺少 API Secret（新版流式接口必需，请在设置页补填）")
        }
        val url = iflytekAuthUrl(req.apiKey, req.apiSecret, System.currentTimeMillis())
        val frame = buildJsonObject {
            put("common", buildJsonObject { put("app_id", req.appId) })
            put("business", buildJsonObject {
                put("aue", "lame")       // mp3
                put("sfl", 1)            // aue=lame 时必须开启流式返回
                put("vcn", req.voiceId.ifBlank { "xiaoyan" })
                put("speed", 50)
                put("volume", 50)
                put("pitch", 50)
            })
            put("data", buildJsonObject {
                put("status", 2)         // 流式合成文本只能一次性上传，固定为 2
                put("text", Base64.encodeToString(req.text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            })
        }.toString()

        val audio = java.io.ByteArrayOutputStream()
        var finished = false
        KtorClient.instance.client.webSocket(url) {
            send(Frame.Text(frame))
            for (packet in incoming) {
                when (packet) {
                    is Frame.Text -> {
                        val text = packet.readText()
                        val root = runCatching { parseObject(text) }.getOrNull()
                            ?: throw TTSException("讯飞响应不是 JSON: ${text.take(160)}")
                        val code = int(root, "code") ?: -1
                        if (code != 0) {
                            throw TTSException("讯飞合成失败 code=$code: ${str(root, "message").orEmpty()}")
                        }
                        obj(root, "data")?.let { data ->
                            str(data, "audio")?.let { b64 ->
                                runCatching { Base64.decode(b64, Base64.DEFAULT) }
                                    .getOrNull()?.let { audio.write(it) }
                            }
                            if ((int(data, "status") ?: -1) == 2) finished = true
                        }
                    }
                    is Frame.Binary -> audio.write(packet.readBytes())
                    else -> Unit
                }
                if (finished) break
            }
        }
        val bytes = audio.toByteArray()
        if (bytes.isEmpty()) throw TTSException("讯飞返回空音频（请确认 AppID/APIKey/APISecret 与发音人权限）")
        return bytes
    }

    /**
     * 生成讯飞流式接口的鉴权 WebSocket URL。拆出来便于单测签名算法。
     *
     * date 用 GMT/RFC1123（服务端允许 300 秒偏差）；signature 对
     * `host: ...\ndate: ...\nGET /v2/tts HTTP/1.1` 做 HMAC-SHA256 再 base64，
     * 拼进 authorization_origin 后整体再做一次 base64。
     */
    internal fun iflytekAuthUrl(apiKey: String, apiSecret: String, nowMillis: Long): String {
        val host = "tts-api.xfyun.cn"
        val path = "/v2/tts"
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(nowMillis))
        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"
        val signature = base64Encode(hmacSha256(apiSecret.toByteArray(Charsets.UTF_8), signatureOrigin))
        val authOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", " +
            "headers=\"host date request-line\", signature=\"$signature\""
        val authorization = base64Encode(authOrigin.toByteArray(Charsets.UTF_8))
        return "wss://$host$path?authorization=${urlEncode(authorization)}&date=${urlEncode(date)}&host=$host"
    }

    /** 标准 Base64 编码（纯 Kotlin，避免单测环境里 android.util.Base64 是 stub）。 */
    internal fun base64Encode(input: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val out = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else -1
            out.append(table[b0 shr 2])
            out.append(table[((b0 and 0x3) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            out.append(if (b1 >= 0) table[((b1 and 0xF) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)] else '=')
            out.append(if (b2 >= 0) table[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    // ---- 火山引擎（豆包）-----------------------------------------------
    // V3 HTTP Chunked：POST {baseUrl}/api/v3/tts/unidirectional
    // 一次请求、流式回多帧 JSON，每帧 data 是一段 base64 音频，code=20000000 表示说完。
    //
    // 为什么从 /api/v1/tts 迁过来：v1 官方已标「不推荐」，而且**明确不支持 2.0 音色**
    // （zh_female_xxx_uranus_bigtts），发不发 resource id 都没用。2.0 的音质和音色数量
    // 都比 1.0 好一大截，为它必须走 v3。
    //
    // 鉴权有两套，而且**互不兼容**：
    //   新版控制台：只发 X-Api-Key（一把 UUID 形态的 Key）
    //   旧版控制台：X-Api-App-Id + X-Api-Access-Key
    // 不能按「填没填 AppID」分流——用户很容易新旧控制台各抄一点（填了 AppID，Key 却是
    // 新版 API Key），实测这样会拿到 401 `load grant: requested grant not found`，
    // 而且看不出是哪一步错。所以按 Key 的形态先猜一次，被拒再换另一套重试。
    private suspend fun volcengine(req: Request): ByteArray {
        // AppID 空着就只剩单头一条路；有 AppID 时两种都排上，第一次通过就不会再发第二次
        val order = when {
            req.appId.isBlank() -> listOf(false)
            looksLikeVolcApiKey(req.apiKey) -> listOf(false, true)
            else -> listOf(true, false)
        }
        val failures = mutableListOf<String>()
        for (legacyAuth in order) {
            try {
                return volcSynthesize(req, legacyAuth)
            } catch (rejected: VolcAuthRejected) {
                failures += "${if (legacyAuth) "AppID + Access Token" else "API Key 单头"}，" +
                    rejected.detail
            }
        }
        throw TTSException(
            if (failures.size == 1) {
                "火山拒绝了这次请求（${failures.first()}）。" +
                    "新版控制台只填 API Key、AppID 留空；旧版控制台 AppID 和 Access Token 都要填。"
            } else {
                "火山两套鉴权都被拒绝了（${failures.joinToString("；")}）。" +
                    "确认一下 Key 到底是新版的 API Key 还是旧版的 Access Token，两者不能混用。"
            }
        )
    }

    /**
     * 火山 v3 的一次合成。
     *
     * 鉴权被拒时抛 [VolcAuthRejected]，外层会换另一套头重试；其余失败（音色没开通、
     * resource 不匹配、空音频）换头也没用，直接抛给用户。
     */
    private suspend fun volcSynthesize(req: Request, legacyAuth: Boolean): ByteArray {
        val voice = req.voiceId.ifBlank { DEFAULT_VOLC_VOICE }
        val body = buildJsonObject {
            put("user", buildJsonObject { put("uid", "aifriend") })
            put("req_params", buildJsonObject {
                put("text", req.text)
                put("speaker", voice)
                put("audio_params", buildJsonObject {
                    put("format", "mp3")
                    put("sample_rate", 24_000)
                })
            })
        }
        val splitter = JsonFrameSplitter()
        val audio = java.io.ByteArrayOutputStream()
        var finishCode: Int? = null

        KtorClient.instance.client.preparePost("${req.baseUrl.trimEnd('/')}/api/v3/tts/unidirectional") {
            // 这里自己看状态码：火山的失败原因在响应体里（音色没开通、resource 不匹配），
            // 交给全局校验器翻译只会剩一个 4xx，用户没法照着改。
            expectSuccess = false
            if (legacyAuth) {
                header("X-Api-App-Id", req.appId)
                header("X-Api-Access-Key", req.apiKey)
            } else {
                header("X-Api-Key", req.apiKey)
            }
            header("X-Api-Resource-Id", volcResourceId(voice))
            header("X-Api-Request-Id", UUID.randomUUID().toString())
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(body.toString())
        }.execute { response ->
            if (response.status.value !in 200..299) {
                val detail = "HTTP ${response.status.value} ${response.bodyAsText().take(200)}"
                // 401/403 才是「这套头不被认」，换一套还有机会；其它状态码换头也白换
                if (response.status.value == 401 || response.status.value == 403) {
                    throw VolcAuthRejected(detail)
                }
                throw TTSException("火山合成失败 $detail")
            }
            val channel = response.bodyAsChannel()
            while (true) {
                val packet = channel.readRemaining(8 * 1024L)
                if (packet.exhausted()) break
                for (frame in splitter.feed(packet.readByteArray())) {
                    val root = parseObject(frame)
                    val code = int(root, "code") ?: -1
                    when {
                        // 结束帧：data 为 null，不能再当音频解
                        code == VOLC_CODE_FINISHED -> finishCode = code
                        code != 0 -> {
                            val detail = "code=$code ${str(root, "message").orEmpty()}"
                            if (isVolcAuthMessage(detail)) throw VolcAuthRejected(detail)
                            throw TTSException("火山合成失败 $detail")
                        }
                        else -> str(root, "data")?.takeIf { it.isNotEmpty() }?.let {
                            audio.write(Base64.decode(it, Base64.DEFAULT))
                        }
                    }
                    if (finishCode != null) break
                }
                if (finishCode != null) break
            }
        }

        if (audio.size() == 0) {
            throw TTSException("火山返回了空音频（音色 $voice 可能没开通对应模型）")
        }
        return audio.toByteArray()
    }

    /**
     * 新版控制台的 API Key 是 UUID 形态，旧版 Access Token 不是。
     *
     * 只用来决定先试哪套，猜错有自动回退兜着，所以不必求全。
     */
    internal fun looksLikeVolcApiKey(key: String): Boolean =
        VOLC_API_KEY_FORMAT.matches(key.trim())

    /** 响应体里出现这些字样说明是鉴权问题，不是音色问题。 */
    internal fun isVolcAuthMessage(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("grant") || t.contains("unauthorized") ||
            t.contains("access key") || t.contains("access_key") ||
            t.contains("authentication") || t.contains("permission") ||
            t.contains("45000010")
    }

    /** 这套鉴权头不被接受，换另一套可能就过了——不是终态错误。 */
    private class VolcAuthRejected(val detail: String) : Exception(detail)

    private val VOLC_API_KEY_FORMAT =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * 音色对应的 resource id，决定走哪个模型版本，也决定计费商品。
     *
     * `seed-tts-2.0` 只认 2.0 音色、`seed-tts-1.0` 只认 1.0 音色，配错会直接报
     * 「voice_type 不存在」或「access denied」——所以必须按音色推断，不能写死。
     * 声音复刻训练出来的音色（通常 `S_` 开头）走 `seed-icl-2.0`。
     */
    internal fun volcResourceId(voiceType: String): String = when {
        voiceType.endsWith("_uranus_bigtts") -> "seed-tts-2.0"
        voiceType.startsWith("S_") -> "seed-icl-2.0"
        else -> "seed-tts-1.0"
    }

    /** 火山 2.0 的默认音色：官方示例里用小何，账号开通 2.0 后即可用。 */
    internal const val DEFAULT_VOLC_VOICE = "zh_female_xiaohe_uranus_bigtts"

    /** 合成结束的成功码，和中间帧的 0 区分开。 */
    private const val VOLC_CODE_FINISHED = 20_000_000

    // ---- 阿里云百炼 -----------------------------------------------------
    // POST .../SpeechSynthesizer → output.audio.url，再 GET 下载音频。
    private suspend fun alibaba(req: Request): ByteArray {
        val body = buildJsonObject {
            put("model", "cosyvoice-v3-flash")
            put("input", buildJsonObject {
                put("text", req.text)
                put("voice", req.voiceId.ifBlank { "longanyang" })
                put("format", "mp3")
                put("sample_rate", 24_000)
            })
        }
        val text = KtorClient.instance.client.post(
            "${req.baseUrl.trimEnd('/')}/api/v1/services/audio/tts/SpeechSynthesizer"
        ) {
            header("Authorization", "Bearer ${req.apiKey}")
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        val url = obj(parseObject(text), "output")?.let { obj(it, "audio") }?.let { str(it, "url") }
        if (url.isNullOrBlank()) throw TTSException("阿里云合成失败: ${text.take(160)}")
        val bytes: ByteArray = KtorClient.instance.client.request(url) { method = HttpMethod.Get }.body()
        if (bytes.isEmpty()) throw TTSException("阿里云音频下载为空")
        return bytes
    }

    // ---- 腾讯云 ---------------------------------------------------------
    // POST https://tts.tencentcloudapi.com，TC3-HMAC-SHA256 签名，Response.Audio 是 base64。
    private suspend fun tencent(req: Request): ByteArray {
        val service = "tts"
        val host = "tts.tencentcloudapi.com"
        val version = "2019-08-23"
        val region = "ap-guangzhou"
        val timestamp = System.currentTimeMillis() / 1000
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp * 1000))

        // 腾讯云两套音色字段：官方预置的是数字 VoiceType，复刻/克隆音色是一个字符串，
        // 走 FastVoiceType。以前只做 toIntOrNull 兜底，用户填了复刻音色会被静默换成
        // 默认音色——听感不对但没有任何报错，最难查。这里按「是不是数字」分流。
        val numericVoice = req.voiceId.toIntOrNull()
        val payload = buildJsonObject {
            put("Text", req.text)
            put("SessionId", UUID.randomUUID().toString())
            put("VoiceType", numericVoice ?: 101001)
            if (numericVoice == null && req.voiceId.isNotBlank()) {
                put("FastVoiceType", req.voiceId)
            }
            put("Codec", "mp3")
            put("PrimaryLanguage", 1)
        }.toString()

        val ct = "application/json; charset=utf-8"
        val canonicalRequest = listOf(
            "POST", "/", "",
            "content-type:$ct",
            "host:$host",
            "",
            "content-type;host",
            sha256Hex(payload.toByteArray(Charsets.UTF_8)),
        ).joinToString("\n")
        val stringToSign = listOf(
            "TC3-HMAC-SHA256",
            timestamp.toString(),
            "$date/$service/tc3_request",
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)),
        ).joinToString("\n")

        val secretDate = hmacSha256(("TC3" + req.apiSecret).toByteArray(Charsets.UTF_8), date)
        val secretService = hmacSha256(secretDate, service)
        val secretSigning = hmacSha256(secretService, "tc3_request")
        val signature = hmacSha256(secretSigning, stringToSign).toHex()
        val authorization = "TC3-HMAC-SHA256 Credential=${req.apiKey}/$date/$service/tc3_request, " +
            "SignedHeaders=content-type;host, Signature=$signature"

        val textResponse = KtorClient.instance.client.post("https://$host") {
            header("Authorization", authorization)
            header("Content-Type", ct)
            header("X-TC-Action", "TextToVoice")
            header("X-TC-Timestamp", timestamp.toString())
            header("X-TC-Version", version)
            header("X-TC-Region", region)
            setBody(payload)
        }.bodyAsText()
        val response = obj(parseObject(textResponse), "Response")
            ?: throw TTSException("腾讯云响应异常: ${textResponse.take(160)}")
        val error = obj(response, "Error")
        if (error != null) {
            throw TTSException("腾讯云合成失败 ${str(error, "Code").orEmpty()}: ${str(error, "Message").orEmpty()}")
        }
        val audio = str(response, "Audio")
        if (audio.isNullOrBlank()) throw TTSException("腾讯云返回空音频")
        return Base64.decode(audio, Base64.DEFAULT)
    }

    // ---- JSON / 编码工具 ------------------------------------------------

    private fun parseObject(text: String): JsonObject =
        json.parseToJsonElement(text) as? JsonObject ?: throw TTSException("响应不是 JSON: ${text.take(120)}")

    private fun obj(parent: JsonObject, key: String): JsonObject? = parent[key] as? JsonObject
    private fun str(parent: JsonObject, key: String): String? =
        (parent[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
    private fun int(parent: JsonObject, key: String): Int? = str(parent, key)?.toIntOrNull()

    internal fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).toHex()

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    internal fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    internal fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex 长度必须为偶数" }
        return ByteArray(length / 2) { i ->
            ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
        }
    }

    private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

class TTSException(message: String) : Exception(message)
