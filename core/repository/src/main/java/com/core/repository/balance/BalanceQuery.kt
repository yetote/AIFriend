package com.core.repository.balance

import com.core.network.getReadableMessage
import com.core.network.KtorClient
import com.core.repository.settings.AIProvider
import com.core.repository.storage.AICredential

/**
 * 查一家厂商的账户余量。
 *
 * **不用网页登录**——各家的开放平台都有 OAuth，但个人开发者申请 OAuth 应用
 * 要企业资质、要审核、要配回调域名，周期以周计，而且 App 里还得塞 WebView 和 token 刷新。
 * 而余量这件事，八成厂商直接开了 HTTP 接口，用用户已经填在设置页的那把 Key 就能查。
 *
 * 只有火山方舟是例外：它的管控面接口只认 Access Key ID + Secret Access Key，
 * 聊天用的 API Key 查不了额度。所以火山要多填一对 AK/SK（见 [AICredential.accessKeyId]）。
 */
object BalanceQuery {

    /** 方舟管控面的域名。签名里的 Host 也用这个值。 */
    private const val VOLC_HOST = "ark.cn-beijing.volcengineapi.com"

    private const val VOLC_REGION = "cn-beijing"

    /**
     * 查余额。
     *
     * @param targetModel 火山用：判断**这个模型**在方舟上开通了没。
     *                   方舟的模型要在控制台单独开通，没开通调用直接 404，
     *                   而 `/models` 会把没开通的也列出来——所以这一项比余额更要紧。
     */
    suspend fun query(
        provider: AIProvider,
        credential: AICredential,
        targetModel: String? = null,
    ): BalanceResult {
        val spec = provider.balance
            ?: return BalanceResult.Unsupported("${provider.name} 没有公开的余量查询接口")

        if (credential.apiKey.isBlank() && spec.method != BalanceMethod.SIGNED_POST) {
            return BalanceResult.Failed("还没有配置 ${provider.name} 的 API Key")
        }

        return try {
            val body = when (spec.method) {
                BalanceMethod.GET -> get(spec, credential)
                BalanceMethod.SIGNED_POST -> signedPost(spec, credential)
            }
            val parsed = BalanceParsers.parse(
                kind = spec.parser,
                providerId = provider.id,
                providerName = provider.name,
                body = body,
                targetModel = targetModel,
            )
            // 解析失败时再翻一遍通用错误字段：有些厂商把原因放在 error.message 里，
            // 直接把原文给用户，比「返回里没有余额信息」有用得多
            if (parsed is BalanceResult.Failed) {
                val detail = BalanceParsers.extractErrorMessage(body)
                if (!detail.isNullOrBlank()) parsed.copy(reason = detail) else parsed
            } else {
                parsed
            }
        } catch (t: Throwable) {
            BalanceResult.Failed(getReadableMessage(t))
        }
    }

    /**
     * 普通 GET。
     *
     * 鉴权头的写法有候选时依次试：第一个被拒（401）就换下一种。
     * 只有鉴权失败才换写法——网络断了换写法也没用，重试只是白等一次超时。
     */
    private suspend fun get(spec: ProviderBalanceSpec, credential: AICredential): String {
        var authFailure: Throwable? = null
        for (template in spec.authHeaderValues) {
            val headers = mapOf(spec.authHeaderName to template.replace("{key}", credential.apiKey))
            try {
                return KtorClient.instance.getTextWithHeaders(spec.url, headers)
            } catch (t: Throwable) {
                if (!isAuthFailure(t)) throw t
                authFailure = t
            }
        }
        throw authFailure ?: IllegalStateException("没有可用的鉴权方式")
    }

    /** 401 / 403：换一种鉴权写法也许能过；别的错误跟鉴权无关。 */
    private fun isAuthFailure(t: Throwable): Boolean =
        generateSequence(t) { it.cause }
            .any { it is com.core.network.DomainException.UnauthorizedException }

    /**
     * 火山这类要自己签名的接口。
     *
     * 签名算错时服务端报的是 `InvalidAccessKey`，看着像 Key 填错了。
     * 所以拿不到 AK/SK 时直接拦下来，不发那个注定失败的请求。
     */
    private suspend fun signedPost(spec: ProviderBalanceSpec, credential: AICredential): String {
        if (credential.accessKeyId.isBlank() || credential.accessKeySecret.isBlank()) {
            throw IllegalStateException("火山方舟的额度查询需要 Access Key ID 和 Secret")
        }
        val action = spec.action ?: error("签名单调接口必须给 action")
        val query = "Action=$action&Version=2024-01-01"
        val body = """{"Filter":{"IncludeDeprecatedModels":false}}"""
        val signed = VolcengineSigner.sign(
            accessKeyId = credential.accessKeyId,
            secretAccessKey = credential.accessKeySecret,
            region = VOLC_REGION,
            host = VOLC_HOST,
            query = query,
            body = body,
        )
        return KtorClient.instance.postText(
            url = "${spec.url}?$query",
            body = signed.body,
            headers = signed.headers,
        )
    }
}
