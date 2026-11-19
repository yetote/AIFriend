package com.feature.werewolf.engine.persistence

import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「对局文件」的读写。
 *
 * 这份文件的唯一用处是**换一台设备接着看重、接着演**，所以判据不是「能解析出个东西」，
 * 而是「导回来的局面和导出前一模一样」。差一个字段，演出里就会少一段夜聊、
 * 或者死了的人重新站起来投票。
 */
class WerewolfGameFileTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    private fun table(): WerewolfTable =
        WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster(8)))

    private fun ok(read: WerewolfGameFile.Read): WerewolfGameFile.Read.Ok {
        assertTrue("应该能读出来，实际是 $read", read is WerewolfGameFile.Read.Ok)
        return read as WerewolfGameFile.Read.Ok
    }

    private fun reason(read: WerewolfGameFile.Read): String {
        assertTrue("应该被拒绝，实际是 $read", read is WerewolfGameFile.Read.Rejected)
        return (read as WerewolfGameFile.Read.Rejected).reason
    }

    @Test
    fun `导出的对局文件再读回来，局面一模一样`() {
        val original = table()
        val text = WerewolfGameFile.encode(original, id = "g-1", createdAt = 1_700_000_000_000L)

        val game = ok(WerewolfGameFile.read(text)).game

        assertEquals("整局必须完全相等", original, game.table)
    }

    @Test
    fun `文件里带着这是哪一局、什么时候开的`() {
        val text = WerewolfGameFile.encode(table(), id = "g-1", createdAt = 1_700_000_000_000L)

        val game = ok(WerewolfGameFile.read(text)).game

        // 列表按开局时间排，id 用来判「这一局是不是已经在列表里了」
        assertEquals("g-1", game.id)
        assertEquals(1_700_000_000_000L, game.createdAt)
    }

    @Test
    fun `随便一段 JSON 不会被当成对局文件`() {
        val text = """{"brand":"openai","apiKey":"sk-xxx","model":"gpt-x"}"""

        val message = reason(WerewolfGameFile.read(text))

        assertTrue("要说清楚这不是对局文件，实际是：$message", message.contains("不是对局文件"))
    }

    @Test
    fun `空文件有专门的提示`() {
        assertEquals("这个文件是空的", reason(WerewolfGameFile.read("   \n ")))
    }

    @Test
    fun `来自更新版本的文件会请你升级，而不是说文件坏了`() {
        val text = WerewolfGameFile.encode(table(), id = "g-1", createdAt = 1L)
            .replace("\"fileVersion\":1", "\"fileVersion\":9")

        val message = reason(WerewolfGameFile.read(text))

        assertTrue("要提示升级，实际是：$message", message.contains("更新版本"))
    }

    @Test
    fun `被截断的文件不会崩，而是报内容不完整`() {
        val text = WerewolfGameFile.encode(table(), id = "g-1", createdAt = 1L)
        val truncated = text.take(text.length / 2)

        val message = reason(WerewolfGameFile.read(truncated))

        assertTrue("截断的文件不该被当成好好的，实际是：$message", message.isNotBlank())
    }

    @Test
    fun `裸快照也认：早期玩家直接贴出去的就是这一段`() {
        // 没有信封，只有快照本体。内容是对的，就该收——拒绝一个内容正确的文件
        // 比多写十几行兼容代码更让人难受
        val original = table()
        val snapshot = WerewolfSnapshot.encode(original)

        val game = ok(WerewolfGameFile.read(snapshot)).game

        assertEquals(original, game.table)
        // 没写 id 和开局时间：交给上层补，不能拿空串当 id 落库
        assertEquals("", game.id)
        assertEquals(0L, game.createdAt)
    }

    @Test
    fun `玩家设定跟着对局文件一起搬走`() {
        val original = WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster(8)))
        val prompt = "你们说话短一点。"

        val game = ok(WerewolfGameFile.read(WerewolfGameFile.encode(original, "g1", 1000L, prompt))).game

        assertEquals("换台手机导回来，复盘的设定不该少一块", prompt, game.customPrompt)
        assertEquals(original, game.table)
    }

    @Test
    fun `没有设定的文件导进来就是 null`() {
        val original = WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster(8)))

        val game = ok(WerewolfGameFile.read(WerewolfGameFile.encode(original, "g1", 1000L))).game

        assertNull(game.customPrompt)
    }

    @Test
    fun `文件里有别的应用的同名字段也不影响判断`() {
        // 有 version 但没有 players：不是快照，别硬解成一局空牌桌
        val text = """{"version":3,"items":[1,2,3],"name":"some other app config blob"}"""

        val message = reason(WerewolfGameFile.read(text))

        assertTrue(message.contains("不是对局文件"))
    }
}
