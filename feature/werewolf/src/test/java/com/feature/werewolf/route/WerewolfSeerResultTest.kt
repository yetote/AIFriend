package com.feature.werewolf.route

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.SeatAgent
import com.feature.werewolf.engine.WerewolfDirector
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfVerdict
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预言家查验的结论必须真的到得了玩家眼前。
 *
 * 预言家是全游戏唯一能「查身份」的角色，他的查验结果就是他的命根子。
 * AI 预言家能从提示词里读到 `seerChecks`，**人类预言家没有提示词**——
 * 他只能看牌桌。所以引擎把结论写进私有消息还不够，这条消息还必须
 * 以「结论」的样子出现在他的记录流里，而不是一行压暗的小字。
 *
 * 这一层曾经整个是空的：`nightSeer` 只写「查验 N 号」加一句理由，
 * 谁是狼一个字都不说，于是人类预言家查完人什么都不知道。
 */
class WerewolfSeerResultTest {

    /** 1、2 号是狼，3 号预言家，4、5 号好人。 */
    private fun game(phase: Phase = Phase.NIGHT_SEER): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.VILLAGER to 2,
            ),
        ),
        roster = (1..5).map { RosterEntry(id = "p$it", name = "玩家$it") },
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    ).copy(phase = phase)

    /** 只回固定 JSON 的假模型。 */
    private class ScriptedAI(private val reply: String) : IAI {
        override var modelSet: MutableSet<String> = mutableSetOf("m")
        override var brandName: String = "Fake"
        override var company: String = "Fake"

        override suspend fun requestModel() = Unit

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
            webSearch: Boolean,
        ): String = reply

        @Deprecated("测试替身不走单条消息接口")
        override suspend fun chat(message: String, model: String): String = reply
    }

    // ------------------------------------------------------------------
    // 引擎层：结论要写进那条私有消息
    // ------------------------------------------------------------------

    @Test
    fun `查验狼人的那条私有消息要写明是狼`() = runBlocking {
        val director = WerewolfDirector(
            mapOf(3 to SeatAgent(ScriptedAI("""{"target": 1, "reason": "1 号发言像狼。"}"""), "m"))
        )

        val result = director.nightSeer(WerewolfTable(game()))

        val msg = result.transcriptFor(3).single { it.kind == MessageKind.NIGHT_ACT }
        assertTrue(
            "查验结论必须出现在消息里，否则人类预言家永远不知道验出了什么：${msg.text}",
            msg.text.contains("狼人"),
        )
    }

    @Test
    fun `查验好人的那条私有消息要写明是好人`() = runBlocking {
        val director = WerewolfDirector(
            mapOf(3 to SeatAgent(ScriptedAI("""{"target": 4, "reason": "4 号像好人。"}"""), "m"))
        )

        val result = director.nightSeer(WerewolfTable(game()))

        val msg = result.transcriptFor(3).single { it.kind == MessageKind.NIGHT_ACT }
        assertTrue(
            "查验结论必须出现在消息里：${msg.text}",
            msg.text.contains("好人"),
        )
    }

    @Test
    fun `结论不能出现在公开消息里`() = runBlocking {
        val director = WerewolfDirector(
            mapOf(3 to SeatAgent(ScriptedAI("""{"target": 1, "reason": "1 号像狼。"}"""), "m"))
        )

        val result = director.nightSeer(WerewolfTable(game()))

        assertEquals(
            "查验结论是私有信息，不能进公开消息",
            0,
            result.publicTranscript.count { it.text.contains("狼人") },
        )
    }

    // ------------------------------------------------------------------
    // 呈现层：结论要以「结论」的样子出现在预言家的记录流里
    // ------------------------------------------------------------------

    private fun tableWithCheck(targetSeat: Int = 1): WerewolfTable {
        val base = WerewolfTable(game(Phase.NIGHT_SEER))
        // seerCheck 只在 NIGHT_SEER 阶段允许调用，所以先查再把阶段推回白天
        val checked = base.game.seerCheck(targetSeat).copy(phase = Phase.DAY_DISCUSSION)
        return base.copy(
            game = checked,
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.NIGHT_SEER,
                    seat = 3,
                    kind = MessageKind.NIGHT_ACT,
                    text = "查验 $targetSeat 号：狼人。",
                    audience = setOf(3),
                )
            ),
        )
    }

    @Test
    fun `预言家在自己的牌桌上看得见查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        val line = live.lines.singleOrNull { it.text.contains("狼人") }
        assertTrue("预言家必须在自己的记录流里看到结论", line != null)
        assertTrue("结论行要标成只有自己看得见", line!!.secret)
    }

    @Test
    fun `查验结论在牌桌上要醒目 不能是一行压暗的心里话`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        val line = live.lines.single { it.text.contains("狼人") }
        assertEquals(
            "查验结论是这局最关键的一条私有情报，不能跟「投票念头」共用排版",
            com.ui.werewolf.data.UIWerewolfLineKind.SEER_RESULT,
            line.kind,
        )
    }

    @Test
    fun `好人看不到别人的查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 4, turn = null, avatars = emptyMap())

        assertTrue(
            "查验结论只给预言家本人：${live.lines.map { it.text }}",
            live.lines.none { it.text.contains("狼人") },
        )
    }

    @Test
    fun `观战视角看得到查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertTrue(
            "观战是上帝视角，查验结论本来就该摊开",
            live.lines.any { it.text.contains("狼人") },
        )
        // secret 说的是「这条在真实牌桌上只有预言家看得到」，
        // 观战者看到的是上帝视角，但这条消息本身仍然是私有的
        assertTrue(
            "查验结论在真实牌桌上就是私有消息",
            live.lines.first { it.text.contains("狼人") }.secret,
        )
    }

    // ------------------------------------------------------------------
    // 验人本：预言家白天要靠它报验人
    // ------------------------------------------------------------------

    @Test
    fun `预言家牌桌上有一份验人本`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertEquals(1, live.selfChecks.size)
        assertEquals(1, live.selfChecks.single().seat)
        assertTrue("验人本要带着结论", live.selfChecks.single().isWerewolf)
    }

    @Test
    fun `好人的牌桌上没有验人本`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 4, turn = null, avatars = emptyMap())

        assertTrue(
            "验人本是私有信息，好人不能拿到：${live.selfChecks}",
            live.selfChecks.isEmpty(),
        )
    }

    @Test
    fun `观战视角没有验人本`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertTrue(
            "验人本只属于预言家本人，上帝视角走复盘页那一节",
            live.selfChecks.isEmpty(),
        )
    }

    // ------------------------------------------------------------------
    // 座位徽标：结论要长在被验的那个座位下面
    //
    // 记录流里那条「查验 3 号：狼人」会被几十条发言埋掉，验人本又是一行小字。
    // 预言家白天报验人、对账，全靠「3 号头像下面就写着狼人」这一眼。
    // ------------------------------------------------------------------

    @Test
    fun `预言家在被验座位下面看得见结论`() {
        val live = WerewolfUiState(table = tableWithCheck(targetSeat = 1), running = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertEquals(
            "1 号是狼，结论要挂在 1 号座位上",
            UIWerewolfVerdict.WOLF,
            live.seats.first { it.seat == 1 }.seerVerdict,
        )
        assertTrue(
            "没被验的座位不能有结论",
            live.seats.filter { it.seat != 1 }.all { it.seerVerdict == null },
        )
    }

    @Test
    fun `验出好人时座位上挂的是好人`() {
        val live = WerewolfUiState(table = tableWithCheck(targetSeat = 4), running = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertEquals(
            "4 号是好人",
            UIWerewolfVerdict.GOOD,
            live.seats.first { it.seat == 4 }.seerVerdict,
        )
    }

    @Test
    fun `好人的座位上没有查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 4, turn = null, avatars = emptyMap())

        assertTrue(
            "查验结论是私有情报，好人不能拿到：${live.seats.map { it.seerVerdict }}",
            live.seats.all { it.seerVerdict == null },
        )
    }

    @Test
    fun `狼人的座位上没有查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = 1, turn = null, avatars = emptyMap())

        assertTrue(
            "狼人拿到预言家的查验结论，等于开局就知道谁是自己人",
            live.seats.all { it.seerVerdict == null },
        )
    }

    @Test
    fun `上帝视角不挂查验结论 那一格留给身份名`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = true)
            .toLive(humanSeat = null, turn = null, avatars = emptyMap())

        assertTrue(
            "观战时所有人的身份都摊开着，结论是多余的信息",
            live.seats.all { it.seerVerdict == null },
        )
        assertTrue(
            "观战时徽标那一格挂的是身份名",
            live.seats.all { it.roleLabel != null },
        )
    }

    @Test
    fun `已结束的局不挂查验结论`() {
        val live = WerewolfUiState(table = tableWithCheck(), running = false, finished = true)
            .toLive(humanSeat = 3, turn = null, avatars = emptyMap())

        assertTrue(
            "结束后身份全部公开，徽标那一格归身份名",
            live.seats.all { it.seerVerdict == null },
        )
    }
}
