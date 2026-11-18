package com.feature.werewolf.export

import com.ui.werewolf.data.UIWerewolfBallotLine
import com.ui.werewolf.data.UIWerewolfCheckReport
import com.ui.werewolf.data.UIWerewolfDayReport
import com.ui.werewolf.data.UIWerewolfDialogueDay
import com.ui.werewolf.data.UIWerewolfGroupKind
import com.ui.werewolf.data.UIWerewolfNightReport
import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfRoleGroup
import com.ui.werewolf.data.UIWerewolfSpeechLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出文档的单测。
 *
 * 重点不是「Markdown 语法对不对」，而是**复盘页上有的东西，导出里不能少**：
 * 底牌、票、夜里的刀、查验、逐条原话。少一段的话，导出去的文件看着挺完整，
 * 实际是删改过的版本，玩家拿它跟自己的记忆对账时对不上。
 */
class WerewolfMarkdownTest {

    private fun review(): UIWerewolfReview = UIWerewolfReview(
        winnerLabel = "好人胜",
        wolfWon = false,
        reason = "狼人全部出局",
        facts = "8 人局 · 打到第 2 天 · 3 人出局",
        selfLine = "你是 4 号 · 女巫 · 好人阵营，活到了最后。",
        groups = listOf(
            UIWerewolfRoleGroup(
                kind = UIWerewolfGroupKind.WOLF,
                title = "狼人",
                members = listOf(
                    UIWerewolfRevealedSeat(
                        seat = 3,
                        name = "张三",
                        roleLabel = "狼人",
                        alive = false,
                        fate = "第 2 天被票出",
                        votes = 3,
                    ),
                ),
            ),
            UIWerewolfRoleGroup(
                kind = UIWerewolfGroupKind.GOD,
                title = "神职",
                members = listOf(
                    UIWerewolfRevealedSeat(seat = 4, name = "你", roleLabel = "女巫", isSelf = true),
                ),
            ),
        ),
        days = listOf(
            UIWerewolfDayReport(
                day = 2,
                result = "放逐 3 号（3 票）",
                exiled = true,
                extraRounds = 1,
                ballots = listOf(
                    UIWerewolfBallotLine(voter = 1, target = 3, key = true),
                    UIWerewolfBallotLine(voter = 2, target = null),
                ),
            ),
        ),
        nights = listOf(
            UIWerewolfNightReport(
                day = 1,
                wolfLine = "狼刀 6 号",
                guardLine = "守卫守 6 号",
                witchLine = null,
                deathLine = "平安夜",
            ),
        ),
        checks = listOf(UIWerewolfCheckReport(day = 1, target = 3, isWolf = true)),
        dialogues = listOf(
            UIWerewolfDialogueDay(
                day = 1,
                lines = listOf(
                    UIWerewolfSpeechLine(
                        seat = 3,
                        speaker = "3 号 张三",
                        label = "狼人夜聊",
                        text = "刀 6 号。\n别刀预言家。",
                        secret = true,
                        roleLabel = "狼人",
                        roleKind = UIWerewolfGroupKind.WOLF,
                    ),
                    UIWerewolfSpeechLine(
                        seat = null,
                        speaker = "上帝",
                        label = "播报",
                        text = "天亮了",
                    ),
                    UIWerewolfSpeechLine(
                        seat = 4,
                        speaker = "4 号 你",
                        label = "发言",
                        text = "我是好人",
                        roleLabel = "女巫",
                        roleKind = UIWerewolfGroupKind.GOD,
                        reasoning = "我在心里怀疑 3 号，但先不暴露。",
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `复盘页上的每一段都在导出里`() {
        val md = WerewolfMarkdown.of(review())

        listOf(
            "# 狼人杀复盘 · 好人胜",
            "> 狼人全部出局",
            "8 人局 · 打到第 2 天 · 3 人出局",
            "你是 4 号 · 女巫 · 好人阵营，活到了最后。",
            "## 底牌",
            "### 狼人",
            "- **3 号 张三** · 狼人 · 第 2 天被票出 · 共 3 票",
            "### 神职",
            "- **4 号 你**（你） · 女巫",
            "## 逐日票型",
            "### 第 2 天",
            "**放逐 3 号（3 票）**（加投 1 轮）",
            "- 1 号 → 3 号",
            "- 2 号 → 弃票",
            "## 夜间行动",
            "| 夜 | 狼刀 | 守卫 | 女巫 | 结果 |",
            "| 第 1 夜 | 狼刀 6 号 | 守卫守 6 号 | — | 平安夜 |",
            "## 预言家查验",
            "- 第 1 夜 · 验 3 号 → 狼人",
            "## 逐日对话",
            "### 第 1 天",
            "**3 号 张三**（狼人） · 狼人夜聊 · 密谈",
            "> 刀 6 号。",
            "> 别刀预言家。",
            "**4 号 你**（女巫） · 发言",
            "> 我是好人",
            "<details>",
            "<summary>思考过程</summary>",
            "> 我在心里怀疑 3 号，但先不暴露。",
            "</details>",
        ).forEach { fragment ->
            assertTrue("导出里少了：$fragment", md.contains(fragment))
        }
    }

    @Test
    fun `密谈全量导出，但先声明它原本只有少数人可见`() {
        val md = WerewolfMarkdown.of(review())
        assertTrue(md.contains("含狼人夜聊与神职夜间行动"))
        assertTrue(md.contains("刀 6 号"))
    }

    @Test
    fun `上帝播报没有身份标签`() {
        val md = WerewolfMarkdown.of(review())
        assertTrue(md.contains("**上帝** · 播报"))
        assertFalse(md.contains("**上帝**（"))
    }

    @Test
    fun `多行正文每一行都带引用前缀`() {
        val md = WerewolfMarkdown.of(review())
        val first = md.indexOf("> 刀 6 号。")
        val second = md.indexOf("> 别刀预言家。")
        assertTrue("两行都要在", first >= 0 && second > first)
    }

    @Test
    fun `没有数据的段落不写标题，也不编内容`() {
        val md = WerewolfMarkdown.of(
            UIWerewolfReview(
                winnerLabel = "狼人胜",
                wolfWon = true,
                reason = "好人全部出局",
                facts = "8 人局",
                selfLine = null,
            )
        )
        assertTrue(md.contains("# 狼人杀复盘 · 狼人胜"))
        assertFalse("没底牌就别摆空标题", md.contains("## 底牌"))
        assertFalse("没票型就别摆空标题", md.contains("## 逐日票型"))
        assertFalse(md.contains("## 夜间行动"))
        assertFalse(md.contains("## 逐日对话"))
        assertFalse(md.contains("0 票"))
    }

    @Test
    fun `旧档标明数据不全`() {
        val md = WerewolfMarkdown.of(
            UIWerewolfReview(
                winnerLabel = "好人胜",
                wolfWon = false,
                reason = "狼人全部出局",
                facts = "8 人局",
                selfLine = null,
                incomplete = true,
            )
        )
        assertTrue(md.contains("## 说明"))
        assertTrue(md.contains("旧版存档"))
    }

    @Test
    fun `表格里的竖线要转义，否则一行会劈成两列`() {
        val md = WerewolfMarkdown.of(
            review().copy(
                nights = listOf(
                    UIWerewolfNightReport(
                        day = 1,
                        wolfLine = "狼刀 6|7 号",
                        guardLine = null,
                        witchLine = null,
                        deathLine = "平安夜",
                    ),
                ),
            )
        )
        assertTrue(md.contains("狼刀 6\\|7 号"))
        assertFalse(md.contains("| 狼刀 6|7 号 |"))
    }
}
