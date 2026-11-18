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
 * 可选的云端朗读音色。
 *
 * 各家音色 ID 完全不同（MiniMax 是 male-qn-qingse、讯飞是 xiaoyan、
 * 火山是 zh_female_qingxin、腾讯云是数字音色号、阿里是 longanyang），
 * 所以按服务商分组：[forProvider] 只返回该服务能用的音色。
 *
 * 放在 core:ai 而不是 core:tts：ui:agent 在建角色页展示、feature:werewolf 在朗读时
 * 取用，两者都只依赖 core:ai。core:tts 只认 voiceId 字符串，不认识展示名。
 *
 * [id] 为 null 表示「跟随引擎默认音色」，与 Friend.voiceId 的 null 语义一致。
 */
data class VoiceOption(val id: String?, val label: String)

object VoiceCatalog {

    private val byProvider: Map<String, List<VoiceOption>> = mapOf(
        "minimax" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("male-qn-qingse", "青涩青年"),
            VoiceOption("male-qn-jingying", "精英青年"),
            VoiceOption("male-qn-badao", "霸道青年"),
            VoiceOption("male-qn-daxuesheng", "青年大学生"),
            VoiceOption("female-shaonv", "少女"),
            VoiceOption("female-yujie", "御姐"),
            VoiceOption("female-chengshu", "成熟女性"),
            VoiceOption("female-tianmei", "甜美女性"),
        ),
        "iflytek" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("xiaoyan", "小燕（女）"),
            VoiceOption("xiaolin", "小林（男）"),
            VoiceOption("xiaomeng", "小梦（女）"),
            VoiceOption("xiaoyu", "小宇（男）"),
        ),
        "volcengine" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("zh_female_qingxin", "清新女声"),
            VoiceOption("zh_male_zhixing", "知性男声"),
            VoiceOption("zh_female_wanqudashu_moon_bigtts", "湾湾大叔（大模型）"),
            VoiceOption("zh_male_zhixing_moon_bigtts", "知性男声（大模型）"),
        ),
        "alibaba" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("longanyang", "龙安洋"),
            VoiceOption("longxiaochun_v2", "龙小春"),
            VoiceOption("longwan_v2", "龙婉"),
            VoiceOption("longyuan_v2", "龙远"),
        ),
        "tencent" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("101001", "知玉（女）"),
            VoiceOption("101002", "知雪（女）"),
            VoiceOption("101004", "知云（男）"),
            VoiceOption("101011", "知言（男）"),
        ),
    )

    /** MiniMax 音色也作为「默认服务 / 没指定服务」时的展示，保证旧角色能回显。 */
    val all: List<VoiceOption> = byProvider.getValue("minimax")

    /** 选中某个 TTS 服务时，界面只展示该服务的音色。 */
    fun forProvider(providerId: String?): List<VoiceOption> =
        byProvider[providerId] ?: all

    /** 界面回显：未知 id（音色下线等）也别崩，落到「默认」。 */
    fun labelOf(id: String?): String = all.firstOrNull { it.id == id }?.label ?: "默认音色"

    /** 默认音色 id（引擎侧也有一份，这里给 UI 回填用）。 */
    fun defaultVoiceOf(providerId: String?): String? =
        providerId?.let { TTSProviderCatalog.byId(it)?.defaultVoice }
}
