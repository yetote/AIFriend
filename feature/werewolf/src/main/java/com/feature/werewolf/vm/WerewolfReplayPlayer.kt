package com.feature.werewolf.vm

import com.core.tts.SpeechEngine
import com.core.tts.SpeechTiming
import com.feature.werewolf.replay.ReplayTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 狼人杀复盘演出播放器。
 *
 * 从 `WerewolfViewModel` 抽离，独立管理演出的时间线游标、播放/暂停、倍速、上帝视角切换
 * 以及与共享 [SpeechEngine] 的串行协同。不依赖 Android，可直接做纯 JVM 单测。
 */
internal class WerewolfReplayPlayer(
    private val scope: CoroutineScope,
    private val engine: SpeechEngine,
    private val autoSpeak: StateFlow<Boolean>,
    private val speechReady: StateFlow<Boolean>,
    private val onBeforeStart: () -> Unit = {},
    private val gapMs: Float = DEFAULT_GAP_MS,
    private val lineTimeoutMs: (String) -> Long = SpeechTiming::totalTimeoutMs,
) {
    private val _replay = MutableStateFlow<WerewolfReplay?>(null)
    val replay: StateFlow<WerewolfReplay?> = _replay.asStateFlow()

    private var voiceIds: Map<Int, String?> = emptyMap()
    private var ttsIds: Map<Int, String?> = emptyMap()
    private var mutedSeats: Set<Int> = emptySet()
    private var replayJob: Job? = null

    /**
     * 开始一场复盘演出。
     * @return 帧列表为空时返回 false（无法演出），由调用方决定报错文案。
     */
    fun start(
        record: WerewolfRecordView,
        godView: Boolean,
        voiceIds: Map<Int, String?>,
        ttsIds: Map<Int, String?>,
        mutedSeats: Set<Int>,
    ): Boolean {
        val frames = ReplayTimeline.build(record.table, record.humanSeat, godView)
        if (frames.isEmpty()) return false

        this.voiceIds = voiceIds
        this.ttsIds = ttsIds
        this.mutedSeats = mutedSeats

        // 演出与对局共用同一个喇叭，开演前先把对局朗读停干净
        onBeforeStart()
        _replay.value = WerewolfReplay(
            recordId = record.id,
            table = record.table,
            humanSeat = record.humanSeat,
            godView = godView,
            frames = frames,
            playing = true,
        )
        startLoop()
        return true
    }

    fun close() {
        replayJob?.cancel()
        replayJob = null
        engine.stop()
        _replay.value = null
    }

    /** 播放 / 暂停；演到结尾再点播放会回到开头重演一遍。 */
    fun toggle() {
        val current = _replay.value ?: return
        if (current.playing) {
            pause()
            return
        }
        val atEnd = current.cursor >= current.frames.lastIndex
        _replay.update { state ->
            state?.copy(playing = true, cursor = if (atEnd) -1 else state.cursor)
        }
        startLoop()
    }

    fun pause() {
        replayJob?.cancel()
        replayJob = null
        engine.stop()
        _replay.update { it?.copy(playing = false, speakingSeat = null) }
    }

    fun next() = seek((_replay.value?.cursor ?: -1) + 1)

    fun prev() = seek((_replay.value?.cursor ?: 0) - 1)

    /** 1x → 1.5x → 2x → 1x。 */
    fun cycleSpeed() {
        _replay.update {
            val next = when (it?.speed) {
                1f -> 1.5f
                1.5f -> 2f
                else -> 1f
            }
            it?.copy(speed = next)
        }
    }

    /** 演出页里直接切上帝视角：时间线重摊、从头再放，音色/静音表原样保留。 */
    fun setGodView(godView: Boolean) {
        val current = _replay.value ?: return
        if (current.godView == godView) return
        val frames = ReplayTimeline.build(current.table, current.humanSeat, godView)
        if (frames.isEmpty()) return
        replayJob?.cancel()
        replayJob = null
        engine.stop()
        _replay.value = current.copy(
            godView = godView,
            frames = frames,
            cursor = -1,
            playing = true,
            speakingSeat = null,
        )
        startLoop()
    }

    /** ViewModel 销毁时仅停协程，不释放共享的 engine（由语音播放器统一释放）。 */
    fun stopLoopOnly() {
        replayJob?.cancel()
        replayJob = null
    }

    private fun seek(target: Int) {
        val current = _replay.value ?: return
        val index = target.coerceIn(-1, current.frames.lastIndex)
        replayJob?.cancel()
        replayJob = null
        engine.stop()
        if (current.playing) {
            _replay.update { it?.copy(cursor = (index - 1).coerceAtLeast(-1), speakingSeat = null) }
            startLoop()
        } else {
            _replay.update { it?.copy(cursor = index, speakingSeat = null) }
        }
    }

    private fun startLoop() {
        replayJob?.cancel()
        replayJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                while (isActive) {
                    val current = _replay.value ?: break
                    if (!current.playing) break
                    val next = current.cursor + 1
                    if (next > current.frames.lastIndex) {
                        _replay.update { it?.copy(playing = false) }
                        break
                    }
                    if (current.frames.getOrNull(next) == null) break
                    playFrame(next)
                }
            } finally {
                if (replayJob === self) {
                    replayJob = null
                    _replay.update { it?.copy(speakingSeat = null) }
                }
            }
        }
    }

    private suspend fun playFrame(index: Int) {
        val current = _replay.value ?: return
        val frame = current.frames.getOrNull(index) ?: return
        _replay.update { it?.copy(cursor = index) }

        val gap = (gapMs / current.speed).toLong()
        val seat = frame.message.seat
        val shouldSpeak = frame.speakable && autoSpeak.value && speechReady.value &&
            seat != null && seat !in mutedSeats

        if (!shouldSpeak) {
            val reading = if (gapMs <= 0f) {
                0L
            } else {
                (350 + frame.message.text.length * 55L).coerceAtMost(4_000L)
            }
            if (reading > 0L) delay((reading / current.speed).toLong())
            return
        }

        _replay.update { it?.copy(speakingSeat = seat) }
        try {
            withTimeoutOrNull(lineTimeoutMs(frame.message.text)) {
                suspendCancellableCoroutine<Unit> { cont ->
                    engine.speak(
                        text = frame.message.text,
                        utteranceId = "replay-$index",
                        onDone = { if (cont.isActive) cont.resume(Unit) },
                        voiceId = voiceIds[seat],
                        ttsConfigId = ttsIds[seat],
                    )
                    cont.invokeOnCancellation { engine.stop() }
                }
            }
        } finally {
            if (currentCoroutineContext().isActive) {
                _replay.update { it?.copy(speakingSeat = null) }
            }
        }
        if (gap > 0L) delay(gap / 2)
    }

    companion object {
        const val DEFAULT_GAP_MS = 700f
    }
}
