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
     *
     * [readEverything] 是「上帝视角」开关：纯观战时牌桌上的一切本来就是公开的，
     * 狼人夜聊、查验点人、上帝播报全都摊在屏幕上，只念公开发言反而漏了一半剧情。
     * **只有确认观战才能开**——参与的局里打开它，等于当着别人的面把狼人私聊念出来。
     * 这个判断由调用方给出（`humanSeat == null`），这里只照做。投票表态不在
     * 这个开关的范围内，见 [isInnerThought]。
     */
    /**
     * [isInnerThought] 是「这是不是心里想的」：投票理由、夜里的查验用药念头。
     * 这类内容**任何模式下都不读**，[readEverything] 也翻不过来。
     *
     * 这个优先级是刻意排在最前面的：朗读的意义是听牌桌上的人对话，
     * 把内心盘算也念出来，一圈投票就变成十几段独白，还把「谁在说、谁在想」
     * 搅成一锅。用户对这一点给过明确反馈：投票太拖节奏。
     */
    fun speakable(
        isPublicSpeech: Boolean,
        speakerSeat: Int?,
        selfSeat: Int?,
        text: String,
        readEverything: Boolean = false,
        isInnerThought: Boolean = false,
    ): Boolean {
        // 空白永远不读：会读出一段莫名的停顿，后面还排着队
        if (text.isBlank()) return false
        // 心里想的永远不念，上帝视角也不例外
        if (isInnerThought) return false
        // 不读自己刚打完的字，两种模式下都成立
        if (speakerSeat != null && speakerSeat == selfSeat) return false
        if (readEverything) return true
        return isPublicSpeech &&
            speakerSeat != null // 上帝播报没有座位，普通视角下不读
    }
}
