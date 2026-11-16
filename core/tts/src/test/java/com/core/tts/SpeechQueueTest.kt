package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {

    private fun candidates(vararg speakable: Boolean) =
        speakable.mapIndexed { index, can -> SpeechCandidate("第 $index 句", can) }

    @Test
    fun `同一份消息流反复喂进来只读一次`() {
        val queue = SpeechQueue()
        val all = candidates(true, true, true)

        assertEquals(3, queue.offer(all).size)
        assertTrue("整表重传不该再读", queue.offer(all).isEmpty())
        assertTrue("第三次也不该读", queue.offer(all).isEmpty())
    }

    @Test
    fun `新增的消息接着上次往下读`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true))

        // 界面拿到的是整表：前两条是旧的，第三条才是新的
        val added = queue.offer(candidates(true, true, true))

        assertEquals(1, added.size)
        assertEquals(2, added.first().index)
        assertEquals("第 2 句", added.first().text)
    }

    @Test
    fun `不能读的消息占着下标但不入队`() {
        val queue = SpeechQueue()
        // 下标 1 是自己说的话，下标 2 是上帝播报：都不读，但下标不能跳过
        val added = queue.offer(candidates(true, false, false, true))

        assertEquals(2, added.size)
        assertEquals(0, added[0].index)
        assertEquals(3, added[1].index)
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
    fun `换局时从当前长度重新开始 历史不补读`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true, true, true))
        queue.poll()
        assertEquals(2, queue.size)

        // 续档：消息流已经有 20 条历史，从 20 开始，不念昨天的发言
        queue.reset(fromIndex = 20)
        assertTrue("换局要清掉未读完的", queue.isEmpty)

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

        // 再长回来时仍能正常读，不会因为游标卡在 4 而永远沉默
        assertEquals(1, queue.offer(candidates(true, true)).size)
    }

    @Test
    fun `跳过关闭期间积下的发言 重开后不补念`() {
        val queue = SpeechQueue()
        queue.offer(candidates(true))
        assertEquals(1, queue.size)

        // 朗读关着的时候又来了三条，采集器没往里放；重开时直接跳到当前末尾
        queue.skipTo(index = 4)
        assertTrue(queue.isEmpty)

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
