package com.core.repository.balance

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 火山引擎 OpenAPI 的请求签名（HMAC-SHA256）。
 *
 * 方舟的管控面接口（查模型开通状态、查推理限额）**不接受聊天用的 API Key**，
 * 只认 Access Key ID + Secret Access Key，而且每个请求都要自己算签名。
 *
 * 签名的四步派生是固定套路：
 * ```
 * kDate    = HMAC(secret, "20240101")
 * kRegion  = HMAC(kDate, "cn-beijing")
 * kService = HMAC(kRegion, "ark")
 * kSigning = HMAC(kService, "request")
 * ```
 * 再拿 kSigning 对 StringToSign 签名。派生出来的中间密钥是按天、按地域、按服务
 * 分层的，所以日期和服务名一个都不能错——错一个不会报「签名格式不对」，
 * 只会报「InvalidAccessKey」，看着像 Key 填错了，其实是派生那一步错了。
 */
internal object VolcengineSigner {

    private const val ALGORITHM = "HMAC-SHA256"
    private const val SERVICE = "ark"

    /** 算好签名的一个请求。 */
    data class SignedRequest(
        val headers: Map<String, String>,
        val body: String,
    )

    /**
     * 给一个 POST 请求签名。
     *
     * @param query 已经按字典序排好的查询串（`Action=…&Version=…`）。
     *              排序在这里做掉，是因为签名用的是排序后的串，
     *              而实际发出去的 URL 顺序可以不同——服务端会用同样规则重排。
     */
    fun sign(
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        host: String,
        query: String,
        body: String,
        timestamp: Date = Date(),
    ): SignedRequest {
        val requestDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(timestamp)
        val shortDate = requestDate.substring(0, 8)
        val payloadHash = hex(sha256(body.toByteArray(Charsets.UTF_8)))

        // 要签的头：小写、每行一条、行尾带 \n。
        // 顺序必须和 SignedHeaders 一致（都按字典序）。
        val canonicalHeaders = buildString {
            append("content-type:application/json\n")
            append("host:$host\n")
            append("x-content-sha256:$payloadHash\n")
            append("x-date:$requestDate\n")
        }
        val signedHeaders = "content-type;host;x-content-sha256;x-date"

        val canonicalRequest = buildString {
            append("POST").append('\n')
            append("/").append('\n')
            append(query).append('\n')
            append(canonicalHeaders)
            // CanonicalHeaders 后面要有一个空行再接 SignedHeaders。
            // 少这一行是最容易犯的错——签名算出来不一样，但报的是「签名错误」。
            append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }

        val credentialScope = "$shortDate/$region/$SERVICE/request"
        val stringToSign = buildString {
            append(ALGORITHM).append('\n')
            append(requestDate).append('\n')
            append(credentialScope).append('\n')
            append(hex(sha256(canonicalRequest.toByteArray(Charsets.UTF_8))))
        }

        val kDate = hmac(secretAccessKey, shortDate)
        val kRegion = hmacBytes(kDate, region)
        val kService = hmacBytes(kRegion, SERVICE)
        val kSigning = hmacBytes(kService, "request")
        val signature = hex(hmacBytes(kSigning, stringToSign))

        return SignedRequest(
            headers = mapOf(
                "Content-Type" to "application/json",
                "Host" to host,
                "X-Date" to requestDate,
                "X-Content-Sha256" to payloadHash,
                "Authorization" to
                    "$ALGORITHM Credential=$accessKeyId/$credentialScope, " +
                        "SignedHeaders=$signedHeaders, Signature=$signature",
            ),
            body = body,
        )
    }

    private fun hmac(key: String, data: String): ByteArray =
        hmacBytes(key.toByteArray(Charsets.UTF_8), data)

    /**
     * HMAC 的密钥是**字节串**，不是它的十六进制文本。
     *
     * 派生密钥那四步每一步都要拿上一步的字节结果继续算，
     * 中间任何一步改成 hex 字符串再喂回去，签名就全错了。
     */
    private fun hmacBytes(key: ByteArray, data: String): ByteArray =
        Sha256.hmac(key, data.toByteArray(Charsets.UTF_8))

    private fun sha256(data: ByteArray): ByteArray = Sha256.digest(data)

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append("%02x".format(b.toInt() and 0xff))
        }
        return sb.toString()
    }
}
