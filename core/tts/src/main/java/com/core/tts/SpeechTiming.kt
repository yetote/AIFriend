package com.core.tts

import kotlin.math.min

/**
 * 朗读的超时预算。
 *
 * 为什么必须有：朗读是**串行**的，一句念完才 poll 下一句，而狼人杀的显示闸门
 * 又跟着朗读进度走。只要有一句的回调丢了（合成连接被服务端挂住、MediaPlayer
 * 的 prepared/completion 在个别 ROM 上不回调），队列就永远停在这一句上：
 * 后面既没有声音，`revealedIndex` 也不再前进，界面上就是「说到一半，
 * 之后什么消息都没有了」。
 *
 * 所以每一句都要有一个「再慢也该念完了」的上限，超了就跳过这句继续下一句。
 * 声音是锦上添花，文字的进度不能陪葬。
 */
object SpeechTiming {

    /** 单句合成（网络请求）的硬超时。跟句子长短无关——合成本身很快，慢就是在挂。 */
    const val SYNTH_TIMEOUT_MS: Long = 20_000L

    /** 估算一句「合成 + 播放」要多久。字数越多念得越久，但再多也有个上限。 */
    fun totalTimeoutMs(text: String): Long {
        // 中文 TTS 大致 4~5 字/秒；按 300ms/字 留足余量，再叠一个固定开销
        // （建连 + 合成首帧 + MediaPlayer 起播）。
        val speaking = text.length.toLong() * 300L
        return min(MAX_MS, BASE_MS + speaking).coerceAtLeast(MIN_MS)
    }

    /**
     * 「追平」等待的总上限：引擎在一个阶段边界上最多停这么久等朗读。
     *
     * 为什么需要：让引擎跟着朗读走之后，引擎的往下推进就绑在了朗读队列上。
     * 队列自己会跳超时的句子（见 [totalTimeoutMs]），理论上一定会推进；
     * 但「理论上」不够——一旦哪天真卡住，游戏会永久停在半路，比抢跑糟得多。
     * 这个上限保证最坏情况下引擎自己走，界面最多看到一次「进度跳一段」。
     */
    const val CATCH_UP_TIMEOUT_MS: Long = 300_000L

    private const val BASE_MS = 25_000L
    private const val MIN_MS = 25_000L
    private const val MAX_MS = 120_000L
}
