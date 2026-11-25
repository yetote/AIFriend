package com.feature.werewolf.route

import com.feature.werewolf.engine.BallotCard
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WitchStock
import com.feature.werewolf.export.WerewolfMarkdown
import com.feature.werewolf.replay.ReplayTimeline
import com.feature.werewolf.vm.WerewolfReplay
import com.ui.werewolf.data.UIWerewolfFate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 牌桌体验增强功能的信息差边界测试。
 *
 * 重点守护「上帝观战模式」与「人类游玩模式」的严格隔离：
 * 1. 游玩模式绝不下发任何 AI 的私密狼坑本（`beliefNote`），好人绝不拿到狼队风向卡（`wolfStyleCard`）；
 * 2. 女巫「药水本」与守卫「守护本」严格只给对应人类身份本人，不给其他身份也不占上帝视角；
 * 3. 公开台账（`publicLedger`）严格受朗读进度闸门（`revealedIndex`）与公开性双重过滤，零未来剧透、零私密泄露；
 * 4. 复盘演出非上帝视角下绝不泄露真实狼阵营、刀/毒死因、狼队风向卡或终局心证。
 */
class WerewolfTableBoundaryTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    /** 8 人局：1、2 狼；3 预言家；4 女巫；5 守卫；6 猎人；7、8 平民。 */
    private fun sampleTable(): WerewolfTable {
        val base = WerewolfGame.withRoles(
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
        val game = base.copy(
            day = 2,
            phase = Phase.DAY_DISCUSSION,
            players = base.players.map { p ->
                when (p.seat) {
                    6 -> p.copy(alive = false, deathCause = DeathCause.WITCH_POISON, deathDay = 2)
                    else -> p
                }
            },
            witch = WitchStock(antidoteAvailable = false, poisonAvailable = false),
            lastGuardedSeat = 3,
            tonightGuardedSeat = 4,
            nights = listOf(
                NightRecord(day = 1, wolfTarget = 7, guarded = 3, healed = true, deaths = emptyList()),
                NightRecord(day = 2, wolfTarget = 8, guarded = 4, healed = false, poisonTarget = 6, deaths = listOf(6)),
            ),
            beliefNotes = mapOf(
                1 to "狼1心证：装民冲票，把3号真预打成悍跳",
                3 to "预言家心证：1号查杀，明天验2号",
            ),
            wolfStyleSeed = "【狼踩狼做身份流】两狼制造假对立做高深水狼身份。",
            history = listOf(
                GameEvent.SheriffWithdrawn(day = 1, seats = listOf(2)),
                GameEvent.Dawn(day = 1, deadSeats = emptyList()),
                GameEvent.Dawn(day = 2, deadSeats = listOf(6)),
            ),
        )
        val transcript = listOf(
            // index 0：狼人私有夜聊（绝不能进公开台账）
            WerewolfMessage(
                day = 1, phase = Phase.NIGHT_WOLF_CHAT, seat = 1, kind = MessageKind.WOLF_CHAT,
                text = "我是预言家，昨晚验了7号金水，明天我悍跳。", audience = setOf(1, 2),
            ),
            // index 1：公开预言家发言
            WerewolfMessage(
                day = 1, phase = Phase.DAY_SHERIFF_SPEECH, seat = 3, kind = MessageKind.SPEECH,
                text = "我是真预言家，首夜验了1号是狼人，警徽流留7号、8号。",
            ),
            // index 2：公开女巫第 2 天起跳（用于测朗读闸门截断）
            WerewolfMessage(
                day = 2, phase = Phase.DAY_DISCUSSION, seat = 4, kind = MessageKind.SPEECH,
                text = "我是女巫，第一夜救了7号，昨晚毒了6号。",
            ),
            // index 3：公开唱票
            WerewolfMessage(
                day = 2, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.BALLOT,
                text = "唱票",
                ballot = BallotCard(listOf(Ballot(3, 1), Ballot(7, 1))),
            ),
        )
        return WerewolfTable(game = game, transcript = transcript)
    }

    @Test
    fun `游玩模式下严格隐藏 AI 狼坑本与狼队风向卡，且私有笔记按角色严格隔离`() {
        val state = WerewolfUiState(table = sampleTable(), running = true)

        // 1. 人类是 7 号平民
        val villagerLive = state.toLive(humanSeat = 7, turn = null, avatars = emptyMap(), autoSpeak = false)
        assertTrue("平民视角下所有座位的 beliefNote 必须为 null", villagerLive.seats.all { it.beliefNote == null })
        assertNull("好人游玩模式绝不能看到狼队风向卡", villagerLive.wolfStyleCard)
        assertNull("平民绝不能拿到女巫药水本", villagerLive.selfWitch)
        assertNull("平民绝不能拿到守卫守护本", villagerLive.selfGuard)
        assertEquals("平民看被毒死的人只能看到中性夜死", UIWerewolfFate.NIGHT, villagerLive.seats.first { it.seat == 6 }.fate)
        assertTrue("公开台账含公开发言的验人与银水", villagerLive.publicLedger.contains("3 号称第 1 天验 1 号是狼人"))
        assertTrue("公开台账含起跳女巫的用药", villagerLive.publicLedger.contains("4 号称第 2 天救了 7 号"))
        assertFalse("公开台账绝不泄露狼人夜聊里的假话", villagerLive.publicLedger.contains("1 号自称预言家"))

        // 2. 人类是 4 号女巫
        val witchLive = state.toLive(humanSeat = 4, turn = null, avatars = emptyMap(), autoSpeak = false)
        assertNotNull("女巫本人必须拿到药水本", witchLive.selfWitch)
        assertFalse(witchLive.selfWitch!!.antidoteAvailable)
        assertFalse(witchLive.selfWitch!!.poisonAvailable)
        assertEquals("第1夜救7号", witchLive.selfWitch!!.healedSummary)
        assertEquals("第2夜毒6号", witchLive.selfWitch!!.poisonedSummary)
        assertNull("女巫不能拿到守卫守护本", witchLive.selfGuard)
        assertNull("女巫不能拿到狼队风向卡", witchLive.wolfStyleCard)
        assertTrue("女巫也不能看 AI 私密狼坑本", witchLive.seats.all { it.beliefNote == null })

        // 3. 人类是 5 号守卫
        val guardLive = state.toLive(humanSeat = 5, turn = null, avatars = emptyMap(), autoSpeak = false)
        assertNotNull("守卫本人必须拿到守护本", guardLive.selfGuard)
        assertEquals(listOf("第1夜 守3号", "第2夜 守4号"), guardLive.selfGuard!!.records)
        assertEquals("白天阶段下夜禁连守昨夜刚守的 4 号", 4, guardLive.selfGuard!!.cannotGuardNextSeat)
        assertNull("守卫不能拿到女巫药水本", guardLive.selfWitch)
        assertNull("守卫不能拿到狼队风向卡", guardLive.wolfStyleCard)

        // 夜间守卫行动前：禁连守对象应切换为 lastGuardedSeat（3 号）
        val guardNightTable = sampleTable().let {
            it.copy(game = it.game.copy(phase = Phase.NIGHT_GUARD, lastGuardedSeat = 3, tonightGuardedSeat = null))
        }
        val guardNightLive = WerewolfUiState(table = guardNightTable, running = true)
            .toLive(humanSeat = 5, turn = null, avatars = emptyMap(), autoSpeak = false)
        assertEquals(3, guardNightLive.selfGuard!!.cannotGuardNextSeat)

        // 4. 人类是 1 号狼人
        val wolfLive = state.toLive(humanSeat = 1, turn = null, avatars = emptyMap(), autoSpeak = false)
        assertNotNull("人类狼人可见本局狼队风向卡", wolfLive.wolfStyleCard)
        assertTrue("人类狼人游玩模式也不能看其他 AI 的私密狼坑本", wolfLive.seats.all { it.beliefNote == null })
        assertNull(wolfLive.selfWitch)
        assertNull(wolfLive.selfGuard)
    }

    @Test
    fun `上帝观战模式可见 AI 狼坑本与风向卡，且公开台账严格受朗读进度闸门控制`() {
        val state = WerewolfUiState(table = sampleTable(), running = true)

        // 朗读只推进到 index 1（第 1 天预言家刚说完，第 2 天女巫还没播到）
        val godLive = state.toLive(
            humanSeat = null,
            turn = null,
            avatars = emptyMap(),
            autoSpeak = true,
            speechReady = true,
            revealedIndex = 1,
        )
        assertEquals("狼1心证：装民冲票，把3号真预打成悍跳", godLive.seats.first { it.seat == 1 }.beliefNote)
        assertEquals("预言家心证：1号查杀，明天验2号", godLive.seats.first { it.seat == 3 }.beliefNote)
        assertNotNull("上帝视角可见狼队风向卡", godLive.wolfStyleCard)
        assertNull("上帝视角不显示单人药水本", godLive.selfWitch)
        assertNull("上帝视角不显示单人守护本", godLive.selfGuard)
        assertTrue("已播出的第 1 天验人应入台账", godLive.publicLedger.contains("3 号称第 1 天验 1 号是狼人"))
        assertFalse("未播出的第 2 天女巫起跳绝不能提前剧透进台账", godLive.publicLedger.contains("4 号称"))
    }

    @Test
    fun `复盘演出非上帝视角不泄露狼阵营、刀毒死因、风向卡与未来发言`() {
        val base = sampleTable()
        val withDawn = base.copy(
            transcript = listOf(
                base.transcript[0],
                base.transcript[1],
                WerewolfMessage(
                    day = 2,
                    phase = Phase.DAY_DISCUSSION,
                    seat = null,
                    kind = MessageKind.DIVINE,
                    text = "第2天 天亮：6 号出局",
                ),
                base.transcript[2],
                base.transcript[3],
            )
        )
        val blindFrames = ReplayTimeline.build(withDawn, humanSeat = 7, godView = false)
        // blindFrames[0] 是第 1 天预言家发言（狼夜聊已被过滤）；blindFrames[1] 是第 2 天天亮死讯；blindFrames[2] 是女巫发言
        val blindReplay = WerewolfReplay(
            recordId = "rec-1",
            table = withDawn,
            humanSeat = 7,
            frames = blindFrames,
            cursor = 1,
            godView = false,
            playing = false,
        ).toLive(avatars = emptyMap(), autoSpeak = false, speechReady = false)

        assertTrue("非上帝视角回放不暴露 wolf 标记", blindReplay.seats.none { it.wolf })
        assertTrue("非上帝视角回放不暴露身份", blindReplay.seats.all { it.roleLabel == null })
        assertTrue("回放不提前暴露终局狼坑本", blindReplay.seats.all { it.beliefNote == null })
        assertNull("非上帝视角回放不暴露狼队风向卡", blindReplay.wolfStyleCard)
        assertFalse("游标停在女巫发言之前时台账绝不剧透女巫起跳", blindReplay.publicLedger.contains("4 号称"))

        // 切到上帝视角回放、游标走到女巫发言（含天亮 6 号出局）
        val godFrames = ReplayTimeline.build(withDawn, humanSeat = null, godView = true)
        val godReplay = WerewolfReplay(
            recordId = "rec-1",
            table = withDawn,
            humanSeat = null,
            frames = godFrames,
            cursor = godFrames.lastIndex,
            godView = true,
            playing = false,
        ).toLive(avatars = emptyMap(), autoSpeak = false, speechReady = false)
        assertTrue(godReplay.seats.first { it.seat == 1 }.wolf)
        assertNotNull(godReplay.wolfStyleCard)
        assertTrue("即使上帝视角回放也不拿终局心证剧透前期天数", godReplay.seats.all { it.beliefNote == null })
    }

    @Test
    fun `终局复盘与 Markdown 导出包含狼队开局风向卡`() {
        val finishedTable = sampleTable().let {
            it.copy(game = it.game.copy(phase = Phase.FINISHED, winner = Faction.GOOD))
        }
        val review = WerewolfUiState(table = finishedTable, running = false, finished = true)
            .toReview(humanSeat = 7, avatars = emptyMap())
        assertNotNull(review)
        assertEquals("【狼踩狼做身份流】两狼制造假对立做高深水狼身份。", review!!.wolfStyleCard)
        val md = WerewolfMarkdown.of(review)
        assertTrue("Markdown 导出包含狼队开局风向章节", md.contains("## 本局狼队开局风向"))
        assertTrue(md.contains("狼踩狼做身份流"))
    }
}
