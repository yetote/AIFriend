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
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
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
    // POST {baseUrl}/v2/tts，MD5 签名 + 表单，响应二进制音频。
    // voiceId 用讯飞发音人（xiaoyan/xiaolin 等），与 MiniMax 的不同。
    private suspend fun iflytek(req: Request): ByteArray {
        val param = buildJsonObject {
            put("auf", "audio/L16;rate=16000")
            put("aue", "lame")
            put("voice_name", req.voiceId.ifBlank { "xiaoyan" })
            put("speed", "50")
            put("volume", "50")
            put("pitch", "50")
            put("engine_type", "intp65")
        }.toString()
        val paramB64 = Base64.encodeToString(param.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val checkSum = md5(req.apiKey + curTime + paramB64)
        val bytes: ByteArray = KtorClient.instance.client.request("${req.baseUrl.trimEnd('/')}/v2/tts") {
            method = HttpMethod.Post
            header("X-Appid", req.appId)
            header("X-CurTime", curTime)
            header("X-Param", paramB64)
            header("X-CheckSum", checkSum)
            contentType(io.ktor.http.ContentType.Application.FormUrlEncoded)
            setBody("text=${urlEncode(req.text)}")
        }.body()
        if (bytes.isEmpty()) throw TTSException("讯飞返回空音频")
        // 鉴权失败时讯飞返回 JSON 错误体而非音频，用前几个字节粗判。
        val head = bytes.take(48).map { it.toInt() and 0xFF }
        if ('{'.code in head) {
            throw TTSException("讯飞合成失败: ${String(bytes, Charsets.UTF_8).take(160)}")
        }
        return bytes
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
