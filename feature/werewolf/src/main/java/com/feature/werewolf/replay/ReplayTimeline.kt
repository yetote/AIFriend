package com.feature.werewolf.replay

import com.core.tts.SpeechFilter
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.GameEvent
import com.feature.werewolf.game.Phase
import com.feature.werewolf.prompt.WerewolfPrompt

/**
 * 演出的一帧：一条消息 + 那个时刻的牌桌。
 *
 * 「那个时刻」是关键。存档里只有**终局**状态——所有死者都已经死了、警徽在最终那个人手上。
 * 直接拿终局去画回放，第一句话冒出来的时候牌桌上就已经是尸体和结局，等于开场就把结局
 * 摊在脸上。所以这里按消息顺序把公开事件重新走一遍，算出每一帧当时的出局名单和警徽归属。
 */
data class ReplayFrame(
    val message: WerewolfMessage,
    val day: Int,
    val phase: Phase,
    /** 到这一帧为止已经出局的座位。 */
    val deadSeats: Set<Int>,
    /** 到这一帧为止警徽在谁手上。null = 还没选出来 / 已经作废。 */
    val sheriffSeat: Int?,
    /** 这条消息在这个视角下要不要发声（内心活动永远不读）。 */
    val speakable: Boolean,
)

/**
 * 把一局的存档摊成可以按帧播放的时间线。
 *
 * 三件事必须由它决定，界面不许自己算：
 * 1. **可见性**：参与的局只看得见公开消息；打开上帝视角才把夜聊、查验摊开。
 * 2. **当时的局面**：出局名单、警徽归属都要跟着进度走（见 [ReplayFrame]）。
 * 3. **能不能朗读**：内心活动（投票理由、夜里念头）永远不念，自己在座的话也不念自己。
 */
object ReplayTimeline {

    fun build(table: WerewolfTable, humanSeat: Int?, godView: Boolean): List<ReplayFrame> {
        val visible = table.transcript.filter { godView || it.isPublic }
        if (visible.isEmpty()) return emptyList()

        val events = table.game.history
        var cursor = 0
        var dead = emptySet<Int>()
        var sheriff: Int? = null

        fun apply(event: GameEvent) {
            when (event) {
                is GameEvent.Dawn -> dead = dead + event.deadSeats
                is GameEvent.Exiled -> dead = dead + event.seat
                is GameEvent.WolfExploded -> dead = dead + event.seat
                is GameEvent.HunterShot -> event.targetSeat?.let { dead = dead + it }
                is GameEvent.SheriffElected -> sheriff = event.seat
                is GameEvent.SheriffVacant -> sheriff = null
                // 顺延不改警徽：本来就没选出来，接着挂着
                is GameEvent.SheriffPostponed -> Unit
                is GameEvent.SheriffBadgePassed -> sheriff = event.toSeat
                // 报名、PK、白痴翻牌、熊咆哮、结束都只影响播报文案，不改变座位状态的这两项
                else -> Unit
            }
        }

        val frames = ArrayList<ReplayFrame>(visible.size)
        visible.forEach { message ->
            // 播报逐条对应 history 里的事件（`syncBroadcasts` 就是按这个顺序生成的），
            // 按文案对齐是最准的：一条「3 号被放逐」出现的那一帧，3 号才变灰。
            if (message.kind == MessageKind.DIVINE) {
                val hit = (cursor until events.size)
                    .firstOrNull { WerewolfPrompt.describe(events[it]) == message.text }
                if (hit != null) {
                    for (i in cursor..hit) apply(events[i])
                    cursor = hit + 1
                }
            }
            // 兜底：文案改过、或者这条消息不是播报时，按阶段先后把「已经发生」的事件补上，
            // 免得漏掉的死亡让牌桌上一直挂着一个早该出局的人。
            while (cursor < events.size && rankOf(events[cursor]) < stageRank(message.day, message.phase)) {
                apply(events[cursor])
                cursor++
            }

            frames += ReplayFrame(
                message = message,
                day = message.day,
                phase = message.phase,
                deadSeats = dead,
                sheriffSeat = sheriff,
                speakable = SpeechFilter.speakable(
                    isPublicSpeech = message.kind.isSpokenOutLoud && message.isPublic,
                    speakerSeat = message.seat,
                    selfSeat = humanSeat,
                    text = message.text,
                    // 上帝视角才把夜聊、播报也念出来；参与的普通视角只念听得见的公开发言
                    readEverything = godView,
                    isInnerThought = message.kind.neverSpoken,
                ),
            )
        }
        return frames
    }

    /**
     * 同一天里阶段的先后。夜晚排在白天前面：`day` 在入夜时就 +1 了，
     * 所以「第 2 夜的守卫」和「第 2 天的投票」是同一个 day 值，只能靠这个顺序区分。
     */
    private fun stageRank(day: Int, phase: Phase): Int = day * 100 + phaseOrder(phase)

    private fun phaseOrder(phase: Phase): Int = when (phase) {
        Phase.NIGHT_GUARD -> 0
        Phase.NIGHT_WOLF_CHAT -> 1
        Phase.NIGHT_WOLF -> 2
        Phase.NIGHT_SEER -> 3
        Phase.NIGHT_WITCH -> 4
        Phase.DAY_SHERIFF_SIGNUP -> 10
        Phase.DAY_SHERIFF_SPEECH -> 11
        Phase.DAY_SHERIFF_VOTE -> 12
        Phase.DAY_DISCUSSION -> 13
        Phase.DAY_VOTE -> 14
        Phase.DAY_LAST_WORDS -> 15
        Phase.HUNTER_SHOOT -> 16
        Phase.FINISHED -> 17
    }

    /**
     * 事件落在当天的哪个位置。
     *
     * 天亮结算和熊咆哮标在 9（比任何阶段都早）：那一夜的结果在天亮第一句话之前就成立了。
     * 放逐、自爆标在 14（投票阶段）：它们发生在这个阶段结束时，但下一阶段的发言本来
     * 就属于别人，早一点点变灰不会读错。
     */
    private fun rankOf(event: GameEvent): Int = event.day * 100 + when (event) {
        is GameEvent.Dawn, is GameEvent.BearRoar -> 9
        is GameEvent.SheriffSignup -> 10
        is GameEvent.SheriffPk, is GameEvent.SheriffElected, is GameEvent.SheriffVacant,
        is GameEvent.SheriffPostponed -> 12
        // 警长定方向发生在讨论发言之前，和讨论同段（13），排在当轮发言记录前面
        is GameEvent.SpeechDirectionChosen -> 13
        is GameEvent.WolfExploded, is GameEvent.Exiled,
        is GameEvent.NoExile, is GameEvent.IdiotRevealed -> 14
        is GameEvent.SheriffBadgePassed -> 15
        is GameEvent.HunterShot -> 16
        is GameEvent.GameOver -> 17
    }
}
