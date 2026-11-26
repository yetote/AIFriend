package com.feature.werewolf.engine.persistence

import com.core.ai.model.TokenUsage
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.Ballot
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.NightRecord
import com.feature.werewolf.game.PendingDawn
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.SeerCheck
import com.feature.werewolf.game.VoteOutcome
import com.feature.werewolf.game.VoteRound
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WerewolfSpeechLength
import com.feature.werewolf.game.WerewolfTurnLimit
import com.feature.werewolf.game.WitchStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 存档格式的测试。
 *
 * 判据是**整局对象完全相等**，不是抽查几个字段。断点续玩的 bug 几乎都是
 * 「大部分字段回来了，但某一个漏了」，然后在续上的那一刻表现成一局怪游戏——
 * 比如死了的人还能投票、狼人忘了队友。抽查字段恰好抓不到这类问题。
 */
class WerewolfSnapshotTest {

    private fun roster(n: Int) = (1..n).map { RosterEntry("p$it", "玩家$it") }

    /** 一局攒满各种状态的局面：死人、选票、查验、药品消耗、猎人待开枪、胜负已分。 */
    private fun richTable(): WerewolfTable {
        val players = listOf(
            WerewolfPlayer(1, "p1", "玩家1", WerewolfRole.WEREWOLF, alive = true),
            WerewolfPlayer(2, "p2", "玩家2", WerewolfRole.WEREWOLF, alive = false, deathCause = DeathCause.EXILE, deathDay = 2),
            WerewolfPlayer(3, "p3", "玩家3", WerewolfRole.SEER, alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = 2),
            WerewolfPlayer(4, "p4", "玩家4", WerewolfRole.WITCH, alive = true),
            WerewolfPlayer(5, "p5", "玩家5", WerewolfRole.HUNTER, alive = false, deathCause = DeathCause.WITCH_POISON, deathDay = 1),
            WerewolfPlayer(6, "p6", "玩家6", WerewolfRole.VILLAGER, alive = true),
            WerewolfPlayer(7, "p7", "玩家7", WerewolfRole.VILLAGER, alive = false, deathCause = DeathCause.HUNTER_SHOOT, deathDay = 1),
            WerewolfPlayer(8, "p8", "玩家8", WerewolfRole.VILLAGER, alive = true),
        )

        val game = WerewolfGame(
            config = WerewolfConfig(
                composition = WerewolfConfig.Standard8,
                winRule = com.feature.werewolf.game.WinRule.SLAY_ALL,
                maxVoteRounds = 3,
                witchCanSelfHealOnFirstNight = false,
            ),
            players = players,
            day = 3,
            phase = Phase.HUNTER_SHOOT,
            // 非默认的本夜用药决定也要 round-trip：结算已收敛到天亮，续档必须带上它
            witch = WitchStock(
                antidoteAvailable = false, poisonAvailable = true,
                nightHeal = true, nightPoison = 6,
            ),
            wolfVotes = mapOf(1 to 6),
            wolfVoteRound = 2,
            tonightVictim = 6,
            seerChecks = listOf(
                SeerCheck(day = 1, targetSeat = 2, isWerewolf = true),
                SeerCheck(day = 2, targetSeat = 6, isWerewolf = false),
            ),
            voteRound = 2,
            voteCandidates = listOf(1, 4, 6),
            ballots = listOf(
                Ballot(voterSeat = 1, targetSeat = 4),
                Ballot(voterSeat = 4, targetSeat = null),
            ),
            pendingHunterSeat = 5,
            afterHunterPhase = Phase.DAY_DISCUSSION,
            beliefNotes = mapOf(
                1 to "装民冲票，把3号真预打成悍跳",
                4 to "站边3号，狼坑暂定1、2，今晚考虑毒1",
            ),
            wolfStyleSeed = "【狼踩狼做身份流】两名狼人提前约好轻微互踩拉对立，制造假反水做高深水狼身份。",
            history = listOf(
                GameEvent.Dawn(day = 1, deadSeats = listOf(5, 7)),
                GameEvent.Exiled(day = 2, seat = 2, voteCount = 5.0),
                GameEvent.NoExile(day = 2),
                GameEvent.HunterShot(day = 3, hunterSeat = 5, targetSeat = 7),
                GameEvent.GameOver(day = 3, winner = Faction.WEREWOLF),
            ),
            winner = Faction.WEREWOLF,
            nights = listOf(
                NightRecord(
                    day = 1, wolfTarget = 5, healed = false, poisonTarget = 5,
                    deaths = listOf(5), mutualProtection = true,
                ),
                NightRecord(day = 2, wolfTarget = 3, healed = true, deaths = emptyList()),
            ),
            voteRounds = listOf(
                VoteRound(
                    day = 1,
                    round = 1,
                    ballots = listOf(Ballot(1, 2), Ballot(4, null)),
                    outcome = VoteOutcome.TIE,
                ),
                VoteRound(
                    day = 1,
                    round = 2,
                    ballots = listOf(Ballot(1, 2), Ballot(4, 2)),
                    outcome = VoteOutcome.EXILED,
                    exiledSeat = 2,
                    exileVotes = 2.0,
                ),
            ),
        )

        val transcript = listOf(
            WerewolfMessage(
                day = 1, phase = Phase.NIGHT_WOLF, seat = 1, kind = MessageKind.WOLF_CHAT,
                text = "刀 5 号吧。", audience = setOf(1, 2),
            ),
            WerewolfMessage(
                day = 1, phase = Phase.NIGHT_SEER, seat = 3, kind = MessageKind.NIGHT_ACT,
                text = "查验 2 号。", audience = setOf(3),
            ),
            WerewolfMessage(
                day = 1, phase = Phase.DAY_DISCUSSION, seat = 4, kind = MessageKind.SPEECH,
                text = "我觉得 2 号有问题。",
                reasoning = "他昨晚的发言在避重就轻。",
            ),
            WerewolfMessage(
                day = 1, phase = Phase.DAY_VOTE, seat = 6, kind = MessageKind.VOTE,
                text = "我投 2 号。",
            ),
            com.feature.werewolf.engine.BallotCard(
                ballots = listOf(
                    Ballot(1, 2), Ballot(2, 2), Ballot(4, null), Ballot(6, 2),
                ),
                tally = mapOf(2 to 4.5),
            ).let { card ->
                WerewolfMessage(
                    day = 1, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.BALLOT,
                    text = "第 1 天 投票：1→2、2→2、4→弃票、6→2",
                    ballot = card,
                )
            },
            WerewolfMessage(
                day = 1, phase = Phase.DAY_VOTE, seat = null, kind = MessageKind.DIVINE,
                text = "第1天 投票：2 号被放逐（5 票）",
            ),
        )
        return WerewolfTable(game = game, transcript = transcript)
    }

    /**
     * 守卫 + 白痴都在场的一局：把两个新身份的状态一次存全。
     *
     * 7 人自制板：2 狼 · 守卫 · 白痴 · 3 民。用自制板而不是 Guard12/Fool12，
     * 是为了让守卫和白痴同时出现——两个字段都存丢了才看得出来。
     */
    private fun newRolesTable(): WerewolfTable {
        val composition = mapOf(
            WerewolfRole.WEREWOLF to 2,
            WerewolfRole.GUARD to 1,
            WerewolfRole.IDIOT to 1,
            WerewolfRole.VILLAGER to 3,
        )
        val players = listOf(
            WerewolfPlayer(1, "p1", "玩家1", WerewolfRole.WEREWOLF, alive = true),
            WerewolfPlayer(2, "p2", "玩家2", WerewolfRole.WEREWOLF, alive = true),
            WerewolfPlayer(3, "p3", "玩家3", WerewolfRole.GUARD, alive = true),
            WerewolfPlayer(4, "p4", "玩家4", WerewolfRole.IDIOT, alive = true),
            WerewolfPlayer(5, "p5", "玩家5", WerewolfRole.VILLAGER, alive = false, deathCause = DeathCause.WOLF_KILL, deathDay = 1),
            WerewolfPlayer(6, "p6", "玩家6", WerewolfRole.VILLAGER, alive = true),
            WerewolfPlayer(7, "p7", "玩家7", WerewolfRole.VILLAGER, alive = true),
        )
        val game = WerewolfGame(
            config = WerewolfConfig(composition = composition),
            players = players,
            day = 2,
            phase = Phase.NIGHT_GUARD,
            // 上一夜守过 6 号，所以这一夜只能守别人
            lastGuardedSeat = 6,
            tonightGuardedSeat = null,
            // 白痴已经在第一天翻过牌
            idiotRevealed = true,
            history = listOf(
                GameEvent.Dawn(day = 1, deadSeats = listOf(5)),
                GameEvent.IdiotRevealed(day = 1, seat = 4, voteCount = 4.0),
            ),
            nights = listOf(
                NightRecord(
                    day = 1, wolfTarget = 5, guarded = 6, healed = false,
                    deaths = listOf(5), mutualProtection = true,
                ),
            ),
            voteRounds = listOf(
                VoteRound(
                    day = 1,
                    round = 1,
                    ballots = listOf(Ballot(1, 4), Ballot(2, 4), Ballot(3, 4), Ballot(6, 4)),
                    outcome = VoteOutcome.IDIOT_REVEALED,
                    exiledSeat = 4,
                    exileVotes = 4.0,
                ),
            ),
        )
        return WerewolfTable(game = game)
    }

    // ------------------------------------------------------------------

    @Test
    fun `守卫与白痴的状态也能原样存取`() {
        val original = newRolesTable()
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(original))

        assertEquals("整局必须完全相等", original, restored)
        assertEquals(6, restored!!.game.lastGuardedSeat)
        assertTrue(restored.game.idiotRevealed)
        assertEquals(6, restored.game.nights.single().guarded)
        assertEquals(VoteOutcome.IDIOT_REVEALED, restored.game.voteRounds.single().outcome)
        assertTrue(restored.game.history.any { it is GameEvent.IdiotRevealed })
    }

    @Test
    fun `整局存档再读出，和存档前一模一样`() {
        val original = richTable()

        val text = WerewolfSnapshot.encode(original)
        val restored = WerewolfSnapshot.decode(text)

        assertNotNull("应该能读回来", restored)
        assertEquals("整局必须完全相等", original, restored)
    }

    @Test
    fun `空的一局也能原样读回`() {
        val table = WerewolfTable(WerewolfGame.deal(WerewolfConfig(), roster(8)))
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(table))
        assertEquals(table, restored)
    }

    @Test
    fun `唱票卡的票型与半点票数跟着存档走`() {
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(richTable()))!!
        val ballot = restored.transcript.first { it.kind == MessageKind.BALLOT }.ballot!!

        assertEquals("四张票都在", 4, ballot.ballots.size)
        assertEquals("弃票也留着", null, ballot.ballots.first { it.voterSeat == 4 }.targetSeat)
        assertEquals("警长的半点票不能丢", 4.5, ballot.tally.getValue(2), 0.0)
    }

    @Test
    fun `token 用量与估算标记跟着存档走`() {
        val original = richTable()
        val withUsage = original.copy(
            transcript = original.transcript.mapIndexed { index, message ->
                if (index == 0) {
                    message.copy(usage = TokenUsage(input = 123, output = 456, estimated = true))
                } else {
                    message
                }
            }
        )
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(withUsage))!!

        assertEquals(TokenUsage(123, 456, true), restored.transcript.first().usage)
        // 估算标记必须一起存：不然复盘会把本地估算值当成平台账单数展示
        assertTrue(restored.transcript.first().usage.estimated)
        assertEquals(TokenUsage.EMPTY, restored.transcript[1].usage)
    }

    @Test
    fun `自爆未离场的狼跟着存档走`() {
        // 续档后他今晚还要参加商议和定刀。丢了这一项，nightWolfSeats 会少一个人，
        // wolfVote 的键集合校验直接失败——续档那一刻整局卡死。
        val original = richTable()
        val withGhost = original.copy(game = original.game.copy(ghostWolves = setOf(1)))
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(withGhost))!!

        assertEquals(setOf(1), restored.game.ghostWolves)
        assertEquals(withGhost, restored)
    }

    @Test
    fun `私有可见性跟着存档一起保留`() {
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(richTable()))!!

        // 狼人夜聊的 audience 必须还在，否则续上之后夜聊会变成公开消息
        val wolfChat = restored.transcript.first { it.kind == MessageKind.WOLF_CHAT }
        assertEquals(setOf(1, 2), wolfChat.audience)
        assertTrue(wolfChat.visibleTo(1))
        assertTrue("好人依旧看不到", !wolfChat.visibleTo(4))

        val public = restored.transcript.first { it.kind == MessageKind.SPEECH }
        assertNull("公开发言不该带 audience", public.audience)
        assertEquals(
            "思考过程跟着存档走，续档之后复盘不能丢",
            "他昨晚的发言在避重就轻。",
            public.reasoning,
        )
    }

    @Test
    fun `还没公布的天亮播报跟着存档走`() {
        // 第一天死讯压在警长竞选之后：中途存档再续上，不能把死讯提前播出去
        val table = richTable().let { it.copy(game = it.game.copy(pendingDawn = PendingDawn(day = 1, deaths = listOf(5)))) }
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(table))!!

        assertEquals(listOf(5), restored.game.pendingDawn?.deaths)
        assertEquals(1, restored.game.pendingDawn?.day)
    }

    @Test
    fun `守救同归的标记跟着存档走`() {
        // 复盘里那行红字「守救同归」是从这个标记来的，续档后不能丢
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(richTable()))!!

        assertTrue(restored.game.nights.first().mutualProtection)
    }

    @Test
    fun `枚举存的是名字不是序号`() {
        val text = WerewolfSnapshot.encode(richTable())
        // 存序号的话，枚举重排一次所有老存档就全错了
        assertTrue(text.contains("\"WEREWOLF\""))
        assertTrue(text.contains("\"NIGHT_WOLF\""))
        assertTrue(text.contains("\"HUNTER_SHOT\""))
    }

    @Test
    fun `未知字段不影响读取`() {
        val text = WerewolfSnapshot.encode(richTable())
        // 模拟未来版本多写了一个字段，旧版本读到时应该忽略而不是失败
        val withExtra = text.dropLast(1) + ",\"futureField\":123}"
        assertNotNull(WerewolfSnapshot.decode(withExtra))
    }

    @Test
    fun `玩家设定随存档一起走，但不是局面的一部分`() {
        val prompt = "你们说话尽量短，别写小作文。"
        val table = richTable()
        val text = WerewolfSnapshot.encode(table, prompt)

        assertEquals("复盘要能查到当时那份设定", prompt, WerewolfSnapshot.decodePrompt(text))
        // 提示词是请求参数、不是状态：读回来的局面不该因为它有任何差别
        assertEquals(table, WerewolfSnapshot.decode(text))
        // 没写设定就没有这一段
        assertNull(WerewolfSnapshot.decodePrompt(WerewolfSnapshot.encode(table)))
    }

    @Test
    fun `旧档没有玩家设定时读出来是 null，整局照样能读`() {
        val prompt = "旧的那份设定"
        val text = WerewolfSnapshot.encode(richTable(), prompt)
        // 模拟 v10 的档：没有 customPrompt 这个键，版本号也更低
        val legacy = text
            .replace("\"customPrompt\":\"$prompt\",", "")
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":10")

        assertTrue("替换没生效，序列化格式变了：$text", legacy != text)
        assertNull("旧档没有这一段", WerewolfSnapshot.decodePrompt(legacy))
        assertNotNull("但局面必须还能续上", WerewolfSnapshot.decode(legacy))
    }

    @Test
    fun `思考时限与本局关掉的思考跟着存档走`() {
        val table = richTable()
        val text = WerewolfSnapshot.encode(
            table = table,
            turnLimit = WerewolfTurnLimit.RELAXED,
            thinkingOff = setOf(3, 7),
        )

        val pacing = WerewolfSnapshot.decodePacing(text)
        assertNotNull("这一局的档位必须读得回来", pacing)
        assertEquals(WerewolfTurnLimit.RELAXED, pacing!!.limit)
        // 续档之后如果把已经关掉的座位又打开，那个慢模型下一阶段会原样再超时一次
        assertEquals("关掉思考的座位要一起带回来", setOf(3, 7), pacing.thinkingOff)
        // 档位是请求参数、不是局面：读回来的牌桌不该因为它有任何差别
        assertEquals(table, WerewolfSnapshot.decode(text))
    }

    @Test
    fun `旧档没有思考时限时按标准处理`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"turnLimit\":\"STANDARD\",", "")
            .replace("\"thinkingOff\":[],", "")
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":12")

        assertTrue("替换没生效，序列化格式变了：$text", !text.contains("\"turnLimit\""))
        val pacing = WerewolfSnapshot.decodePacing(text)
        assertEquals("默认标准", WerewolfTurnLimit.STANDARD, pacing?.limit)
        assertEquals("默认一个都没关", emptySet<Int>(), pacing?.thinkingOff)
        assertNotNull("局面照常能续", WerewolfSnapshot.decode(text))
    }

    @Test
    fun `认不出的思考时限按标准处理而不是整局读不出来`() {
        // 存档可能来自更高版本：将来加了新档位，旧版本读到时不能把整局判成坏档
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"turnLimit\":\"STANDARD\"", "\"turnLimit\":\"TURBO\"")
        assertEquals(WerewolfTurnLimit.STANDARD, WerewolfSnapshot.decodePacing(text)?.limit)
        assertNotNull(WerewolfSnapshot.decode(text))
    }

    @Test
    fun `发言长度跟着存档走`() {
        val text = WerewolfSnapshot.encode(
            table = richTable(),
            speechLength = WerewolfSpeechLength.SHORT,
        )

        assertEquals(WerewolfSpeechLength.SHORT, WerewolfSnapshot.decodePacing(text)?.speechLength)
        // 档位同样是请求参数、不是局面：读回来的牌桌不该因为它有任何差别
        assertEquals(richTable(), WerewolfSnapshot.decode(text))
    }

    @Test
    fun `导演钉死的头狼跟着存档走`() {
        val table = richTable().copy(game = richTable().game.copy(alphaWolfSeat = 2))
        val text = WerewolfSnapshot.encode(table)

        val decoded = WerewolfSnapshot.decode(text)
        assertEquals(table, decoded)
        assertEquals(2, decoded?.game?.alphaWolfSeat)
    }

    @Test
    fun `旧档没有发言长度时按标准处理`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"speechLength\":\"STANDARD\",", "")
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":15")

        assertTrue("替换没生效，序列化格式变了：$text", !text.contains("\"speechLength\""))
        assertEquals(WerewolfSpeechLength.STANDARD, WerewolfSnapshot.decodePacing(text)?.speechLength)
        assertNotNull("局面照常能续", WerewolfSnapshot.decode(text))
    }

    @Test
    fun `上一版存档还能读`() {
        // v2/v3 相对前一代都只是新增带默认值的字段，语义没变，所以老档必须能继续续玩——
        // 升级一次就把玩家没打完的局丢掉，比缺一段复盘难受得多
        val legacy = richTable().let { it.copy(game = it.game.copy(nights = emptyList(), voteRounds = emptyList())) }
        val text = WerewolfSnapshot.encode(legacy)
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":1")

        val restored = WerewolfSnapshot.decode(text)
        assertNotNull("老档应该还能续上", restored)
        assertTrue("老档没有夜间记录", restored!!.game.nights.isEmpty())
        assertTrue("老档没有票型历史", restored.game.voteRounds.isEmpty())
        assertEquals("除复盘数据外，局面必须一字不差", legacy, restored)
    }

    @Test
    fun `v2 存档读出来时守卫和白痴取默认值`() {
        // v2 里还没有这两个身份，所以老档里一定没有这几个键。
        // 这里手动把它们从 JSON 里删掉，模拟一个真正的 v2 档，而不是只改版本号
        val legacy = newRolesTable().let {
            it.copy(game = it.game.copy(tonightGuardedSeat = null, lastGuardedSeat = null, idiotRevealed = false))
        }
        var text = WerewolfSnapshot.encode(legacy)
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":2")
        listOf("\"tonightGuardedSeat\":null,", "\"lastGuardedSeat\":null,", "\"idiotRevealed\":false,")
            .forEach { field ->
                assertTrue("构造 v2 档失败：$field 还在", text.contains(field))
                text = text.replace(field, "")
            }

        val restored = WerewolfSnapshot.decode(text)
        assertNotNull("v2 档应该还能续上", restored)
        assertEquals("缺的字段按默认值补齐", legacy, restored)
    }

    @Test
    fun `v24 旧档缺少 beliefNotes 和 wolfStyleSeed 时按默认值兼容读取`() {
        val legacy = richTable().let {
            it.copy(game = it.game.copy(beliefNotes = emptyMap(), wolfStyleSeed = null))
        }
        var text = WerewolfSnapshot.encode(legacy)
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":24")
        listOf("\"beliefNotes\":{},", "\"wolfStyleSeed\":null,")
            .forEach { field ->
                assertTrue("构造 v24 档失败：$field 没找到", text.contains(field))
                text = text.replace(field, "")
            }

        val restored = WerewolfSnapshot.decode(text)
        assertNotNull("v24 档必须能正常续档", restored)
        assertEquals("缺省字段应补为空心证与 null 风向卡", legacy, restored)
    }

    @Test
    fun `版本不匹配当作没有存档`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":999")
        assertNull("不认识的版本不该硬读", WerewolfSnapshot.decode(text))
    }

    @Test
    fun `损坏的存档返回 null 而不是抛异常`() {
        assertNull(WerewolfSnapshot.decode(""))
        assertNull(WerewolfSnapshot.decode("不是 JSON"))
        assertNull(WerewolfSnapshot.decode("{}"))
    }

    @Test
    fun `未知事件类型会被判为坏档`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"HUNTER_SHOT\"", "\"SOMETHING_NEW\"")
        assertNull(WerewolfSnapshot.decode(text))
    }

    @Test
    fun `每个座位用的模型跟着存档走，回放显示的是原局那一份`() {
        // 用 withRoles 不洗牌，保证 players 顺序和名单一致，断言才能对上号
        val game = WerewolfGame.withRoles(
            WerewolfConfig(),
            listOf(
                RosterEntry("p1", "玩家1", "deepseek/deepseek-chat:free"),
                RosterEntry("p2", "玩家2", "qwen3.8-max"),
                RosterEntry("p3", "玩家3", "human"),
                RosterEntry("p4", "玩家4", "tencent/Hunyuan-MT-7B"),
                RosterEntry("p5", "玩家5", "grok-pro"),
                RosterEntry("p6", "玩家6", "doubao-seed-2.1-lite"),
                RosterEntry("p7", "玩家7", ""),
                RosterEntry("p8", "玩家8", "MiniMax-M3"),
            ),
            listOf(
                WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.SEER, WerewolfRole.WITCH,
                WerewolfRole.HUNTER, WerewolfRole.WEREWOLF, WerewolfRole.VILLAGER, WerewolfRole.VILLAGER,
            ),
        )
        val table = WerewolfTable(game)
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(table))!!

        assertEquals("整局仍然完全相等", table, restored)
        assertEquals("聚合平台的完整 id 原样存档", "deepseek/deepseek-chat:free", restored.game.players[0].model)
        assertEquals("自家短名原样存档", "qwen3.8-max", restored.game.players[1].model)
        assertEquals("人座也存着，界面据此不显示", "human", restored.game.players[2].model)
        assertEquals("空模型也原样带回", "", restored.game.players[6].model)
    }

    @Test
    fun `v13 老档没有模型字段也能读，model 回落到空串`() {
        // 把当前档的版本号改成 13，并删掉所有 "model":"..." 字段，模拟升级前的存档
        val legacy = WerewolfSnapshot.encode(richTable())
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":13")
            .replace(Regex(",\"model\":\"[^\"]*\""), "")
        val restored = WerewolfSnapshot.decode(legacy)

        assertNotNull("老档照样能续上", restored)
        assertTrue("老档案位没有模型名", restored!!.game.players.all { it.model.isEmpty() })
    }

    @Test
    fun `阶段内发言进度跟着存档走，续档不会让说过的人重来`() {
        // 讨论/竞选这类阶段中途退出，flush 会把「谁说过」写进 game 一起落档。
        // 续档据此跳过，否则整阶段重跑，用户看到「这一轮重新开始」。
        val table = richTable()
        val withProgress = table.copy(
            game = table.game.copy(
                phase = Phase.DAY_DISCUSSION,
                spokenPhaseKey = table.game.copy(phase = Phase.DAY_DISCUSSION).currentPhaseKey,
                spokenSeats = setOf(1, 4, 6),
            ),
        )
        val restored = WerewolfSnapshot.decode(WerewolfSnapshot.encode(withProgress))

        assertNotNull(restored)
        assertEquals(setOf(1, 4, 6), restored!!.game.spokenSeats)
        assertTrue("阶段 key 也要带回来", restored.game.spokenPhaseKey.isNotBlank())
        assertEquals(table.game.players, restored.game.players)
    }

    @Test
    fun `旧档没有发言进度时按空处理，照常能续`() {
        val text = WerewolfSnapshot.encode(richTable())
            .replace("\"spokenPhaseKey\":\"\",", "")
            .replace("\"spokenSeats\":[],", "")
            .replaceFirst("\"version\":${WerewolfSnapshot.VERSION}", "\"version\":14")

        assertTrue("替换没生效：$text", !text.contains("spokenPhaseKey"))
        val restored = WerewolfSnapshot.decode(text)
        assertNotNull("旧档照样能续", restored)
        assertEquals("没有进度就是谁都没说过", emptySet<Int>(), restored!!.game.spokenSeats)
    }

    @Test
    fun `裁判抽取的台账事实与裁判token用量随消息完整存档还原`() {
        val base = richTable()
        val withGmMsg = base.copy(
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_DISCUSSION,
                    seat = 4,
                    kind = MessageKind.SPEECH,
                    text = "若 1 号爆了就投 2 号，否则主投 1 号。",
                    usage = TokenUsage(input = 1000, output = 120),
                    facts = com.feature.werewolf.engine.SpeechLedgerFacts(
                        claimRole = "女巫",
                        witchReports = listOf(com.feature.werewolf.engine.SpeechWitchFact("救", 6)),
                        voteTarget = 1,
                        conditionalVoteTargets = listOf(2),
                    ),
                    gmUsage = TokenUsage(input = 320, output = 45, estimated = false),
                )
            )
        )
        val encoded = WerewolfSnapshot.encode(withGmMsg)
        val restored = WerewolfSnapshot.decode(encoded)

        assertEquals(withGmMsg, restored)
        assertEquals(320, restored!!.transcript.first().gmUsage.input)
        assertEquals(45, restored.transcript.first().gmUsage.output)
        assertEquals(listOf(2), restored.transcript.first().facts?.conditionalVoteTargets)
    }
}
