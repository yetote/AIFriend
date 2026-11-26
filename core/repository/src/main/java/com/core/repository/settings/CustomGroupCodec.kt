package com.core.repository.settings

/**
 * 同一自定义中转站下多分组模型的编码与解码工具。
 *
 * 编码格式：`@{groupId}::{rawModelId}`
 * - 单分组（或内置厂商）：不编码，保持原始模型 ID；
 * - 多分组：用不可变的 `groupId` 绑定该模型归属的令牌分组，因此用户在设置页修改分组显示名时，
 *   已经创建好的好友和存档完全不受影响；发请求前再经 [rawModelId] 还原出中转站识别的真实模型 ID。
 *
 * 选用 `@` + `::` 作为分隔标记：主流厂商模型 ID 只含 `/`、`-`、`.`、`_` 与单个 `:`（如 `:free`、`:8b`），
 * 不会以 `@` 开头且包含 `::`，可安全无歧义区分。
 */
object CustomGroupCodec {

    private const val PREFIX = "@"
    private const val SEPARATOR = "::"

    data class DecodedModel(
        val groupId: String?,
        val rawModelId: String,
    )

    fun encode(groupId: String, rawModelId: String): String {
        val cleanGroup = groupId.trim()
        val cleanRaw = rawModelId(rawModelId)
        if (cleanGroup.isEmpty() || cleanRaw.isEmpty()) return cleanRaw
        return "$PREFIX$cleanGroup$SEPARATOR$cleanRaw"
    }

    fun decode(modelId: String): DecodedModel {
        val trimmed = modelId.trim()
        if (!trimmed.startsWith(PREFIX)) return DecodedModel(null, trimmed)
        val sepIndex = trimmed.indexOf(SEPARATOR)
        if (sepIndex <= PREFIX.length) return DecodedModel(null, trimmed)
        val groupId = trimmed.substring(PREFIX.length, sepIndex).trim()
        val raw = trimmed.substring(sepIndex + SEPARATOR.length).trim()
        if (groupId.isEmpty() || raw.isEmpty()) return DecodedModel(null, trimmed)
        return DecodedModel(groupId, raw)
    }

    fun groupIdOf(modelId: String): String? = decode(modelId).groupId

    fun rawModelId(modelId: String): String = decode(modelId).rawModelId

    /** 归一化 Base URL，用于判断两个自定义接口是否指向同一家中转站。 */
    fun normalizeBaseUrl(url: String): String =
        url.trim().trimEnd('/').lowercase()
}
