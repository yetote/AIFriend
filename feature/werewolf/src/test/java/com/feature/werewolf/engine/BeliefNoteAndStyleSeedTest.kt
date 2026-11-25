package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfBoards
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.prompt.roles.WolfStyleCards
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 私密狼坑本（beliefNotes）与狼队开局风向卡（wolfStyleSeed）的端到端编排测试。
 *
 * 重点守护两条红线：
 * 1. **私密狼坑本严格隔离**：某座位的 `note` 只写进它自己的 `game.beliefNotes[seat]`，
 *    下一轮只注入它本人的上下文，绝不泄漏给任何其他座位；且并发阶段（nightPending / vote）互不覆盖。
 * 2. **狼队风向卡第 1 夜自动抽取且受板子配置约束**：不会在无警长板子抽到抢徽/吞徽流，
 *    不会在无女巫板子抽到骗药/盗银水流，也不会在单狼板子抽到双狼悍跳。
 */
class BeliefNoteAndStyleSeedTest {

    private data class RecordedCall(
        val seat: Int,
        val systemPrompt: String,
        val userText: String,
    )

    private class RecordingAI(
        private val seat: Int,
        private val onCall: (RecordedCall) -> String,
    ) : IAI {
        override suspend fun requestModel() = Unit
        override var modelSet: MutableSet<String> = mutableSetOf()
        override var brandName: String = "测试"
        override var company: String = "测试"

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            systemPrompt: String?,
            enableThinking: Boolean,
            jsonMode: Boolean,
            webSearch: Boolean,
        ): String {
            val userText = messages.joinToString("\n") { it.content }
            return onCall(RecordedCall(seat, systemPrompt.orEmpty(), userText))
        }

        @Deprecated("测试用不到")
        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    /** 8 人带守卫+警长：1、2 狼；3 预言家；4 女巫；5 守卫；6 猎人；7、8 平民。 */
    private fun guard8Game(): WerewolfGame = WerewolfGame.withRoles(
        config = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.WITCH to 1,
                WerewolfRole.GUARD to 1,
                WerewolfRole.HUNTER to 1,
                WerewolfRole.VILLAGER to 2,
            ),
            hasSheriff = true,
        ),
        roster = roster(8),
        roles = listOf(
            WerewolfRole.WEREWOLF,
            WerewolfRole.WEREWOLF,
            WerewolfRole.SEER,
            WerewolfRole.WITCH,
            WerewolfRole.GUARD,
            WerewolfRole.HUNTER,
            WerewolfRole.VILLAGER,
            WerewolfRole.VILLAGER,
        ),
    )

    @Test
    fun `投票写下的私密狼坑本只回注给本人，绝不泄漏给其他座位，且超长自动截断`() = runBlocking {
        val game = guard8Game().copy(day = 1, phase = Phase.DAY_VOTE)
        val longNote = "A".repeat(200)
        val recordedCalls = mutableListOf<RecordedCall>()

        val agents = (1..8).associateWith { seat ->
            val ai = RecordingAI(seat) { call ->
                recordedCalls += call
                when {
                    call.systemPrompt.contains("只能从") || call.systemPrompt.contains("可以从") -> {
                        val note = when (seat) {
                            3 -> "3号私密心证：1号和2号共边，明天验7号"
                            6 -> longNote
                            else -> ""
                        }
                        val target = if (seat == 1) 7 else 1
                        """{"target": $target, "reason": "按逻辑投。", "note": "$note"}"""
                    }
                    else -> "我先听一圈。"
                }
            }
            SeatAgent(ai = ai, model = "mock")
        }

        val director = WerewolfDirector(agents = agents, random = Random(42))
        val afterVote = director.vote(WerewolfTable(game))

        assertEquals("3号私密心证：1号和2号共边，明天验7号", afterVote.game.beliefNotes[3])
        assertEquals(WerewolfGame.BELIEF_NOTE_MAX_CHARS, afterVote.game.beliefNotes[6]?.length)
        assertFalse("空 note 不写入映射", afterVote.game.beliefNotes.containsKey(4))

        // 复盘可见：3 号自己的私密投票消息 reasoning 带上笔记更新
        val seat3VoteMsg = afterVote.transcript.first { it.seat == 3 && it.kind == MessageKind.VOTE }
        assertTrue(
            "3 号私密投票消息应记录狼坑本更新",
            seat3VoteMsg.reasoning.contains("【私密狼坑本更新】3号私密心证：1号和2号共边，明天验7号"),
        )

        // 进入下一天白天讨论，验证只有 3 号本人看到 3 号的心证，别人绝对看不到
        recordedCalls.clear()
        val nextDayTable = afterVote.copy(
            game = afterVote.game.copy(
                day = 2,
                phase = Phase.DAY_DISCUSSION,
                spokenPhaseKey = "",
                spokenSeats = emptySet(),
            )
        )
        director.discussion(nextDayTable)

        val seat3Call = recordedCalls.first { it.seat == 3 }
        assertTrue(
            "3 号白天发言上下文必须注入自己的私密狼坑本",
            seat3Call.userText.contains("【你的私密狼坑本") &&
                seat3Call.userText.contains("3号私密心证：1号和2号共边，明天验7号"),
        )

        recordedCalls.filter { it.seat != 3 }.forEach { otherCall ->
            assertFalse(
                "${otherCall.seat} 号绝不能看到 3 号的私密狼坑本",
                otherCall.userText.contains("3号私密心证"),
            )
        }
    }

    @Test
    fun `并发夜间行动合并各方私密狼坑本互不覆盖，且首夜狼聊自动抽取风向卡`() = runBlocking {
        val game = guard8Game().copy(day = 1, phase = Phase.NIGHT_PENDING)
        val wolfChatPrompts = mutableListOf<String>()

        val agents = (1..8).associateWith { seat ->
            val ai = RecordingAI(seat) { call ->
                val sys = call.systemPrompt
                when {
                    sys.contains("守卫") && call.userText.contains("守护") ->
                        """{"target": 3, "reason": "首夜保预。", "note": "守卫心证：首夜守3"}"""
                    sys.contains("预言家") && call.userText.contains("查验") ->
                        """{"target": 1, "reason": "验首置位。", "note": "预言家心证：验1号"}"""
                    sys.contains("商议结束，现在各自定刀") ->
                        """{"target": 6, "reason": "刀外置位。", "note": "狼${seat}心证：刀6号"}"""
                    else -> {
                        if (seat in setOf(1, 2)) wolfChatPrompts += sys
                        "今晚先刀 6 号，明天按计划走。"
                    }
                }
            }
            SeatAgent(ai = ai, model = "mock")
        }

        val director = WerewolfDirector(agents = agents, random = Random(7))
        val afterPending = director.nightPending(WerewolfTable(game))

        assertEquals("守卫心证：首夜守3", afterPending.game.beliefNotes[5])
        assertEquals("预言家心证：验1号", afterPending.game.beliefNotes[3])
        assertEquals("狼1心证：刀6号", afterPending.game.beliefNotes[1])
        assertEquals("狼2心证：刀6号", afterPending.game.beliefNotes[2])
        assertNotNull("首夜狼聊必须抽出本局狼队风向卡", afterPending.game.wolfStyleSeed)
        assertTrue(
            "风向卡必须注入第 1 夜狼聊提示词",
            wolfChatPrompts.any { it.contains(afterPending.game.wolfStyleSeed!!) },
        )
    }

    @Test
    fun `狼队风向卡按板子配置过滤，不抽与板子冲突的套路`() {
        // 12 人预女猎守有警长：5 张卡都能抽到
        val guard12 = WerewolfBoards.configOf(WerewolfBoards.GUARD_12)
        val rolled12 = (0 until 120).map { WolfStyleCards.pick(guard12, Random(it)) }.toSet()
        assertTrue("12 人守卫有警长应能抽到多种流派（实际 ${rolled12.size} 种）", rolled12.size >= 4)

        // 6 人无女巫、无警长、2 狼板：绝不抽到双狼悍跳、狼踩狼、骗药盗银水、控徽吞徽
        val tiny = WerewolfConfig(
            composition = mapOf(
                WerewolfRole.WEREWOLF to 2,
                WerewolfRole.SEER to 1,
                WerewolfRole.HUNTER to 1,
                WerewolfRole.VILLAGER to 2,
            ),
            hasSheriff = false,
        )
        repeat(40) { seed ->
            val card = WolfStyleCards.pick(tiny, Random(seed))
            assertTrue("极简板只应抽到稳健控场流：$card", card.startsWith("稳健控场流"))
        }
    }
}
