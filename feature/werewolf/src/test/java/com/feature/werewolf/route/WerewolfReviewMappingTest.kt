package com.feature.werewolf.route

import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
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
                    val target = game.aliveSeats.random(rng)
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
        // 但没有记录也不能丢掉底牌
        assertEquals(8, review.groups.flatMap { it.members }.size)
    }
}
