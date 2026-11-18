package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 朗读超时预算：只要这一层算错，一句长发言就会被提前掐断，
 * 或者一句挂住的请求会把队列（以及跟着走的界面文字）多卡半分钟。
 */
class SpeechTimingTest {

    @Test
    fun `短句也至少有最低预算`() {
        // 「好」这种一个字的气口，建连 + 合成 + 起播本身就够慢，
        // 不能按字数算出一个几秒的超时把正常朗读打断
        assertTrue(SpeechTiming.totalTimeoutMs("好") >= 25_000L)
        assertEquals(25_000L, SpeechTiming.totalTimeoutMs(""))
    }

    @Test
    fun `预算随字数增长`() {
        val short = SpeechTiming.totalTimeoutMs("我上警，我是 1 号。")
        val medium = SpeechTiming.totalTimeoutMs("我上警。我是1号龙娘，平民身份，逻辑清晰、记性极好。")
        assertTrue("长发言应该给更长的预算", medium > short)
    }

    @Test
    fun `超长发言不会无限加码`() {
        // 狼人杀的「上帝播报」偶尔会拼出很长一段；再长也得有个上限，
        // 否则一句卡住的请求能把队列按住好几分钟
        val veryLong = "啊".repeat(5_000)
        assertEquals(120_000L, SpeechTiming.totalTimeoutMs(veryLong))
    }

    @Test
    fun `合成超时比单句总预算短`() {
        // 合成本身很快，慢就是连接被挂住了：它必须早于「播放 + 合成」的总预算触发，
        // 否则总超时先到，日志里只会看到「朗读超时」，看不出是卡在网络还是卡在播放
        assertTrue(SpeechTiming.SYNTH_TIMEOUT_MS < SpeechTiming.totalTimeoutMs("短短一句"))
    }
}
