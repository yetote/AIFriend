package com.core.tts

/**
 * 决定一条消息能不能朗读的规则。
 *
 * 抽成纯函数而不是塞在 ViewModel 里，是因为朗读的筛选**错了会出事**：
 * 私有消息一旦念出来就收不回，一桌人都能听见「我是狼、今晚刀 3 号」。
 * 这类规则必须有单测守着，不能靠人记得别改。
 *
 * 这里刻意不认识狼人杀的 MessageKind——调用方把「这是不是公开发言」
 * 预先算好传进来。模块依赖保持单向：core 不依赖 feature。
 */
object SpeechFilter {

    /**
     * [isPublicSpeech] 由调用方判定：必须是公开发言类（不是上帝播报、不是投票、
     * 不是夜间私聊），且 audience 为 null。
     *
     * [selfSeat] 为 null 表示观战——没有「自己」，所有公开发言都读。
     */
    fun speakable(
        isPublicSpeech: Boolean,
        speakerSeat: Int?,
        selfSeat: Int?,
        text: String,
    ): Boolean = isPublicSpeech &&
        speakerSeat != null && // 上帝播报没有座位，本来也不该传进来
        speakerSeat != selfSeat && // 不读自己刚打完的字
        text.isNotBlank()
}
