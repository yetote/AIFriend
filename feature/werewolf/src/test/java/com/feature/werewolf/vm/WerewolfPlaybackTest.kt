package com.feature.werewolf.vm

import com.core.tts.SpeechEngine
import com.feature.werewolf.engine.MessageKind
import com.feature.werewolf.engine.WerewolfMessage
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.game.Phase
import com.feature.werewolf.game.RosterEntry
import com.feature.werewolf.game.WerewolfConfig
import com.feature.werewolf.game.WerewolfGame
import com.feature.werewolf.game.WerewolfRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽离后的 [WerewolfSpeechPlayer] 与 [WerewolfReplayPlayer] 纯 JVM 单测。
 *
 * 守住历史上最容易出事故的几条边界：
 * - 参与局绝不朗读狼人夜聊与内心念头；
 * - 关朗读时 awaitCaughtUp 必须立刻放行，不能把引擎卡死；
 * - 演出暂停 / 跳条 / 切上帝视角 / 结尾重播与静音座位行为一致。
 */
class WerewolfPlaybackTest {

    private class FakeSpeechEngine(
        override var available: Boolean = true,
        override var initialized: Boolean = true,
        private val autoComplete: Boolean = true,
    ) : SpeechEngine {
        override var onReady: ((Boolean) -> Unit)? = null
        val spoken = mutableListOf<SpokenRecord>()
        var stopCount = 0
        var releaseCount = 0
        var retryCount = 0
        private val pending = ArrayDeque<() -> Unit>()

        data class SpokenRecord(
            val text: String,
            val utteranceId: String,
            val voiceId: String?,
            val ttsConfigId: String?,
        )

        override fun speak(
            text: String,
            utteranceId: String,
            onDone: () -> Unit,
            voiceId: String?,
            ttsConfigId: String?,
        ) {
            spoken += SpokenRecord(text, utteranceId, voiceId, ttsConfigId)
            if (autoComplete) {
                onDone()
            } else {
                pending.addLast(onDone)
            }
        }

        fun finishNext(): Boolean {
            val cb = pending.removeFirstOrNull() ?: return false
            cb()
            return true
        }

        override fun stop() {
            stopCount++
        }

        override fun release() {
            releaseCount++
        }

        override fun retry() {
            retryCount++
        }
    }

    private fun sampleRecord(): WerewolfRecordView {
        val game = WerewolfGame.withRoles(
            config = WerewolfConfig(
                composition = mapOf(
                    WerewolfRole.WEREWOLF to 2,
                    WerewolfRole.SEER to 1,
                    WerewolfRole.VILLAGER to 2,
                ),
            ),
            roster = (1..5).map { RosterEntry(id = "p$it", name = "玩家$it") },
            roles = listOf(
                WerewolfRole.WEREWOLF,
                WerewolfRole.WEREWOLF,
                WerewolfRole.SEER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
            ),
        )
        val table = WerewolfTable(
            game = game.copy(phase = Phase.DAY_DISCUSSION),
            transcript = listOf(
                WerewolfMessage(
                    day = 1,
                    phase = Phase.NIGHT_WOLF_CHAT,
                    seat = 1,
                    kind = MessageKind.WOLF_CHAT,
                    text = "今晚刀 4 号",
                    audience = setOf(1, 2),
                ),
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_DISCUSSION,
                    seat = 3,
                    kind = MessageKind.SPEECH,
                    text = "我跳预言家，1 号是查杀",
                ),
                WerewolfMessage(
                    day = 1,
                    phase = Phase.DAY_DISCUSSION,
                    seat = 4,
                    kind = MessageKind.SPEECH,
                    text = "我站边 3 号",
                ),
            ),
        )
        return WerewolfRecordView(id = "g1", table = table, humanSeat = 4)
    }

    @Test
    fun `参与局只读他人的公开发言，狼人夜聊与本人发言不出声但推进闸门`() = runBlocking {
        val scope = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
        try {
            val engine = FakeSpeechEngine(autoComplete = true)
            val player = WerewolfSpeechPlayer(scope = scope, engine = engine)
            player.configureVoices(
                voiceIds = mapOf(1 to "v1", 3 to "v3", 4 to "v4"),
                ttsConfigIds = mapOf(3 to "minimax"),
            )
            val transcript = listOf(
                WerewolfMessage(1, Phase.NIGHT_WOLF_CHAT, 1, MessageKind.WOLF_CHAT, "刀4号", audience = setOf(1, 2)),
                WerewolfMessage(1, Phase.DAY_VOTE, 3, MessageKind.VOTE, "投1号", audience = setOf(3)),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 3, MessageKind.SPEECH, "我是预言家"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 4, MessageKind.SPEECH, "我自己的发言"),
            )

            player.offer(transcript, humanSeat = 4)
            player.awaitCaughtUp(transcript.size)
            while (player.speechBusy.value) delay(5)

            assertEquals("闸门应推进到最后一条", 3, player.revealedIndex.value)
            assertEquals("只有 3 号的公开发言出声", listOf("我是预言家"), engine.spoken.map { it.text })
            assertEquals("音色应按座位透传", "v3", engine.spoken.single().voiceId)
            assertEquals("TTS 配置应按座位透传", "minimax", engine.spoken.single().ttsConfigId)
            assertNull("播完后座位高亮清零", player.speakingSeat.value)
            assertFalse("播完后不忙", player.speechBusy.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `等朗读追平时用户关掉朗读开关会立刻放行`() = runBlocking {
        val scope = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
        try {
            val engine = FakeSpeechEngine(autoComplete = false)
            val player = WerewolfSpeechPlayer(
                scope = scope,
                engine = engine,
                lineTimeoutMs = { 30_000L },
                catchUpTimeoutMs = 30_000L,
            )
            val transcript = listOf(
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 1, MessageKind.SPEECH, "第一句"),
                WerewolfMessage(1, Phase.DAY_DISCUSSION, 2, MessageKind.SPEECH, "第二句"),
            )
            player.offer(transcript, humanSeat = null)
            while (engine.spoken.isEmpty()) delay(5)

            val waiting = async { player.awaitCaughtUp(transcript.size) }
            delay(20)
            assertTrue("第一句还没念完，应该还在等", waiting.isActive)

            player.setAutoSpeak(enabled = false, currentTranscriptSize = transcript.size)
            waiting.await()

            assertFalse(player.autoSpeak.value)
            assertFalse(player.speechBusy.value)
            assertTrue(engine.stopCount >= 1)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `复盘演出支持播放暂停跳条倍速切换与静音座位`() = runBlocking {
        val scope = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
        try {
            val engine = FakeSpeechEngine(autoComplete = false)
            val autoSpeak = MutableStateFlow(true)
            val speechReady = MutableStateFlow(true)
            var beforeStartCalls = 0
            val replayPlayer = WerewolfReplayPlayer(
                scope = scope,
                engine = engine,
                autoSpeak = autoSpeak,
                speechReady = speechReady,
                onBeforeStart = { beforeStartCalls++ },
                gapMs = 0f,
            )
            val record = sampleRecord()

            val started = replayPlayer.start(
                record = record,
                godView = true,
                voiceIds = mapOf(1 to "v1", 3 to "v3", 4 to "v4"),
                ttsIds = emptyMap(),
                mutedSeats = setOf(1), // 1 号静音，跳过朗读直接落到 3 号
            )
            assertTrue(started)
            assertEquals(1, beforeStartCalls)

            while (engine.spoken.isEmpty()) delay(5)
            // 1 号被静音不发声，第一句出声的应是 3 号
            assertEquals("我跳预言家，1 号是查杀", engine.spoken.first().text)
            assertEquals(3, replayPlayer.replay.value?.speakingSeat)

            // 暂停：清空 speakingSeat、停止引擎
            replayPlayer.toggle()
            assertFalse(replayPlayer.replay.value!!.playing)
            assertNull(replayPlayer.replay.value!!.speakingSeat)

            // 暂停状态下下一条 / 上一条移动游标
            val pausedAt = replayPlayer.replay.value!!.cursor
            replayPlayer.next()
            assertEquals((pausedAt + 1).coerceAtMost(replayPlayer.replay.value!!.frames.lastIndex), replayPlayer.replay.value!!.cursor)
            replayPlayer.prev()
            assertEquals(pausedAt, replayPlayer.replay.value!!.cursor)

            // 倍速循环 1x -> 1.5x -> 2x -> 1x
            assertEquals(1f, replayPlayer.replay.value!!.speed)
            replayPlayer.cycleSpeed()
            assertEquals(1.5f, replayPlayer.replay.value!!.speed)
            replayPlayer.cycleSpeed()
            assertEquals(2f, replayPlayer.replay.value!!.speed)
            replayPlayer.cycleSpeed()
            assertEquals(1f, replayPlayer.replay.value!!.speed)

            // 切上帝视角：时间线重摊，普通视角看不到 1 号狼人夜聊
            replayPlayer.setGodView(false)
            assertNotNull(replayPlayer.replay.value)
            assertFalse(replayPlayer.replay.value!!.godView)
            assertTrue(replayPlayer.replay.value!!.frames.none { it.message.kind == MessageKind.WOLF_CHAT })

            replayPlayer.close()
            assertNull(replayPlayer.replay.value)
        } finally {
            scope.cancel()
        }
    }
}
