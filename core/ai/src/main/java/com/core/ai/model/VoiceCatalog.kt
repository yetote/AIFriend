package com.core.ai.model

import com.core.repository.settings.TTSProviderCatalog

/**
 * 建角色页展示用的「TTS 服务」选项。
 *
 * 数据来自 [TTSProviderCatalog]（core:repository），这里只做一层薄 DTO：
 * ui:agent 依赖 core:ai 但不依赖 core:repository，不能直接看目录实体。
 * 多一个「默认」选项让不想指定的角色跟随全局。
 */
data class TTSServiceOption(val id: String?, val label: String)

object TTSServiceCatalog {
    val all: List<TTSServiceOption> = listOf(TTSServiceOption(null, "默认服务")) +
        TTSProviderCatalog.all.map { TTSServiceOption(it.id, it.name) }

    fun labelOf(id: String?): String = all.firstOrNull { it.id == id }?.label ?: "默认服务"
}

/**
 * 可选的云端朗读音色（MiniMax 系统音色）。
 *
 * 放在 core:ai 而不是 core:tts：ui:agent 在建角色页展示、feature:werewolf 在朗读时
 * 取用，两者都只依赖 core:ai。core:tts 只认 voiceId 字符串，不认识展示名——
 * 这样换 TTS 厂商时，这里换的是音色清单，引擎层不受影响。
 *
 * [id] 为 null 表示「跟随引擎默认音色」，与 Friend.voiceId 的 null 语义一致。
 */
data class VoiceOption(val id: String?, val label: String)

object VoiceCatalog {
    /** 第一项「默认」让不想挑的用户也能直接保存。 */
    val all: List<VoiceOption> = listOf(
        VoiceOption(null, "默认音色"),
        VoiceOption("male-qn-qingse", "青涩青年"),
        VoiceOption("male-qn-jingying", "精英青年"),
        VoiceOption("male-qn-badao", "霸道青年"),
        VoiceOption("male-qn-daxuesheng", "青年大学生"),
        VoiceOption("female-shaonv", "少女"),
        VoiceOption("female-yujie", "御姐"),
        VoiceOption("female-chengshu", "成熟女性"),
        VoiceOption("female-tianmei", "甜美女性"),
    )

    /** 界面回显：未知 id（音色下线等）也别崩，落到「默认」。 */
    fun labelOf(id: String?): String = all.firstOrNull { it.id == id }?.label ?: "默认音色"
}
