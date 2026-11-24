package com.core.repository.balance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * 各家余额响应的解析规则。
 *
 * 这些接口没有公开文档，字段名和语义全靠实测钉出来的，
 * 所以每一家的字段规则都要有测试守着——改错了会在测试里炸，
 * 而不是等用户打开页面看到一个「余额 ¥NaN」。
 */
class BalanceParsersTest {

    private fun parse(
        kind: BalanceParserKind,
        body: String,
        targetModel: String? = null,
    ): BalanceResult = BalanceParsers.parse(
        kind = kind,
        providerId = "p",
        providerName = "厂商",
        body = body,
        targetModel = targetModel,
    )

    private fun ok(result: BalanceResult): BalanceSnapshot {
        assertTrue("期望成功，实际是 $result", result is BalanceResult.Ok)
        return (result as BalanceResult.Ok).snapshot
    }

    // ---- DeepSeek ----------------------------------------------------

    @Test
    fun `DeepSeek 金额是字符串也要认`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.DEEPSEEK,
                """
                {"is_available": true, "balance_infos": [
                  {"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}
                ]}
                """.trimIndent(),
            )
        )
        assertEquals("¥110.00", snapshot.headline)
        assertTrue(snapshot.details.contains("赠金 ¥10.00"))
        assertTrue(snapshot.details.contains("充值 ¥100.00"))
    }

    @Test
    fun `DeepSeek 多币种优先取人民币`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.DEEPSEEK,
                """
                {"is_available": true, "balance_infos": [
                  {"currency":"USD","total_balance":"9.00"},
                  {"currency":"CNY","total_balance":"20.00"}
                ]}
                """.trimIndent(),
            )
        )
        assertEquals("¥20.00", snapshot.headline)
    }

    @Test
    fun `DeepSeek 余额不可用要显示成见底`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.DEEPSEEK,
                """{"is_available": false, "balance_infos": [{"currency":"CNY","total_balance":"0.00"}]}""",
            )
        )
        assertEquals(BalanceTone.Empty, snapshot.tone)
    }

    // ---- Kimi --------------------------------------------------------

    @Test
    fun `Kimi 读 available_balance`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.MOONSHOT,
                """{"code":0,"data":{"available_balance":100.0,"voucher_balance":50.0,"cash_balance":50.0}}""",
            )
        )
        assertEquals("¥100.00", snapshot.headline)
        assertTrue(snapshot.details.contains("代金券 ¥50.00"))
    }

    @Test
    fun `Kimi 换个字段名也要认`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.MOONSHOT,
                """{"code":0,"data":{"total_balance":8.5}}""",
            )
        )
        assertEquals("¥8.50", snapshot.headline)
    }

    @Test
    fun `Kimi 返回了错误码要说清楚`() {
        val result = parse(
            BalanceParserKind.MOONSHOT,
            """{"code":1004,"message":"账号欠费"}""",
        )
        assertTrue("期望失败，实际是 $result", result is BalanceResult.Failed)
        assertEquals("账号欠费", (result as BalanceResult.Failed).reason)
    }

    // ---- MiniMax -----------------------------------------------------

    @Test
    fun `MiniMax 剩余百分比直接用不要再减 100`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.MINIMAX,
                """
                {"base_resp":{"status_code":0,"status_msg":""},"model_remains":[
                  {"model_name":"MiniMax-M2",
                   "current_interval_total_count":100,
                   "current_interval_usage_count":80,
                   "current_interval_remaining_percent":80,
                   "end_time":1778753640304,
                   "current_weekly_total_count":500,
                   "current_weekly_usage_count":300,
                   "current_weekly_remaining_percent":60,
                   "weekly_end_time":1781345721976,
                   "current_weekly_status":1}
                ]}
                """.trimIndent(),
            )
        )
        assertEquals("5 小时剩余 80%", snapshot.headline)
        assertTrue(snapshot.details.contains("本周剩余 60%"))
    }

    @Test
    fun `MiniMax 业务失败是 HTTP 200 也要认出来`() {
        val result = parse(
            BalanceParserKind.MINIMAX,
            """{"base_resp":{"status_code":1004,"status_msg":"套餐已过期"},"model_remains":[]}""",
        )
        assertTrue("期望失败，实际是 $result", result is BalanceResult.Failed)
        assertEquals("套餐已过期", (result as BalanceResult.Failed).reason)
    }

    @Test
    fun `MiniMax 没有周额度时不显示周那一条`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.MINIMAX,
                """
                {"base_resp":{"status_code":0},"model_remains":[
                  {"model_name":"MiniMax-M2","current_interval_remaining_percent":50,
                   "current_weekly_status":3}
                ]}
                """.trimIndent(),
            )
        )
        assertTrue(snapshot.details.none { it.contains("本周") })
    }

    // ---- 智谱 --------------------------------------------------------

    @Test
    fun `智谱的 percentage 是已用比例要反过来`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.ZHIPU,
                """
                {"code":200,"msg":"操作成功","success":true,"data":{
                  "limits":[
                    {"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":20,"nextResetTime":1778753640304},
                    {"type":"TIME_LIMIT","unit":5,"number":1,"usage":100,"currentValue":0,"remaining":100,"percentage":0,"nextResetTime":1781345721976}
                  ],
                  "level":"lite"
                }}
                """.trimIndent(),
            )
        )
        // 已用 20% → 剩余 80%
        assertEquals("5 小时 剩余 80%", snapshot.headline)
        assertTrue(snapshot.details.contains("套餐 lite"))
        assertTrue(snapshot.details.any { it.startsWith("5 小时 剩余 80%") })
        assertTrue(snapshot.details.any { it.startsWith("月度 剩余 100%") })
    }

    @Test
    fun `智谱的重置时间是毫秒时间戳`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.ZHIPU,
                """
                {"data":{"limits":[
                  {"type":"TOKENS_LIMIT","unit":3,"number":5,"percentage":0,"nextResetTime":1778753640304}
                ]}}
                """.trimIndent(),
            )
        )
        assertTrue(
            "应有重置时间，实际 ${snapshot.details}",
            snapshot.details.any { it.contains("重置") },
        )
    }

    // ---- 硅基流动 / OpenRouter ---------------------------------------

    @Test
    fun `硅基流动读 balance`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.SILICONFLOW,
                """{"data":{"balance":"12.34","chargeBalance":"10.00","totalUsage":"2.34"}}""",
            )
        )
        assertEquals("¥12.34", snapshot.headline)
        assertTrue(snapshot.details.contains("已用 ¥2.34"))
    }

    @Test
    fun `OpenRouter 的剩余是总额减已用`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.OPENROUTER,
                """{"data":{"total_credits":10.0,"total_usage":1.5}}""",
            )
        )
        assertEquals("$8.50", snapshot.headline)
        assertTrue(snapshot.details.contains("总额 $10.00"))
    }

    // ---- 火山方舟 ----------------------------------------------------

    @Test
    fun `火山已开通的模型`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.VOLCENGINE,
                """
                {"Items":[
                  {"FoundationModelName":"doubao-seed-2-1-pro-260915","State":"Available"},
                  {"FoundationModelName":"doubao-seed-1-6","State":"Unavailable"}
                ]}
                """.trimIndent(),
                targetModel = "doubao-seed-2-1-pro-260915",
            )
        )
        assertEquals("模型已开通", snapshot.headline)
        assertEquals(BalanceTone.Normal, snapshot.tone)
    }

    @Test
    fun `火山没开通的模型要一眼看出来`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.VOLCENGINE,
                """
                {"Items":[
                  {"FoundationModelName":"doubao-seed-2-1-pro-260915","State":"Available"},
                  {"FoundationModelName":"doubao-seed-1-6","State":"Unavailable"}
                ]}
                """.trimIndent(),
                targetModel = "doubao-seed-1-6",
            )
        )
        assertEquals("模型未开通", snapshot.headline)
        assertEquals(BalanceTone.Empty, snapshot.tone)
    }

    @Test
    fun `火山对不上模型名时不能直接判没开通`() {
        val snapshot = ok(
            parse(
                BalanceParserKind.VOLCENGINE,
                """{"Items":[{"FoundationModelName":"doubao-seed-1-6","State":"Available"}]}""",
                targetModel = "ep-20241101-abc",
            )
        )
        assertEquals("未在开通列表里", snapshot.headline)
        assertEquals(BalanceTone.Low, snapshot.tone)
    }

    // ---- 通用 --------------------------------------------------------

    @Test
    fun `解析失败时能翻出厂商给的原因`() {
        val message = BalanceParsers.extractErrorMessage(
            """{"error":{"code":"InvalidApiKey","message":"密钥无效"}}"""
        )
        assertEquals("密钥无效", message)
    }

    @Test
    fun `不是 JSON 也不能崩`() {
        val result = parse(BalanceParserKind.DEEPSEEK, "<html>502</html>")
        assertTrue("期望失败，实际是 $result", result is BalanceResult.Failed)
    }
}

/**
 * 火山签名。
 *
 * 签名的中间密钥是**字节**不是 hex 文本，这一点错一步算出来的值就完全不同，
 * 而服务端只会回一句「InvalidAccessKey」，看着像 Key 填错了。
 */
class VolcengineSignerTest {

    private val fixedTime = Date(1778753640304L)

    private fun sign(
        accessKeyId: String = "AKLT",
        secret: String = "SECRET",
        body: String = """{"Filter":{}}""",
    ) = VolcengineSigner.sign(
        accessKeyId = accessKeyId,
        secretAccessKey = secret,
        region = "cn-beijing",
        host = "ark.cn-beijing.volcengineapi.com",
        query = "Action=ListModelActivations&Version=2024-01-01",
        body = body,
        timestamp = fixedTime,
    )

    @Test
    fun `同样的输入签名要一样`() {
        assertEquals(sign().headers["Authorization"], sign().headers["Authorization"])
    }

    @Test
    fun `换一把密钥签名要变`() {
        assertTrue(
            "不同 Secret 必须算出不同签名",
            sign(secret = "A").headers["Authorization"] != sign(secret = "B").headers["Authorization"],
        )
    }

    @Test
    fun `换请求体签名要变`() {
        assertTrue(
            "请求体进了签名里，改了就必须变",
            sign(body = """{"Filter":{"a":1}}""").headers["Authorization"] !=
                sign(body = """{"Filter":{"a":2}}""").headers["Authorization"],
        )
    }

    @Test
    fun `Authorization 的格式要对`() {
        val authorization = sign().headers["Authorization"].orEmpty()
        assertTrue(
            "Authorization 格式不对：$authorization",
            authorization.startsWith("HMAC-SHA256 Credential=AKLT/") &&
                authorization.contains("SignedHeaders=content-type;host;x-content-sha256;x-date") &&
                authorization.contains("Signature="),
        )
    }

    @Test
    fun `请求头要齐`() {
        val headers = sign().headers
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("ark.cn-beijing.volcengineapi.com", headers["Host"])
        assertTrue(headers["X-Date"].orEmpty().endsWith("Z"))
        assertEquals(64, headers["X-Content-Sha256"].orEmpty().length)
    }
}
