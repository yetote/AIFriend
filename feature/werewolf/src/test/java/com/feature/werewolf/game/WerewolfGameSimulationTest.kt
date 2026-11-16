package com.feature.werewolf.game

import com.feature.werewolf.game.WerewolfRole.SEER
import com.feature.werewolf.game.WerewolfRole.WITCH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 随机自走测试。
 *
 * 单测只能证明「我想到的路径是对的」，证明不了「状态机一定能走出去」。狼人杀最难受的 bug
 * 不是算错票，而是某个局面卡住——比如空刀夜之后没人死、白天全员弃票，然后永远循环。
 *
 * 这里用固定种子跑大量随机对局，每一步都检查不变量，断言：
 * 1. 任何一局都能在有限步内分出胜负；
 * 2. 中途不会出现「没有胜者、却某一方已经全灭」的矛盾局面；
 * 3. 死人不带活人标签（`alive == false` 必有死因和死亡天数）。
 *
 * 种子固定，失败可精确复现。
 */
class WerewolfGameSimulationTest {

    private fun roster(count: Int): List<RosterEntry> =
        List(count) { RosterEntry(id = "p${it + 1}", name = "${it + 1}号") }

    @Test
    fun `8 人局随机自走全部收敛`() {
        val winners = mutableSetOf<Faction>()
        repeat(60) { seed ->
            val start = WerewolfGame.deal(WerewolfConfig(), roster(8), Random(seed))
            val end = runToEnd(start, Random(seed + 10_000))
            assertNotNull("种子 $seed 没有分出胜负", end.winner)
            assertEquals(Phase.FINISHED, end.phase)
            // 防止「开局立刻判胜」这种假通过：一局真实对局至少要经历天亮播报。
            assertTrue("种子 $seed 没有任何天亮播报", end.history.any { it is GameEvent.Dawn })
            winners += end.winner!!
        }
        // 胜负两个方向都得跑出来，否则说明判胜逻辑可能永远倒向一边。
        assertEquals(setOf(Faction.WEREWOLF, Faction.GOOD), winners)
    }

    @Test
    fun `9 人局随机自走全部收敛`() {
        val config = WerewolfConfig(composition = WerewolfConfig.Standard9)
        repeat(60) { seed ->
            val start = WerewolfGame.deal(config, roster(9), Random(seed))
            val end = runToEnd(start, Random(seed + 20_000))
            assertNotNull("种子 $seed 没有分出胜负", end.winner)
            assertEquals(Phase.FINISHED, end.phase)
        }
    }

    @Test
    fun `屠城规则下随机自走同样收敛`() {
        val config = WerewolfConfig(
            composition = WerewolfConfig.Standard9,
            winRule = WinRule.SLAY_ALL,
        )
        repeat(60) { seed ->
            val start = WerewolfGame.deal(config, roster(9), Random(seed))
            val end = runToEnd(start, Random(seed + 30_000))
            assertNotNull("种子 $seed 没有分出胜负", end.winner)
        }
    }

    /**
     * 一直推进到分出胜负。
     *
     * [maxSteps] 是「卡死」的探测器：正常一局几十步就结束，超过上限说明存在走不出去的局面。
     */
    private fun runToEnd(start: WerewolfGame, rng: Random, maxSteps: Int = 800): WerewolfGame {
        var game = start
        var steps = 0
        while (!game.isFinished) {
            check(steps++ < maxSteps) {
                "局面未收敛：第 ${game.day} 天，阶段 ${game.phase}"
            }
            game = step(game, rng)
            assertInvariants(game)
        }
        return game
    }

    /**
     * 每一套板子都要能自走收敛。
     *
     * 新身份最容易出的问题不是算错，而是**卡住**：守卫阶段找不到活着的守卫、
     * 白痴翻牌后投票人数没变导致永远平票、某个板子凑不出合法的候选人。
     * 这些路径单测想不到，只能靠每套板子都真跑一遍。
     */
    @Test
    fun `所有板子都能随机自走收敛`() {
        val boards = listOf(
            "6 人快局" to WerewolfConfig(composition = WerewolfConfig.Quick6),
            "8 人标准局" to WerewolfConfig(),
            "9 人标准局" to WerewolfConfig(composition = WerewolfConfig.Standard9),
            "10 人守卫局" to WerewolfConfig(composition = WerewolfConfig.Guard10),
            "12 人守卫局" to WerewolfConfig(composition = WerewolfConfig.Guard12),
            "12 人白痴局" to WerewolfConfig(composition = WerewolfConfig.Fool12),
            // 带警长的板子单独跑一遍：竞选、PK、警徽移交都是新状态，随机走位最容易撞出漏清的字段
            "12 人守卫局（警长）" to WerewolfConfig(
                composition = WerewolfConfig.Guard12,
                hasSheriff = true,
                maxVoteRounds = 2,
            ),
            "12 人白痴局（警长）" to WerewolfConfig(
                composition = WerewolfConfig.Fool12,
                hasSheriff = true,
                maxVoteRounds = 2,
            ),
        )
        boards.forEach { (name, config) ->
            repeat(30) { seed ->
                val start = WerewolfGame.deal(config, roster(config.playerCount), Random(seed))
                val end = runToEnd(start, Random(seed + 40_000))
                assertNotNull("$name 种子 $seed 没有分出胜负", end.winner)
                assertEquals("$name 种子 $seed 停在了 ${end.phase}", Phase.FINISHED, end.phase)
                assertEquals("$name 的座位数", config.playerCount, end.players.size)
                assertTrue(
                    "$name 种子 $seed 没有任何天亮播报",
                    end.history.any { it is GameEvent.Dawn },
                )
            }
        }
    }

    /** 随机挑一个合法行动推进一步。 */
    private fun step(game: WerewolfGame, rng: Random): WerewolfGame = when (game.phase) {
        Phase.NIGHT_GUARD -> {
            // 三分之一空守：空守是合法动作，也要被覆盖到
            game.guardProtect(if (rng.nextInt(3) == 0) null else game.guardTargets.random(rng))
        }

        // 商议阶段不改局面，只管往下走；真正的分歧留给下刀那一步
        Phase.NIGHT_WOLF_CHAT -> game.beginWolfVote()

        Phase.NIGHT_WOLF -> {
            val target = game.aliveSeats.random(rng)
            game.wolfVote(game.aliveWolves().map { it.seat }.associateWith { target })
        }

        Phase.NIGHT_SEER -> {
            val seer = game.alivePlayers.first { it.role == SEER }
            val targets = game.aliveSeats.filter { it != seer.seat }
            game.seerCheck(targets.random(rng))
        }

        Phase.NIGHT_WITCH -> {
            val witch = game.alivePlayers.first { it.role == WITCH }
            val poisonable = game.aliveSeats.filter { it != witch.seat }
            // 不模拟自救：自救受「仅第一夜」限制，这里只走必然合法的那条路，规则本身由单测覆盖。
            val canHeal = game.witchCanHeal && game.tonightVictim != witch.seat
            when {
                canHeal && rng.nextInt(3) == 0 -> game.witchAct(heal = true)
                game.witchCanPoison && poisonable.isNotEmpty() && rng.nextInt(3) == 0 ->
                    game.witchAct(heal = false, poisonTarget = poisonable.random(rng))

                else -> game.witchAct(heal = false)
            }
        }

        Phase.DAY_DISCUSSION -> game.atDiscussion().beginVote()

        Phase.DAY_VOTE -> {
            val voters = game.votingSeats
            val candidates = game.voteCandidates ?: voters
            game.castVotes(
                voters.associateWith { if (rng.nextInt(6) == 0) null else candidates.random(rng) },
            )
        }

        // 警长竞选：随机上警、随机投票，把「无人上警 / 单人自动当选 / 多人投票 / 平票 PK」
        // 四种结局都撞一遍，比只写单元测试更能发现状态机漏清字段
        Phase.DAY_SHERIFF_SIGNUP ->
            game.signUpSheriff(game.aliveSeats.filter { rng.nextInt(3) == 0 }.toSet())

        Phase.DAY_SHERIFF_SPEECH -> game.beginSheriffVote()

        Phase.DAY_SHERIFF_VOTE -> {
            val candidates = game.sheriffCandidates
            game.castSheriffVotes(
                game.sheriffVoters.associateWith {
                    if (candidates.isEmpty() || rng.nextInt(6) == 0) null else candidates.random(rng)
                },
            )
        }

        Phase.DAY_LAST_WORDS -> {
            // 警长的遗言里可能交警徽，这里两种都走：交出去，或者直接撕掉
            val sheriff = game.sheriffSeat
            val heir = game.aliveSeats.filter { it != sheriff }
            if (sheriff != null && game.canTransferBadge(sheriff) && heir.isNotEmpty() && rng.nextBoolean()) {
                game.transferBadge(sheriff, heir.random(rng))
            } else {
                game.finishLastWords()
            }
        }

        Phase.HUNTER_SHOOT -> {
            val targets = game.aliveSeats
            if (targets.isNotEmpty() && rng.nextInt(3) > 0) {
                game.hunterShoot(targets.random(rng))
            } else {
                game.hunterShoot(null)
            }
        }

        Phase.FINISHED -> game
    }

    /** 每一步都要成立的性质。这些一旦破了，说明规则层已经自相矛盾。 */
    private fun assertInvariants(game: WerewolfGame) {
        assertEquals(game.players.size, game.players.map { it.seat }.distinct().size)

        game.players.forEach { player ->
            if (player.alive) {
                assertNull("${player.seat} 号活着却有死因", player.deathCause)
            } else {
                assertNotNull("${player.seat} 号已出局但没有死因", player.deathCause)
                assertNotNull("${player.seat} 号已出局但没有死亡天数", player.deathDay)
            }
        }

        if (game.phase == Phase.HUNTER_SHOOT) {
            assertNotNull("猎人阶段没有待开枪的猎人", game.pendingHunterSeat)
        }

        // 警长座位上必须是活人，只有两个窗口例外：他刚死、遗言还没说（要交徽），
        // 或者天亮刚结算完、猎人还在开枪（交徽排在开枪之后）
        // 分出胜负之后不再要求清理：死人名下的警徽是那一局的真实历史，复盘要用
        game.sheriffSeat?.let { seat ->
            if (!game.isFinished && !game.playerAt(seat).alive) {
                assertTrue(
                    "警长 $seat 号已出局，但既不在遗言队列，也不在等枪开完：${game.phase}",
                    (game.phase == Phase.DAY_LAST_WORDS && seat in game.pendingLastWords) ||
                        game.phase == Phase.HUNTER_SHOOT,
                )
            }
        }

        // 上过警的人一定都是活人：竞选发生在第 1 天，而第 1 天之前不可能有人出局
        assertTrue("上警名单里出现了已出局的人：${game.sheriffRunners}", game.sheriffRunners.all { it in game.aliveSeats })

        // 有投票权的人必须都是活人：翻牌的白痴会被排除，但死人绝不能留在里面
        assertTrue(
            "投票名单里出现了已出局的人：${game.votingSeats}",
            game.votingSeats.all { it in game.aliveSeats },
        )

        if (!game.isFinished) {
            // 未结束就意味着双方都还有人在场；否则上一轮结算应该已经判出胜负。
            assertTrue("局面未结束但场上无人：第 ${game.day} 天", game.aliveSeats.isNotEmpty())
            assertTrue("局面未结束但狼人已全灭", game.aliveWolves().isNotEmpty())
            assertTrue("局面未结束但好人已全灭", game.alivePlayers.any { it.faction == Faction.GOOD })
            assertNull("未结束却已有胜者", game.winner)
        }
    }
}
