package com.feature.werewolf.route

// 回放帧 → 牌桌的映射。与 toLive 分开：演出只认已经播到的那一帧，绝不看终局。

import com.feature.werewolf.vm.WerewolfReplay
import com.ui.werewolf.data.UIWerewolfLine
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfReplay
import com.ui.werewolf.data.UIWerewolfSeatLive

/**
 * 狼人杀的入口。
 *
 * 只做三件事：把领域数据翻成展示模型、把界面操作翻回 ViewModel、在「列表 / 配置 / 对局」
 * 之间切换。规则、请求调度、存档都在更下层，这一层不出现任何判定逻辑。
 */

/**
 * 复盘演出的一帧画面。
 *
 * 和 [toLive] 是两套东西：那个是「引擎在跑，界面跟着状态走」，这个是「引擎不存在，
 * 界面跟着播放游标走」。所以这里没有闸门、没有进度、没有轮到我——只有
 * 「已经演到第几条」和「那一刻的牌桌长什么样」。
 *
 * 三件事必须**只从帧里取**，不能偷看终局状态：
 * - 出局：`frame.deadSeats`（终局里所有人都死了，直接拿终局去画，开场就一地尸体）
 * - 警徽：`frame.sheriffSeat`（终局那个是最后接手的人）
 * - 舞台：`frame.day / frame.phase`（终局永远是最后一夜）
 */
internal fun WerewolfReplay.toLive(
    avatars: Map<String, String?>,
    autoSpeak: Boolean,
    speechReady: Boolean,
): UIWerewolfLive {
    val game = table.game
    val shown = frames.take((cursor + 1).coerceAtLeast(0))
    val stage = shown.lastOrNull() ?: frames.firstOrNull()
    val day = stage?.day ?: 1
    val phase = stage?.phase ?: game.phase
    val deadSeats = stage?.deadSeats.orEmpty()
    val sheriffSeat = stage?.sheriffSeat
    val me = game.players.firstOrNull { it.seat == humanSeat }

    val seats = game.players.map { player ->
        val out = player.seat in deadSeats
        UIWerewolfSeatLive(
            seat = player.seat,
            name = player.name,
            avatarPath = avatars[player.id],
            // 回放显示原局真正跑的模型（存档里的值），不是替身/当前好友表的模型
            model = player.model.takeIf { it != "human" }.orEmpty(),
            isSelf = player.seat == humanSeat,
            alive = !out,
            // 死因是终局才知道的事（刀还是毒），和出局这个事实分开看：
            // 出局名单跟着进度走，死因只在身份公开时有意义
            fate = if (out) player.deathCause?.toFate() else null,
            // 身份只在上帝视角下写在脸上。普通视角里那一局当时就是不知道，
            // 演出一遍却提前标出来，等于把「猜身份」这个玩法直接删掉
            roleLabel = if (godView) player.role.displayName else null,
            wolf = player.role.isWerewolf,
            active = speakingSeat != null && speakingSeat == player.seat,
            votes = null,
            sheriff = sheriffSeat == player.seat,
        )
    }

    val lines = shown.mapIndexed { index, frame ->
        val message = frame.message
        val speaker = message.seat?.let { seat -> game.players.firstOrNull { it.seat == seat } }
        UIWerewolfLine(
            index = index,
            kind = lineKindOf(message.kind),
            seat = message.seat,
            speaker = speaker?.name,
            avatarPath = speaker?.id?.let { avatars[it] },
            text = message.text,
            innerLabel = innerLabelOf(message),
            secret = !message.isPublic,
        )
    }

    val total = frames.size
    val position = (cursor + 1).coerceIn(0, total)
    return UIWerewolfLive(
        day = day,
        phaseLabel = phase.displayName,
        night = phase.isNightPhase(),
        // running / finished 一律 false：演出没有引擎，也不该让牌桌切到「看复盘」那一版
        running = false,
        finished = false,
        winnerLabel = null,
        // 演出的进度在底下播放条上（第几条 · 进度），顶栏这一行让给「第几天 · 阶段」
        stageLabel = null,
        seats = seats,
        lines = lines,
        streaming = null,
        selfSeat = humanSeat,
        selfRoleLabel = me?.role?.displayName,
        selfFactionLabel = me?.role?.faction?.displayName,
        // 上帝视角在演出里就是「上帝视角」这个标签本身，和观战是同一件事
        spectating = godView,
        turn = null,
        error = null,
        autoSpeak = autoSpeak,
        speechReady = speechReady,
        speechBusy = speakingSeat != null,
        replay = UIWerewolfReplay(
            playing = playing,
            speedLabel = when (speed) {
                1.5f -> "1.5x"
                2f -> "2x"
                else -> "1x"
            },
            positionLabel = "第 $position / $total 条",
            progress = if (total == 0) 0f else position.toFloat() / total,
            canPrev = cursor > -1,
            canNext = cursor < total - 1,
        ),
    )
}
