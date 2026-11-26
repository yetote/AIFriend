package com.ui.werewolf.data

/**
 * 座位卡底下那行模型名的缩写。
 *
 * 列宽只有 72dp，原样塞 `deepseek/deepseek-chat:free`（24 字符）只会显示成一串省略号，
 * 认不出是哪个模型。目标是**保留能辨认的那一截**，而不是显示完整 id。
 *
 * 规则（都是纯字符串操作，不猜厂商）：
 * - 去 `:free` / `:thinking` 这类聚合后缀——它说的是计费方式，不是模型是谁；
 * - `deepseek/deepseek-chat` 这种「重复厂商名」取最后一段（去掉 `deepseek/`），
 *   `tencent/Hunyuan-MT-7B` 这种不重复的只去掉组织段；
 * - 自家短名 `qwen3.8-flash` 原样返回；
 * - 剩下的仍太长就截断，宁可短也不要把徽标那行挤变形。
 */
internal fun shortModelLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed == "human") return ""
    // 自定义中转站多分组编码 `@groupId::rawModelId`，先剥离分组前缀再缩写
    val stripped = if (trimmed.startsWith("@") && "::" in trimmed) {
        trimmed.substringAfter("::").trim()
    } else {
        trimmed
    }
    // 聚合平台的计费/模式后缀
    val base = stripped.substringBefore(":")
    val last = base.substringAfterLast("/")
    // openrouter 的 x-ai/grok-pro 这类两段不重复，取后一段即可；
    // deepseek/deepseek-chat 这类重复前缀，last 已经是干净的模型名。
    val candidate = last.ifEmpty { base }
    // 上限放到 22：doubao-seed-2.1-lite（20）这种完整短名本身就是可辨识的，
    // 砍掉反而认不出；只有异常长的自定义 id 才截断。
    return if (candidate.length > 22) candidate.take(22) else candidate
}
