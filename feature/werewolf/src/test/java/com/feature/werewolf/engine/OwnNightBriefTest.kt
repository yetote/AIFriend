package com.feature.werewolf.engine

import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 白天的上下文里要带上「本人昨夜的判断」。
 *
 * 对局里出现过这样的割裂：7 号女巫夜里毒 10 号时已经推出「2 号是猎人 → 1 号假预言家 →
 * 4 号真」，可到了第 3 天白天她还在说「结论不变，4 号是悍跳狼，投 4 号」。
 * 她的判断写在夜里 NIGHT_ACT 的 text 里，而白天的 context 只喂公开消息——
 * 那条结论被自己的过滤挡掉了。
 *
 * 这里测的是拼上下文的纯函数：只捞本人 NIGHT_ACT，不带队友夜聊，不泄底。
 */
class OwnNightBriefTest {

    /** 5 人局：1、2 狼，3 预言家，4 女巫，5 平民。 */
    private fun game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.VILLAGER to 1,
            ),
        ),
        roster = (1..5).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.VILLAGER,
        ),
    )

    private val director = WerewolfDirector(emptyMap())

    /** 造一条夜里行动记录。audience 只有本人——和导演里各身份夜间行动一致。 */
    private fun nightAct(day: Int, seat: Int, phase: Phase, text: String) = WerewolfMessage(
        day = day,
        phase = phase,
        seat = seat,
        kind = MessageKind.NIGHT_ACT,
        text = text,
        audience = setOf(seat),
    )

    @Test
    fun `女巫夜里想通的结论在白天能看到`() {
        val table = WerewolfTable(game()).append(
            nightAct(
                day = 2,
                seat = 4,
                phase = Phase.NIGHT_WITCH,
                text = "我用毒药毒 10 号。2 号是猎人坐实 1 号假验，4 号才是真预言家。",
            )
        )
        val brief = director.ownNightBrief(table, seat = 4, today = 3)

        assertTrue("女巫要能记起自己夜里推出的结论：$brief", brief.contains("4 号才是真预言家"))
        assertTrue("要标出这是昨夜的判断：$brief", brief.contains("昨夜"))
    }

    @Test
    fun `别人的夜间判断进不来`() {
        val table = WerewolfTable(game()).append(
            nightAct(day = 2, seat = 3, phase = Phase.NIGHT_SEER, text = "查验 5 号是狼人，这是预言家的私密结论。")
        )
        val brief = director.ownNightBrief(table, seat = 4, today = 3)

        assertFalse("女巫不能看到预言家的夜间结论：$brief", brief.contains("查验 5 号"))
        assertTrue("没有本人的夜间记录就不该有这段：$brief", brief.isEmpty())
    }

    @Test
    fun `狼队夜聊不会被当成本人判断捞进来`() {
        // 狼队夜聊是 WOLF_CHAT（audience 是全体狼），不是 NIGHT_ACT。
        // 即便某个狼人自己发言，也不该在这一段里复述——它走 wolfPlan 私有通道。
        val wolfChat = WerewolfMessage(
            day = 2,
            phase = Phase.NIGHT_WOLF_CHAT,
            seat = 1,
            kind = MessageKind.WOLF_CHAT,
            text = "我们定刀 8 号，白天统一由 1 号悍跳、我冲锋。",
            audience = setOf(1, 2),
        )
        val table = WerewolfTable(game()).append(wolfChat)
        val brief = director.ownNightBrief(table, seat = 1, today = 3)

        assertTrue("狼队夜聊不该出现在昨夜判断段：$brief", brief.isEmpty())
    }

    @Test
    fun `只回看最近两夜不让陈年判断挤占预算`() {
        val table = WerewolfTable(game())
            .append(nightAct(day = 1, seat = 4, phase = Phase.NIGHT_WITCH, text = "第一夜的旧判断，不应该再出现。"))
            .append(nightAct(day = 2, seat = 4, phase = Phase.NIGHT_WITCH, text = "第二夜的判断 A。"))
            .append(nightAct(day = 3, seat = 4, phase = Phase.NIGHT_WITCH, text = "第三夜的判断 B。"))
        val brief = director.ownNightBrief(table, seat = 4, today = 3)

        assertTrue("最近的判断要在：$brief", brief.contains("第三夜的判断 B"))
        assertFalse("三天前的旧判断该被挤掉：$brief", brief.contains("第一夜的旧判断"))
    }

    @Test
    fun `提醒别在白天直接说出夜里的私有结论`() {
        val table = WerewolfTable(game()).append(
            nightAct(day = 2, seat = 4, phase = Phase.NIGHT_WITCH, text = "查验类私有结论。")
        )
        val brief = director.ownNightBrief(table, seat = 4, today = 3)

        assertTrue("必须提醒别直接说出来：$brief", brief.contains("别在白天"))
        assertTrue("必须提醒拿它修正站边和票：$brief", brief.contains("站边"))
    }
}
