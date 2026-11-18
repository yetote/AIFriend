package com.core.tts

import android.util.Base64
import com.core.network.KtorClient
import com.core.repository.settings.TTSProtocol
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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

    // ---- 火山引擎 -------------------------------------------------------
    // POST {baseUrl}/api/v1/tts，Authorization 用分号 "Bearer;token"，data 是 base64。
    private suspend fun volcengine(req: Request): ByteArray {
        val body = buildJsonObject {
            put("app", buildJsonObject {
                put("appid", req.appId)
                put("token", req.apiKey)
                put("cluster", "volcano_tts")
            })
            put("user", buildJsonObject { put("uid", "aifriend") })
            put("audio", buildJsonObject {
                put("voice_type", req.voiceId.ifBlank { "zh_female_qingxin" })
                put("encoding", "mp3")
                put("speed_ratio", 1.0)
                put("volume_ratio", 1.0)
                put("pitch_ratio", 1.0)
            })
            put("request", buildJsonObject {
                put("reqid", UUID.randomUUID().toString())
                put("text", req.text)
                put("text_type", "plain")
                put("operation", "query")
            })
        }
        val text = KtorClient.instance.client.post("${req.baseUrl.trimEnd('/')}/api/v1/tts") {
            header("Authorization", "Bearer;${req.apiKey}")
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(body.toString())
        }.bodyAsText()
        val root = parseObject(text)
        val code = int(root, "code") ?: -1
        val data = str(root, "data")
        if (code != 3000 || data.isNullOrBlank()) {
            throw TTSException("火山合成失败 code=$code msg=${str(root, "message").orEmpty()}")
        }
        return Base64.decode(data, Base64.DEFAULT)
    }

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

        val payload = buildJsonObject {
            put("Text", req.text)
            put("SessionId", UUID.randomUUID().toString())
            put("VoiceType", req.voiceId.toIntOrNull() ?: 101001)
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
