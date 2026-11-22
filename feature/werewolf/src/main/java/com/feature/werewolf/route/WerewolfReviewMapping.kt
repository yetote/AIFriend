package com.feature.werewolf.route

// 终局 → 复盘页的映射：底牌、逐日票型、夜间明细、对话归组、结束原因。

import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfUiState
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Faction
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.feature.werewolf.game.WinRule
import com.feature.werewolf.game.VoteOutcome
import com.feature.werewolf.game.formatVotes
import com.ui.werewolf.data.UIWerewolfBallotLine
import com.ui.werewolf.data.UIWerewolfCheckReport
import com.ui.werewolf.data.UIWerewolfDayReport
import com.ui.werewolf.data.UIWerewolfDialogueDay
import com.ui.werewolf.data.UIWerewolfGroupKind
import com.ui.werewolf.data.UIWerewolfNightReport
import com.ui.werewolf.data.UIWerewolfNightDetail
import com.ui.werewolf.data.UIWerewolfReview
import com.ui.werewolf.data.UIWerewolfRevealedSeat
import com.ui.werewolf.data.UIWerewolfRoleGroup
import com.ui.werewolf.data.UIWerewolfSpeechLine

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */

/**
 * 把一局结束的局面翻成复盘页。
 *
 * 这里可以放心地把所有私有信息都摊开：局已经完了，看不懂的部分唯一的意义就是「原来是这样」。
 * 但仍然守住一条：**不去猜**。票型、刀口、查验都只从引擎记下来的记录里读，
 * 没有记录就明说没有（[UIWerewolfReview.incomplete]），不拿播报文案去反推。
 */
internal fun WerewolfUiState.toReview(
    humanSeat: Int?,
    avatars: Map<String, String?>,
): UIWerewolfReview? {
    val table = table ?: return null
    val game = table.game
    val winner = game.winner ?: return null

    // 整局累计得票：票型历史是逐轮的，加投会让同一个人被投两次，这里如实累加
    val votesBySeat = game.voteRounds
        .flatMap { it.ballots }
        .mapNotNull { it.targetSeat }
        .groupingBy { it }
        .eachCount()

    fun fateOf(player: WerewolfPlayer): String? {
        val day = player.deathDay
        if (day == null) {
            // 翻过牌的白痴还活着，但「还活着」不是这一局的全部事实：
            // 不说清楚，复盘里会显示一个没有任何归宿标记的白痴
            return if (player.role == WerewolfRole.IDIOT && game.idiotRevealed) "翻过牌，没被投出" else null
        }
        return when (player.deathCause) {
            DeathCause.WOLF_KILL -> "第 $day 夜被刀"
            DeathCause.WITCH_POISON -> "第 $day 夜被毒"
            DeathCause.EXILE -> "第 $day 天被票出"
            DeathCause.HUNTER_SHOOT -> "第 $day 天被枪带走"
            DeathCause.SELF_EXPLODE -> "第 $day 天自爆"
            null -> null
        }
    }

    // 这一局当过警长的人：选出来的 + 后来接手警徽的。
    // 底牌区不改排版，只在身份后面挂一个「警长」——比新增一行图例省事，
    // 也不会让 12 人局的底牌区再宽一截
    val sheriffSeats = game.history.mapNotNull { event ->
        when (event) {
            is GameEvent.SheriffElected -> event.seat
            is GameEvent.SheriffBadgePassed -> event.toSeat
            else -> null
        }
    }.toSet()

    fun members(predicate: (WerewolfPlayer) -> Boolean) = game.players
        .filter(predicate)
        .map { player ->
            UIWerewolfRevealedSeat(
                seat = player.seat,
                name = player.name,
                avatarPath = avatars[player.id],
                roleLabel = player.role.displayName + if (player.seat in sheriffSeats) "·警长" else "",
                isSelf = player.seat == humanSeat,
                alive = player.alive,
                fate = fateOf(player),
                votes = votesBySeat[player.seat] ?: 0,
            )
        }

    val groups = listOfNotNull(
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.WOLF,
            title = "狼人",
            members = members { it.role.isWerewolf },
        ).takeIf { it.members.isNotEmpty() },
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.GOD,
            title = "神职",
            members = members { it.role.isGod },
        ).takeIf { it.members.isNotEmpty() },
        UIWerewolfRoleGroup(
            kind = UIWerewolfGroupKind.VILLAGER,
            title = "平民",
            members = members { it.role == WerewolfRole.VILLAGER },
        ).takeIf { it.members.isNotEmpty() },
    )

    val days = game.voteRounds
        .groupBy { it.day }
        .toSortedMap()
        .map { (day, rounds) ->
            // 加投时只摆最后一轮：前一轮的票已经被「再投一次」作废了，
            // 两轮的票并排摆出来，会让人以为 XX 号拿了双倍票
            val last = rounds.maxByOrNull { it.round } ?: return@map null
            // 票到了白痴身上只翻牌，没有人出局。这里如果按「放逐」显示，
            // 复盘会写着「放逐 4 号」，而底牌区那个 4 号明明还活着
            val revealed = last.outcome == VoteOutcome.IDIOT_REVEALED
            val exiled = last.exiledSeat.takeIf { !revealed }
            val abstained = last.ballots.isNotEmpty() && last.ballots.all { it.targetSeat == null }
            UIWerewolfDayReport(
                day = day,
                result = when {
                    revealed -> "${last.exiledSeat} 号翻牌（白痴，${formatVotes(last.exileVotes)} 票），不出局"
                    exiled != null -> "放逐 $exiled 号（${formatVotes(last.exileVotes)} 票）"
                    abstained -> "全员弃票"
                    else -> "平票，无人出局"
                },
                exiled = exiled != null,
                extraRounds = (rounds.size - 1).coerceAtLeast(0),
                ballots = last.ballots
                    .sortedBy { it.voterSeat }
                    .map { ballot ->
                        UIWerewolfBallotLine(
                            voter = ballot.voterSeat,
                            target = ballot.targetSeat,
                            key = exiled != null && ballot.targetSeat == exiled,
                        )
                    },
            )
        }
        .filterNotNull()

    val nights = game.nights
        .sortedBy { it.day }
        .map { night ->
            UIWerewolfNightReport(
                day = night.day,
                wolfLine = night.wolfTarget?.let { "狼刀 $it 号" } ?: "狼人空刀",
                guardLine = night.guarded?.let { "守卫守 $it 号" },
                witchLine = when {
                    night.healed -> "女巫用解药救下"
                    night.poisonTarget != null -> "女巫毒 ${night.poisonTarget} 号"
                    else -> null
                },
                deathLine = if (night.deaths.isEmpty()) {
                    "平安夜"
                } else {
                    night.deaths.joinToString("、") + " 号出局"
                },
                mutualProtection = night.mutualProtection,
            )
        }

    // 逐日对话。这里存的是**局内原话**，不是重新生成的摘要——复盘最怕「换个说法」，
    // 一旦改写，玩家就没法拿它跟自己的记忆对账。
    //
    // 可见性按消息产生时的 audience 判定，而不是事后再按身份推一遍：
    // 谁当时能看到这句话，在它被写进 transcript 的那一刻就定死了，复盘只是把它摊开。
    val seatNames = game.players.associate { it.seat to it.name }
    val seatAvatars = game.players.associate { it.seat to avatars[it.id] }
    // 对话里每一行都挂上发言者的终局身份。读复盘时最常问的是「这人当时是什么身份」，
    // 没有它就得往上翻底牌区一个一个座位对，读三句来回滚一次。
    val seatRoles = game.players.associate { it.seat to it.role }

    fun kindOf(role: WerewolfRole): UIWerewolfGroupKind = when {
        role.isWerewolf -> UIWerewolfGroupKind.WOLF
        role.isGod -> UIWerewolfGroupKind.GOD
        else -> UIWerewolfGroupKind.VILLAGER
    }

    fun toSpeechLine(message: WerewolfMessage): UIWerewolfSpeechLine? {
        val text = message.text.trim()
        if (text.isEmpty()) return null
        val seat = message.seat
        val role = seat?.let { seatRoles[it] }
        return UIWerewolfSpeechLine(
            seat = seat,
            speaker = seat?.let { "$it 号 ${seatNames[it].orEmpty()}".trim() } ?: "上帝",
            avatarPath = seat?.let { seatAvatars[it] },
            label = when (message.kind) {
                MessageKind.SPEECH -> "发言"
                MessageKind.VOTE -> "投票"
                MessageKind.WOLF_CHAT -> "狼人夜聊"
                MessageKind.NIGHT_ACT -> "夜间行动"
                MessageKind.DIVINE -> "播报"
                MessageKind.BALLOT -> "唱票"
            },
            text = text,
            secret = !message.isPublic,
            roleLabel = role?.displayName,
            roleKind = role?.let(::kindOf),
            reasoning = message.reasoning,
        )
    }

    // 逐夜明细：夜里每个人做了什么、为什么。
    //
    // 数据来自 transcript 里的**夜间阶段消息**，不是从状态反推：
    // 引擎记下的原话里就有「我守 5 号」「查验 2 号」和它们的理由，
    // 反推只能得到「守了 5 号」，理由那一半就没了。
    val nightPhases = setOf(
        Phase.NIGHT_GUARD,
        Phase.NIGHT_WOLF_CHAT,
        Phase.NIGHT_WOLF,
        Phase.NIGHT_SEER,
        Phase.NIGHT_WITCH,
    )
    val nightDetails = table.transcript
        .filter { it.phase in nightPhases }
        .groupBy { it.day }
        .toSortedMap()
        .map { (day, messages) ->
            fun firstOf(phase: Phase, kind: MessageKind? = null): String? = messages
                .firstOrNull { it.phase == phase && (kind == null || it.kind == kind) }
                ?.text
                ?.let(::headSentence)

            // 狼队定刀可能平票重谈：同一夜里 NIGHT_WOLF 会落两轮记录，
            // 第一轮那句是没定下来的废案（界面上推过一次「谁指了谁」），
            // 摘要必须取**最后一轮**——真正落刀的那次。取第一条就会像这局第 2 夜
            // 那样，摘要写「定刀 12 号」、天亮死的却是 7 号。
            val wolfPlan = messages
                .lastOrNull {
                    it.phase == Phase.NIGHT_WOLF &&
                        (it.text.contains("定刀") || it.text.contains("空刀"))
                }
                ?.text
                ?.let(::headSentence)

            val seerCheck = game.seerChecks.firstOrNull { it.day == day }
            UIWerewolfNightDetail(
                day = day,
                wolfPlan = wolfPlan,
                guardPlan = firstOf(Phase.NIGHT_GUARD),
                seerPlan = firstOf(Phase.NIGHT_SEER)?.let { head ->
                    if (seerCheck == null) {
                        head
                    } else {
                        "$head → " + if (seerCheck.isWerewolf) "狼人" else "好人"
                    }
                },
                witchPlan = firstOf(Phase.NIGHT_WITCH),
                lines = messages.mapNotNull { toSpeechLine(it) },
            )
        }
        .filter { it.lines.isNotEmpty() }

    val dialogues = table.transcript
        .groupBy { it.day }
        .toSortedMap()
        .map { (day, messages) ->
            UIWerewolfDialogueDay(day = day, lines = messages.mapNotNull { toSpeechLine(it) })
        }
        .filter { it.lines.isNotEmpty() }

    val selfLine = humanSeat?.let { seat ->
        val me = game.players.firstOrNull { it.seat == seat } ?: return@let null
        buildString {
            append("你是 $seat 号 · ${me.role.displayName} · ${me.role.faction.displayName}")
            if (me.alive) {
                append("，活到了最后。")
            } else {
                append("，").append(fateOf(me) ?: "已出局").append("。")
            }
            append("下面是当时你看不到的底牌。")
        }
    }

    val deaths = game.players.count { !it.alive }

    return UIWerewolfReview(
        winnerLabel = "${winner.displayName}胜",
        wolfWon = winner == Faction.WEREWOLF,
        reason = finishReason(game, winner),
        facts = "${game.players.size} 人局 · 打到第 ${game.day} 天 · $deaths 人出局",
        selfLine = selfLine,
        // 这一局生效的玩家设定。旧档没记这一段，为 null，复盘页就不显示那一块
        customPrompt = customPrompt?.takeIf { it.isNotBlank() },
        groups = groups,
        days = days,
        nights = nights,
        nightDetails = nightDetails,
        dialogues = dialogues,
        checks = game.seerChecks.map { UIWerewolfCheckReport(it.day, it.targetSeat, it.isWerewolf) },
        // 每局至少有一夜，一条夜间记录都没有只可能是旧档
        incomplete = game.nights.isEmpty(),
    )
}

/** 为什么会结束。胜负本身在横幅上，这里补的是「哪一边先撑不住」。 */
internal fun finishReason(game: WerewolfGame, winner: Faction): String {
    if (winner == Faction.GOOD) return "狼人全部出局，好人守住了。"

    // 绑票要单独说，因为它在屠边局里也成立，而且现场看起来最反直觉：
    // 神和民都还有人，怎么就结束了？答案是他们已经凑不出票把狼投出去
    val aliveWolves = game.players.count { it.alive && it.role.isWerewolf }
    val aliveGood = game.players.count { it.alive && it.faction == Faction.GOOD }
    if (aliveWolves >= aliveGood) {
        // 有没有警长，「为什么投不出去」的说法不一样：警长在也只能多投一轮 PK，
        // 凑不出票这件事不会因为有个警徽就改变。照抄「没有警长就没有 PK」会自相矛盾。
        //
        // 而且要看的是**此刻有没有警长**，不是板子配没配警长：
        // 警长中途被枪带走、警徽作废之后说「警长在场」，那就成了假话。
        val tail = if (game.sheriffSeat != null) {
            "警长在场也最多主持一轮 PK，票数差在这里已经追不回来了。"
        } else {
            "没有警长就没有 PK，平票等于无人出局，夜里再少一个只会更糟。"
        }
        return "场上还剩 $aliveWolves 狼 $aliveGood 好人，狼人已经绑票：白天全员投也凑不出比狼更多的票，$tail"
    }

    return when (game.config.winRule) {
        WinRule.SLAY_ALL -> "存活的狼人已经不少于好人，投票再也翻不过来了。"

        WinRule.SLAY_SIDE -> {
            // 只问「开局有过、现在全没了」的那一类，避免把没有平民的板子判成屠民
            val godsWiped = game.players.any { it.role.isGod } &&
                game.players.none { it.alive && it.role.isGod }
            val villagersWiped = game.players.any { it.role == WerewolfRole.VILLAGER } &&
                game.players.none { it.alive && it.role == WerewolfRole.VILLAGER }
            when {
                godsWiped && villagersWiped -> "神职和平民都已经死光，狼人屠边成功。"
                godsWiped -> "神职全部出局，狼人屠边成功。"
                villagersWiped -> "平民全部出局，狼人屠边成功。"
                else -> "好人一方已经无力回天。"
            }
        }
    }
}

/**
 * 取一句话的头：到第一个句号为止。
 *
 * 夜间行动的原文是「我守 5 号。他昨晚发言少。」这种「结论 + 理由」的结构，
 * 摘要只取结论那一句，理由留给下面的原话——两处都写，摘要就变长了。
 */
internal fun headSentence(text: String, limit: Int = 48): String {
    val trimmed = text.trim()
    val head = trimmed.substringBefore('。').trim().ifEmpty { trimmed }
    return if (head.length > limit) head.take(limit) + "…" else head
}
