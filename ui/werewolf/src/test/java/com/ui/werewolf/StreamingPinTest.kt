package com.ui.werewolf

import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLineKind
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfStreaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「说到一半消失」的回归用例。
 *
 * 现场：一条发言已经显示出来了，喇叭状态在它生成过程中翻转（上一条念完、这一条入队），
 * 闸门一关，屏幕上的半句话被撤掉，等整段落进记录才重新出现。
 * 这里钉住的是「已经露过面就不再撤回」这条规则。
 */
class StreamingPinTest {

    private fun live(
        lines: List<UIWerewolfLine> = emptyList(),
        running: Boolean = true,
        finished: Boolean = false,
        error: String? = null,
    ) = UIWerewolfLive(
        day = 2,
        phaseLabel = "白天讨论",
        night = false,
        running = running,
        finished = finished,
        winnerLabel = null,
        stageLabel = null,
        lines = lines,
        error = error,
    )

    private fun speech(seat: Int, text: String) =
        UIWerewolfStreaming(seat = seat, speaker = "玩家$seat", text = text)

    private fun landed(seat: Int, text: String) = UIWerewolfLine(
        index = 0,
        kind = UIWerewolfLineKind.SPEECH,
        seat = seat,
        speaker = "玩家$seat",
        text = text,
    )

    @Test
    fun `闸门放行时直接用当前这段`() {
        val now = speech(4, "我觉得 3 号有问题")
        assertEquals(now, resolveStreaming(live(), allowed = now, pinned = speech(4, "我")))
    }

    @Test
    fun `喇叭中途变忙不撤回已经露面的半句`() {
        val half = speech(4, "我觉得 3 号这一票很反常，他明明")
        val got = resolveStreaming(live(), allowed = null, pinned = half)
        assertEquals("半句话要留在屏幕上", half, got)
    }

    @Test
    fun `落进记录后就交还给正式气泡`() {
        val half = speech(4, "我觉得 3 号这一票很反常")
        val got = resolveStreaming(
            live(lines = listOf(landed(4, "我觉得 3 号这一票很反常，而且他一直在跟票。"))),
            allowed = null,
            pinned = half,
        )
        assertNull("已经落库，不能再叠一条正在说", got)
    }

    @Test
    fun `还没吐字的思考阶段不粘`() {
        val got = resolveStreaming(live(), allowed = null, pinned = speech(4, ""))
        assertNull("一个字都没说，交给三个点", got)
    }

    @Test
    fun `局停了或报错就不粘`() {
        val half = speech(4, "我觉得 3 号")
        assertNull(resolveStreaming(live(running = false), null, half))
        assertNull(resolveStreaming(live(finished = true), null, half))
        assertNull(resolveStreaming(live(error = "网络异常"), null, half))
    }

    @Test
    fun `上一句刚落地时继续显示下一位`() {
        // 3 号刚落进记录，4 号已经开口但喇叭还在念 3 号——这时显示的是 4 号，不是 3 号
        val now = speech(4, "我跟 3 号")
        val got = resolveStreaming(
            live(lines = listOf(landed(3, "我怀疑 5 号。"))),
            allowed = null,
            pinned = now,
        )
        assertEquals(now, got)
    }
}
