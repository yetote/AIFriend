package com.feature.werewolf.route

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfGroupKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 复盘映射的测试。
 *
 * 这一层不做判定，但很容易「漏人」：分组漏一类身份、票型引用了不存在的座位、
 * 观战局冒出一行「你是几号」。这些在界面上都只是少一行或写错一句话，
 * 编译器管不了，所以用随机对局当输入跑一遍。
 */
class WerewolfReviewMappingTest {

    private fun roster(n: Int) = List(n) { RosterEntry("p${it + 1}", "${it + 1}号") }

    /** 随机自走到分出胜负。只求覆盖各种结局，不在这里验规则。 */
    private fun playOut(start: WerewolfGame, rng: Random): WerewolfGame {
        var game = start
        var steps = 0
        while (!game.isFinished) {
            check(steps++ < 800) { "局面没打完：第 ${game.day} 天 ${game.phase}" }
            game = when (game.phase) {
                Phase.NIGHT_GUARD -> game.guardProtect(
                    if (rng.nextInt(4) == 0) null else game.guardTargets.random(rng),
                )

                Phase.NIGHT_WOLF_CHAT -> game.beginWolfVote()

                Phase.NIGHT_WOLF -> {
                    // 刀口只能落在好人身上：引擎现在会拒绝刀队友/刀自己
                    val target = game.aliveSeats.filter { !game.isWerewolf(it) }.random(rng)
                    game.wolfVote(game.aliveWolves().associate { it.seat to target })
                }

                Phase.NIGHT_SEER -> {
                    val seer = game.alivePlayers.first { it.role == WerewolfRole.SEER }
                    game.seerCheck(game.aliveSeats.filter { it != seer.seat }.random(rng))
                }

                Phase.NIGHT_WITCH -> {
                    val witch = game.alivePlayers.first { it.role == WerewolfRole.WITCH }
                    when {
                        // 自救只在第一夜合法，这里走必然合法的路径，规则本身由规则测试覆盖
                        game.witchCanHeal && game.tonightVictim != witch.seat && rng.nextBoolean() ->
                            game.witchAct(heal = true)

                        game.witchCanPoison && game.aliveSeats.size > 1 && rng.nextBoolean() ->
                            game.witchAct(
                                heal = false,
                                poisonTarget = game.aliveSeats.filter { it != witch.seat }.random(rng),
                            )

                        else -> game.witchAct(heal = false)
                    }
                }

                Phase.DAY_DISCUSSION -> game.beginVote()

                Phase.DAY_VOTE -> {
                    val voters = game.votingSeats
                    val candidates = game.voteCandidates ?: voters
                    game.castVotes(
                        voters.associateWith {
                            if (rng.nextInt(5) == 0) null else candidates.random(rng)
                        },
                    )
                }

                Phase.DAY_SHERIFF_SIGNUP ->
                    game.signUpSheriff(game.aliveSeats.filter { rng.nextBoolean() }.toSet())

                Phase.DAY_SHERIFF_SPEECH -> game.beginSheriffVote()

                Phase.DAY_SHERIFF_VOTE -> {
                    val candidates = game.sheriffCandidates
                    game.castSheriffVotes(
                        game.sheriffVoters.associateWith {
                            if (candidates.isEmpty() || rng.nextInt(5) == 0) null
                            else candidates.random(rng)
                        },
                    )
                }

                Phase.DAY_LAST_WORDS -> game.finishLastWords()

                Phase.HUNTER_SHOOT -> game.hunterShoot(game.aliveSeats.randomOrNull(rng))

                Phase.FINISHED -> game
            }
        }
        return game
    }

    private fun reviewOf(game: WerewolfGame, humanSeat: Int?) = WerewolfUiState(
        table = WerewolfTable(game),
        finished = true,
    ).toReview(humanSeat = humanSeat, avatars = emptyMap())

    @Test
    fun `随机对局都能翻成复盘，而且不丢人`() {
        repeat(24) { seed ->
            val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(seed)), Random(seed + 7_000))
            val review = reviewOf(game, humanSeat = 1)
            assertNotNull("种子 $seed 翻不出复盘", review)
            review!!

            assertEquals(
                "每个座位都要出现且只出现一次",
                game.players.map { it.seat }.sorted(),
                review.groups.flatMap { it.members }.map { it.seat }.sorted(),
            )
            assertTrue("每一局至少有一夜", review.nights.isNotEmpty())
            assertFalse("新打的局不该标成数据不全", review.incomplete)
            assertTrue("人数要写进去：${review.facts}", review.facts.contains("8 人局"))
            assertTrue("胜负文案：${review.winnerLabel}", review.winnerLabel.endsWith("胜"))
            assertTrue("要有结束原因", review.reason.isNotBlank())
            assertNotNull("参与局要有身份行", review.selfLine)
            assertTrue("身份行要带上座位号：${review.selfLine}", review.selfLine!!.contains("你是 1 号"))

            val valid = game.players.map { it.seat }.toSet()
            review.days.forEach { day ->
                assertTrue("第 ${day.day} 天没有结果", day.result.isNotBlank())
                day.ballots.forEach { ballot ->
                    assertTrue("投票人 ${ballot.voter} 不存在", ballot.voter in valid)
                    ballot.target?.let { assertTrue("被投的 $it 号不存在", it in valid) }
                }
            }
        }
    }

    @Test
    fun `观战局的复盘没有你的身份行`() {
        val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(3)), Random(11))
        val review = reviewOf(game, humanSeat = null)

        assertNotNull(review)
        assertNull("观战没有「你是几号」", review!!.selfLine)
    }

    @Test
    fun `旧档续下来的局会标明复盘信息不全`() {
        val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(5)), Random(17))
        // 模拟 v1 存档：夜间与票型记录都是空的
        val legacy = game.copy(nights = emptyList(), voteRounds = emptyList())
        val review = reviewOf(legacy, humanSeat = null)

        assertNotNull(review)
        assertTrue("缺数据就要说缺数据", review!!.incomplete)
        assertTrue(review.nights.isEmpty())
        assertTrue(review.days.isEmpty())
        // v1 存档连逐条消息也没有，复盘不能凭空造出对话
        assertTrue(review.dialogues.isEmpty())
        // 但没有记录也不能丢掉底牌
        assertEquals(8, review.groups.flatMap { it.members }.size)
    }

    @Test
    fun `复盘里有每一夜的明细：谁做了什么、为什么`() {
        // 只给结果（「狼刀 5 号」）的复盘等于一张死亡表，看不出这一局输在哪一步。
        // 明细要来自夜间阶段消息的**原话**，理由那一半不能丢。
        val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(9)), Random(23))
        val table = WerewolfTable(
            game = game,
            transcript = listOf(
                WerewolfMessage(
                    day = 1, phase = Phase.NIGHT_WOLF, seat = 3, kind = MessageKind.WOLF_CHAT,
                    text = "我们定刀 5 号。5 号是女巫，昨晚交了解药。",
                    audience = setOf(3, 4),
                ),
                WerewolfMessage(
                    day = 1, phase = Phase.NIGHT_GUARD, seat = 6, kind = MessageKind.NIGHT_ACT,
                    text = "我守 5 号。他昨晚发言少。",
                    audience = setOf(6),
                ),
                WerewolfMessage(
                    day = 1, phase = Phase.NIGHT_SEER, seat = 2, kind = MessageKind.NIGHT_ACT,
                    text = "查验 5 号。他发言不像好人。",
                    audience = setOf(2),
                ),
                WerewolfMessage(
                    day = 1, phase = Phase.NIGHT_WITCH, seat = 7, kind = MessageKind.NIGHT_ACT,
                    text = "我用解药。救下 5 号。",
                    audience = setOf(7),
                ),
            ),
        )
        val review = WerewolfUiState(table = table, finished = true)
            .toReview(humanSeat = 1, avatars = emptyMap())

        assertNotNull(review)
        val detail = review!!.nightDetails.single()
        assertEquals(1, detail.day)
        assertTrue("狼队定刀：${detail.wolfPlan}", detail.wolfPlan!!.contains("我们定刀 5 号"))
        assertEquals("我守 5 号", detail.guardPlan)
        assertTrue("预言家验人：${detail.seerPlan}", detail.seerPlan!!.contains("查验 5 号"))
        assertTrue("女巫用药：${detail.witchPlan}", detail.witchPlan!!.contains("我用解药"))
        // 原话也要留着，理由在摘要之外
        assertEquals(4, detail.lines.size)
        assertTrue(detail.lines.any { it.text.contains("5 号是女巫") })
    }

    @Test
    fun `没有夜间消息时不要摆空明细`() {
        val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(5)), Random(19))
        val review = reviewOf(game, humanSeat = null)
        assertNotNull(review)
        assertTrue(review!!.nightDetails.isEmpty())
    }

    @Test
    fun `复盘把局内原话按天摊开，密谈不混进公开部分`() {
        val game = playOut(WerewolfGame.deal(WerewolfConfig(), roster(8), Random(9)), Random(23))
        val table = WerewolfTable(
            game = game,
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    seat = 3,
                    kind = MessageKind.WOLF_CHAT,
                    text = "今晚刀 5 号",
                    audience = setOf(3, 4),
                ),
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_DISCUSSION,
                    seat = 1,
                    kind = MessageKind.SPEECH,
                    text = "我是好人，先听 3 号怎么说。",
                    reasoning = "先不暴露信息，听狼怎么带节奏。",
                ),
                WerewolfMessage(
                    day = 1,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    seat = null,
                    kind = MessageKind.DIVINE,
                    text = "天亮了，5 号出局",
                ),
            ),
        )
        // 头像按好友 id 查，不是按座位：这里取 1 号实际绑的那个 id，别假设座位顺序
        val selfId = game.players.first { it.seat == 1 }.id
        val review = WerewolfUiState(table = table, finished = true)
            .toReview(humanSeat = 1, avatars = mapOf(selfId to "/tmp/a.png"))

        assertNotNull(review)
        // 同一天的消息合成一段，顺序沿用 transcript 的先后
        assertEquals(1, review!!.dialogues.size)
        val day = review.dialogues.first()
        assertEquals(1, day.day)
        assertEquals(listOf("今晚刀 5 号", "我是好人，先听 3 号怎么说。", "天亮了，5 号出局"), day.lines.map { it.text })
        assertTrue("这一天有密谈", day.hasSecret)
        // 公开部分不能漏出狼人夜聊
        assertEquals(listOf("我是好人，先听 3 号怎么说。", "天亮了，5 号出局"), day.open.map { it.text })

        val wolfChat = day.lines.first()
        assertTrue("密谈要标出来", wolfChat.secret)
        assertEquals("狼人夜聊", wolfChat.label)
        assertEquals(3, wolfChat.seat)
        assertTrue("要带上座位号：${wolfChat.speaker}", wolfChat.speaker.contains("3 号"))

        // 身份跟着对话行走：复盘时不该再要求玩家翻回底牌区对座位
        val roleOf3 = game.players.first { it.seat == 3 }.role
        assertEquals("要带上发言者的身份", roleOf3.displayName, wolfChat.roleLabel)
        assertEquals(
            "分组决定标签配色",
            when {
                roleOf3.isWerewolf -> UIWerewolfGroupKind.WOLF
                roleOf3.isGod -> UIWerewolfGroupKind.GOD
                else -> UIWerewolfGroupKind.VILLAGER
            },
            wolfChat.roleKind,
        )

        val speech = day.open.first()
        assertFalse(speech.secret)
        assertEquals("/tmp/a.png", speech.avatarPath)
        assertEquals(
            "思考过程跟着原话一起进复盘，对局里已经藏了一整局",
            "先不暴露信息，听狼怎么带节奏。",
            speech.reasoning,
        )

        // 上帝播报没有座位，界面上居中显示
        assertNull(day.open.last().seat)
        assertEquals("上帝", day.open.last().speaker)
        assertNull("上帝没有身份标签", day.open.last().roleLabel)
    }
}
