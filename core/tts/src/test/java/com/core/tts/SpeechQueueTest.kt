package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {

    private fun candidates(vararg speakable: Boolean) =
        speakable.mapIndexed { index, can -> SpeechCandidate("第 $index 句", can) }

    @Test
    fun `音色和TTS配置跟着消息一起排队不丢`() {
        val queue = SpeechQueue()
        val all = listOf(
            SpeechCandidate("甲说的", true, voiceId = "voice-a", ttsConfigId = "cfg-minimax"),
            SpeechCandidate("乙说的", true, voiceId = "voice-b", ttsConfigId = "cfg-custom"),
            SpeechCandidate("播报", false), // 没配的消息
        )

        queue.offer(all)

        val a = queue.poll()
        assertEquals("voice-a", a?.voiceId)
        assertEquals("cfg-minimax", a?.ttsConfigId)
        val b = queue.poll()
        assertEquals("voice-b", b?.voiceId)
        assertEquals("cfg-custom", b?.ttsConfigId)
        val c = queue.poll()
        assertEquals("没配的应为 null，交给引擎默认", null, c?.voiceId)
        assertEquals("没配的应为 null，交给引擎默认", null, c?.ttsConfigId)
    }

    @Test
    fun `同一份消息流反复喂进来只入队一次`() {
        val queue = SpeechQueue()
        val all = candidates(true, true, true)

        assertEquals(3, queue.offer(all).size)
        assertTrue("整表重传不该再入队", queue.offer(all).isEmpty())
        assertTrue("第三次也不该入队", queue.offer(all).isEmpty())
    }

    @Test
    fun `新增的消息接着上次往下入队`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true))

        // 界面拿到的是整表：前两条是旧的，第三条才是新的
        val added = queue.offer(candidates(true, true, true))

        assertEquals(1, added.size)
        assertEquals(2, added.first().index)
        assertEquals("第 2 句", added.first().text)
    }

    @Test
    fun `不该朗读的消息也入队 只是标记成不出声`() {
        val queue = SpeechQueue()
        // 下标 1 是自己说的话，下标 2 是上帝播报：都不读，但必须入队否则显示进度会卡住
        val added = queue.offer(candidates(true, false, false, true))

        assertEquals(4, added.size)
        assertFalse(added[1].speakable)
        assertFalse(added[2].speakable)
        assertEquals(4, queue.size)
    }

    @Test
    fun `按原顺序排队 先入先出`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true, true))

        assertEquals(0, queue.poll()?.index)
        assertEquals(1, queue.poll()?.index)
        assertEquals(2, queue.poll()?.index)
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `消费进度只跟 poll 走 不入队不推进`() {
        val queue = SpeechQueue()
        assertEquals("初始什么都没显示", -1, queue.revealedIndex)

        queue.offer(candidates(true, true, true))
        assertEquals("入队不代表显示", -1, queue.revealedIndex)

        queue.poll()
        assertEquals(0, queue.revealedIndex)
        queue.poll()
        assertEquals(1, queue.revealedIndex)
    }

    @Test
    fun `不朗读的消息消费后也推进显示进度`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, false, false, true))

        queue.poll() // 0：读出声
        queue.poll() // 1：自己说的话，瞬间过
        queue.poll() // 2：播报，瞬间过

        assertEquals("不朗读的消息也不能卡住显示", 2, queue.revealedIndex)
    }

    @Test
    fun `队列末尾的非朗读消息不会永远藏在闸门后`() {
        val queue = SpeechQueue()
        // 典型的一天：发言 → 投票 → 天亮播报。末尾两条都不朗读。
        queue.offer(candidates(true, false, false))

        while (!queue.isEmpty) queue.poll()

        assertEquals("全部消费完，显示进度要到末尾", 2, queue.revealedIndex)
    }

    @Test
    fun `换局时从当前长度重新开始 历史不补读但全显示`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true, true))
        queue.poll()
        assertEquals(2, queue.size)

        // 续档：消息流已经有 20 条历史，从 20 开始，不念昨天的发言
        queue.reset(fromIndex = 20)
        assertTrue("换局要清掉未读完的", queue.isEmpty)
        assertEquals("历史全部显示", 19, queue.revealedIndex)

        val withHistory = (0 until 20).map { SpeechCandidate("历史 $it", true) }
        assertTrue(queue.offer(withHistory).isEmpty())

        val fresh = withHistory + SpeechCandidate("新的一句", true)
        val added = queue.offer(fresh)
        assertEquals(1, added.size)
        assertEquals("新的一句", added.first().text)
    }

    @Test
    fun `消息流变短时游标收回来而不是越界`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true, true, true))

        // 存档被删/切到别的局，消息流突然只剩一条
        val added = queue.offer(candidates(true))
        assertTrue(added.isEmpty())

        // 再长回来时仍能正常入队，不会因为游标卡在 4 而永远沉默
        assertEquals(1, queue.offer(candidates(true, true)).size)
    }

    @Test
    fun `跳过关闭期间积下的发言 重开后不补念也不补显示`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true))
        assertEquals(1, queue.size)

        // 朗读关着的时候又来了三条，采集器没往里放；重开时直接跳到当前末尾
        queue.skipTo(index = 4)
        assertTrue(queue.isEmpty)
        assertEquals("关着期间的消息全算已显示", 3, queue.revealedIndex)

        val all = candidates(true, true, true, true)
        assertTrue("关着时那三条不该被补念", queue.offer(all).isEmpty())

        val fresh = all + SpeechCandidate("刚说的", true)
        val added = queue.offer(fresh)
        assertEquals(1, added.size)
        assertEquals("刚说的", added.first().text)
    }

    @Test
    fun `暂停清掉未读完的 但不影响后续新增`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true))
        queue.clear()
        assertTrue(queue.isEmpty)

        assertEquals(1, queue.offer(candidates(true, true, true)).size)
    }
}
