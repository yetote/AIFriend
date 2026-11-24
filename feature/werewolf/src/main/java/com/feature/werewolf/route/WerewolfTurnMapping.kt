package com.feature.werewolf.route

// 实时局面 → 「现在轮到我做什么」面板：发言 / 投票 / 选人 / 女巫。

import com.feature.werewolf.engine.HumanTurn
import com.feature.werewolf.game.DeathCause
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfPlayer
import com.feature.werewolf.game.WerewolfRole
import com.ui.werewolf.data.UIWerewolfTarget
import com.ui.werewolf.data.UIWerewolfTurn
import com.ui.werewolf.data.UIWerewolfTurnKind

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */

/**
 * 现在轮到我做什么。
 *
 * 只认 `phase` + 本人身份。人类座位和 AI 座位共用同一条请求路径，
 * 所以「该不该弹面板」不能靠 `turn != null` 判断——`turn` 只说明导演正在等这个座位。
 */
internal fun buildTurn(
    game: WerewolfGame,
    humanSeat: Int?,
    me: WerewolfPlayer?,
    waiting: HumanTurn?,
): UIWerewolfTurn? {
    if (humanSeat == null || me == null || waiting == null) return null

    val others = game.alivePlayers
        .filter { it.seat != humanSeat }
        .map { UIWerewolfTarget(seat = it.seat, name = it.name) }

    return when (game.phase) {
        // 上警报名：点自己 = 上警，点「不上警」= 弃权。
        // 复用 VOTE 面板而不是新做一个开关：这一步的答案就是「一个座位或 null」，
        // 和投票的形状完全一样，多一种面板只会多一处要维护的按钮文案
        Phase.DAY_SHERIFF_SIGNUP -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = "要不要竞选警长",
            hint = "只是举手报名，理由留到竞选发言；上过警的人首轮不能投警长票",
            targets = listOf(UIWerewolfTarget(seat = humanSeat, name = "上警")),
            canAbstain = true,
            abstainLabel = "不上警",
        )

        Phase.DAY_SHERIFF_SPEECH -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.SPEECH,
            seat = humanSeat,
            title = if (game.sheriffRound > 1) "PK 发言" else "竞选发言",
            hint = "警下的人会按这几句投票",
        )

        Phase.DAY_SHERIFF_WITHDRAW -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.WITHDRAW,
            seat = humanSeat,
            title = "要不要退水",
            // 退水的两种代价必须写清楚：不再被投票，也没有票。
            // 只写「放弃竞选」的话，玩家会以为只是不争了、票还在
            hint = "退了这局就拿不到警徽，也不会有警长票（上过警本来就不投）",
        )

        Phase.DAY_SHERIFF_VOTE -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = "投票选警长",
            hint = if (game.sheriffRound > 1) "PK 后重投" else "只能投还在竞选的人（退水的不算）",
            targets = others.filter { it.seat in game.sheriffCandidates },
            canAbstain = true,
        )

        Phase.DAY_LAST_WORDS -> if (game.sheriffSeat == humanSeat) {
            // 警长的出局处理要同时交代警徽。用 SEAT 面板：选一个人就是交给他，
            // 「撕掉警徽」走弃权那条路。
            // 被枪带走时没有遗言，这一步只剩交徽，标题不能再写「遗言」。
            val shot = game.playerAt(humanSeat).deathCause == DeathCause.HUNTER_SHOOT
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = if (shot) "警徽交给谁" else "遗言：警徽交给谁",
                hint = if (shot) "被枪带走，没有遗言——只能处理警徽" else "交错了人，比撕掉更糟",
                targets = others,
                canAbstain = true,
                abstainLabel = "撕掉警徽",
            )
        } else {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SPEECH,
                seat = humanSeat,
                title = "说遗言",
                hint = "说给还活着的人听",
            )
        }

        Phase.DAY_DISCUSSION -> if (waiting.jsonMode) {
            // 警长定方向：这一步是 jsonMode 的决策请求（DirectionChoice），不是发言。
            // 同一个阶段里先问方向、再依次发言，靠 waiting 的 jsonMode 区分两件事。
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.DIRECTION,
                seat = humanSeat,
                title = "选今天的发言顺序",
                // 「左 / 右」在线上没有真正的左右手，只能钉死成座位号方向，否则每个人理解不同
                hint = "左 = 座位号增大的方向。警左 / 警右从你两边起，你最后说；死左 / 死右从昨夜死者起",
                hasDawnDeath = game.dawnDeadSeats.isNotEmpty(),
            )
        } else {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SPEECH,
                seat = humanSeat,
                title = "轮到你发言",
                hint = "第 ${game.day} 天",
            )
        }

        Phase.DAY_VOTE -> UIWerewolfTurn(
            kind = UIWerewolfTurnKind.VOTE,
            seat = humanSeat,
            title = if (game.voteRound > 1) "平票，再投一次" else "投票放逐",
            hint = if (game.voteCandidates != null) "只能投平票的人" else null,
            targets = if (game.voteCandidates != null) {
                others.filter { it.seat in game.voteCandidates }
            } else {
                // 翻牌的白痴不能再被投：投他只是浪费一次放逐，还会把白天拖成平票
                others.filter { it.seat in game.votingSeats }
            },
            canAbstain = true,
        )

        Phase.NIGHT_GUARD -> if (me.role == WerewolfRole.GUARD) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "今晚守谁",
                hint = game.lastGuardedSeat?.let { "昨夜守了 $it 号，不能连守" },
                // 守卫可以守自己，所以候选里包含本人座位
                targets = game.alivePlayers
                    .filter { it.seat in game.guardTargets }
                    .map { UIWerewolfTarget(seat = it.seat, name = it.name) },
                canAbstain = true,
                abstainLabel = "这晚不守",
            )
        } else {
            null
        }

        // 狼队内部商量。人也要能开口：他这一句队友看得见，下一步定刀才谈得拢。
        // 没人说话也能过——真正的决定在下一个阶段
        Phase.NIGHT_WOLF_CHAT -> if (me.role.isWerewolf) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SPEECH,
                seat = humanSeat,
                title = "和队友商量今晚刀谁",
                hint = "只有狼看得到",
            )
        } else {
            null
        }

        // 定刀。用 VOTE 面板而不是 SEAT：「不做」在这里是**空刀**，走弃权那条路正好
        // 就是 VoteChoice(null)，和 AI 座位用的是同一套决策。
        // 候选是**全部存活座位**（含自己与队友）——自刀骗解药、刀队友做身份都是正经打法，
        // 候选里不给，人类玩家就只能照着 AI 的旧打法走。
        Phase.NIGHT_WOLF -> if (me.role.isWerewolf) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.VOTE,
                seat = humanSeat,
                title = if (game.wolfVoteRound > 1) "平票，重新定刀" else "今晚刀谁",
                hint = "自己人和你自己也能刀；空刀是战术，不是失误",
                targets = game.alivePlayers.map { UIWerewolfTarget(seat = it.seat, name = it.name) },
                canAbstain = true,
                abstainLabel = "空刀",
            )
        } else {
            null
        }

        Phase.NIGHT_SEER -> if (me.role == WerewolfRole.SEER) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "查验谁",
                hint = "只知道是好人还是狼",
                targets = others,
            )
        } else {
            null
        }

        Phase.NIGHT_WITCH -> if (me.role == WerewolfRole.WITCH) {
            // 刀口信息只在解药还可用时披露。解药一旦用完，女巫夜里就是零信息——
            // 不知道今晚谁被刀、不知道是不是平安夜、甚至不知道自己被刀。
            // 白天的死讯播报是全场公开的渠道，那才是她夜里之后唯一的信息来源。
            val antidoteGone = !game.witch.antidoteAvailable
            val victim = game.tonightVictim
            // 「今晚被刀的是你自己」只在解药还在时才允许出现；
            // 解药空了连这一点都不说——她天亮前不知道自己死了。
            val selfDoomed = !antidoteGone && victim == humanSeat && !game.witchCanSelfHeal
            val hint = when {
                selfDoomed ->
                    "今晚被刀的是你自己，你救不了自己。" +
                        (if (game.witch.poisonAvailable) "毒药还在的话，今晚是它最后一次机会。" else "")
                antidoteGone && game.witch.poisonAvailable ->
                    "解药已经用完，今晚的事不会再被告知，你只剩毒药的抉择。"
                antidoteGone ->
                    "解药和毒药都已用完，今晚没有你可以做的事。"
                victim == null ->
                    "今晚没有人被狼人袭击。"
                else -> null // 「用解药救 X 号」按钮上写着，不重复
            }
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.WITCH,
                seat = humanSeat,
                title = "女巫用药",
                hint = hint,
                targets = others,
                canHeal = game.witchCanHeal && !selfDoomed,
                canPoison = game.witchCanPoison,
                // healTarget 只在 canHeal 时被界面渲染，解药空了不会泄露
                healTarget = victim?.let { "$it 号" },
            )
        } else {
            null
        }

        Phase.HUNTER_SHOOT -> if (
            me.role.canShootOnDeath && game.pendingHunterSeat == humanSeat
        ) {
            UIWerewolfTurn(
                kind = UIWerewolfTurnKind.SEAT,
                seat = humanSeat,
                title = "开枪带走一个人",
                hint = "也可以不开",
                targets = others,
                canAbstain = true,
                abstainLabel = "不开枪",
            )
        } else {
            null
        }

        Phase.FINISHED -> null
        // 汇聚节点不问人类；人类守卫/狼人/预言家在各自虚拟化阶段下走老路径
        Phase.NIGHT_PENDING -> null
    }
}
