package com.feature.werewolf.engine

import com.core.ai.entity.IAI
import com.core.ai.model.ChatMessage
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SpeechDirection
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmJudgeTest {

    private fun game6(): WerewolfGame {
        val roles = listOf(
            WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF,
            WerewolfRole.SEER, WerewolfRole.WITCH,
            WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
        )
        return WerewolfGame.withRoles(
            config = WerewolfConfig(composition = roles.groupingBy { it }.eachCount(), hasSheriff = true),
            roster = List(6) { RosterEntry("p${it + 1}", "玩家${it + 1}") },
            roles = roles,
        ).copy(phase = Phase.DAY_DISCUSSION, sheriffSeat = 6).setSpeechDirection(SpeechDirection.SHERIFF_LEFT)
    }

    private fun fakeAi(block: suspend (List<ChatMessage>, String?, Boolean) -> String): IAI = object : IAI {
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
        ): String = block(messages, systemPrompt, jsonMode)

        override suspend fun chat(message: String, model: String): String =
            chat(listOf(ChatMessage.user(message)), model)
    }

    @Test
    fun `sanitize过滤越界座位与自己验自己并归一化结论`() {
        val dto = GmJudge.GmExtractDto(
            claimRole = "真预言家",
            checks = listOf(
                SpeechCheckFact(target = 3, verdict = "金水"),
                SpeechCheckFact(target = 3, verdict = "好人"),
                SpeechCheckFact(target = 1, verdict = "查杀"),
                SpeechCheckFact(target = 99, verdict = "狼人"),
            ),
            badgeFlow = listOf(2, 3, 2, 4, 5, 99),
            witchReports = listOf(
                SpeechWitchFact("捞", 4),
                SpeechWitchFact("毒", 3),
            ),
            voteTarget = 5,
            conditionalVoteTargets = listOf(5, 6, 99),
        )
        val clean = GmJudge.sanitize(dto, speakerSeat = 3, phase = Phase.DAY_DISCUSSION, validSeats = (1..6).toSet())

        assertEquals("预言家", clean.claimRole)
        assertEquals(listOf(SpeechCheckFact(1, "狼人")), clean.checks)
        assertEquals(listOf(2, 4, 5), clean.badgeFlow)
        assertEquals(listOf(SpeechWitchFact("救", 4)), clean.witchReports)
        assertEquals(5, clean.voteTarget)
        assertEquals(listOf(6), clean.conditionalVoteTargets)
    }

    @Test
    fun `裁判模型异常时安全回退null不阻断对局`() = runBlocking {
        val broken = GmAgent(
            ai = fakeAi { _, _, _ -> throw RuntimeException("429 rate limit") },
            model = "broken-free",
        )
        val out = GmJudge.judgeSpeech(
            gm = broken,
            game = game6(),
            speakerSeat = 3,
            phase = Phase.DAY_DISCUSSION,
            speechText = "我跳预言家，今天出 1 号。",
        )
        assertNull(out.facts)
    }

    @Test
    fun `导演调度公开发言后并发由GM抽取事实并记录GM用量`() = runBlocking {
        val playerAi = fakeAi { _, _, _ -> "如果 3 号反咬就改投 3 号，否则我今天投 2 号。" }
        val gmAi = fakeAi { _, systemPrompt, jsonMode ->
            assertTrue("GM 应走 JSON 结构化模式", jsonMode)
            assertTrue(systemPrompt.orEmpty().contains("裁判"))
            """{"claimRole": null, "retractedSeer": false, "checks": [], "badgeFlow": [], "witchReports": [], "voteTarget": 2, "conditionalVoteTargets": [3]}"""
        }
        val director = WerewolfDirector(
            agents = (1..6).associateWith { SeatAgent(playerAi, "player-model") },
            gmAgent = GmAgent(gmAi, "free-gm-flash"),
        )
        val result = director.discussion(WerewolfTable(game6()))

        val firstSpeech = result.transcript.first { it.kind == MessageKind.SPEECH }
        assertNotNull("应挂上 GM 抽取结果", firstSpeech.facts)
        assertEquals(2, firstSpeech.facts?.voteTarget)
        assertEquals(listOf(3), firstSpeech.facts?.conditionalVoteTargets)
        assertTrue("GM 估算或实测用量应记录在 gmUsage", firstSpeech.gmUsage.total > 0)
    }
}
