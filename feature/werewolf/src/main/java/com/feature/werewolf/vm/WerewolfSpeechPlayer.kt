package com.feature.werewolf.vm

import com.core.tts.SpeechCandidate
import com.core.tts.SpeechEngine
import com.core.tts.SpeechFilter
import com.core.tts.SpeechQueue
import com.core.tts.SpeechTiming
import com.feature.werewolf.engine.WerewolfMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 狼人杀对局中的自动朗读播放器。
 *
 * 从 `WerewolfViewModel` 抽离成独立类，收口队列消费、防剧透显示闸门（[revealedIndex] /
 * [speechBusy]）、当前发言座位高亮（[speakingSeat]）和阶段边界追平（[awaitCaughtUp]）。
 * 不依赖 Android，可直接做纯 JVM 单测。
 */
internal class WerewolfSpeechPlayer(
    private val scope: CoroutineScope,
    private val engine: SpeechEngine,
    private val queue: SpeechQueue = SpeechQueue(),
    private val lineTimeoutMs: (String) -> Long = SpeechTiming::totalTimeoutMs,
    private val catchUpTimeoutMs: Long = SpeechTiming.CATCH_UP_TIMEOUT_MS,
    private val onWarn: (String) -> Unit = {},
) {
    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _speechReady = MutableStateFlow(engine.available)
    val speechReady: StateFlow<Boolean> = _speechReady.asStateFlow()

    /**
     * 界面可以显示到第几条消息（原始 transcript 下标，-1 表示还没有）。
     * 朗读开着时跟着队列消费走；关着或没语音包时由界面侧解除闸门。
     */
    private val _revealedIndex = MutableStateFlow(-1)
    val revealedIndex: StateFlow<Int> = _revealedIndex.asStateFlow()

    /** 此刻正在被朗读的那条消息属于哪个座位；null 表示没在念或系统播报。 */
    private val _speakingSeat = MutableStateFlow<Int?>(null)
    val speakingSeat: StateFlow<Int?> = _speakingSeat.asStateFlow()

    /** 喇叭手里还有没有活：队列里积压着没念的行，或者某一句正在念。 */
    private val _speechBusy = MutableStateFlow(false)
    val speechBusy: StateFlow<Boolean> = _speechBusy.asStateFlow()

    private var speakJob: Job? = null
    private var seatVoiceIds: Map<Int, String?> = emptyMap()
    private var seatTtsConfigIds: Map<Int, String?> = emptyMap()

    init {
        engine.onReady = { ready -> _speechReady.value = ready }
        if (engine.initialized) _speechReady.value = engine.available
    }

    /** 凭证异步加载完成后重绑一次云端语音引擎并同步可用状态。 */
    fun refreshAfterCredentialsLoaded() {
        engine.retry()
        _speechReady.value = engine.available
    }

    fun configureVoices(voiceIds: Map<Int, String?>, ttsConfigIds: Map<Int, String?>) {
        seatVoiceIds = voiceIds
        seatTtsConfigIds = ttsConfigIds
    }

    fun clearVoices() {
        seatVoiceIds = emptyMap()
        seatTtsConfigIds = emptyMap()
    }

    /** 同步本地偏好（DataStore 读回来的值）。 */
    fun applyPreference(enabled: Boolean) {
        _autoSpeak.value = enabled
    }

    /** 用户手动切换开关：关掉立刻闭嘴，打开时跳过关闭期间积下的消息。 */
    fun setAutoSpeak(enabled: Boolean, currentTranscriptSize: Int) {
        _autoSpeak.value = enabled
        if (enabled) {
            skipTo(currentTranscriptSize)
            if (!engine.initialized || !engine.available) engine.retry()
        } else {
            stop(clearQueue = true)
        }
    }

    /** 点灰色喇叭时单独重试绑定引擎，不切换朗读开关。 */
    fun retry() {
        engine.retry()
    }

    /**
     * 把新增的消息放进朗读队列。
     *
     * 参与局只读公开发言且不读本人发言；观战（[humanSeat] == null）读所有非内心活动的消息。
     */
    fun offer(transcript: List<WerewolfMessage>, humanSeat: Int?) {
        if (!_autoSpeak.value) return
        val readEverything = humanSeat == null
        val candidates = transcript.map { message ->
            val speakable = SpeechFilter.speakable(
                isPublicSpeech = message.kind.isSpokenOutLoud && message.isPublic,
                speakerSeat = message.seat,
                selfSeat = humanSeat,
                text = message.text,
                readEverything = readEverything,
                isInnerThought = message.kind.neverSpoken,
            )
            SpeechCandidate(
                text = message.text,
                speakable = speakable,
                voiceId = message.seat?.let { seatVoiceIds[it] },
                ttsConfigId = message.seat?.let { seatTtsConfigIds[it] },
                seat = message.seat,
            )
        }
        val added = queue.offer(candidates)
        if (added.isNotEmpty()) {
            _speechBusy.value = true
            startSpeakLoop()
        }
    }

    /**
     * 在阶段边界等朗读追平当前 transcript 末尾。
     * 关朗读、没语音包或等待超时都直接放行：声音可以缺，局面不能挂。
     */
    suspend fun awaitCaughtUp(transcriptSize: Int) {
        if (!_autoSpeak.value || !_speechReady.value) return
        val target = transcriptSize - 1
        val start = _revealedIndex.value
        if (target <= start) return
        val done = withTimeoutOrNull(catchUpTimeoutMs) {
            combine(_revealedIndex, _autoSpeak) { revealed, speak -> revealed >= target || !speak }
                .first { it }
            true
        }
        if (done != true) {
            onWarn("朗读 ${catchUpTimeoutMs / 1000}s 内没追平（$start/$target），引擎继续")
        }
    }

    /** 换局/续档时把队列和显示进度一起重置：历史全显示，新消息才跟朗读走。 */
    fun reset(fromIndex: Int) {
        queue.reset(fromIndex)
        _revealedIndex.value = fromIndex - 1
        _speakingSeat.value = null
        _speechBusy.value = false
    }

    /** 重开朗读时跳过关闭期间积下的消息，显示进度也一起跳到当前末尾。 */
    fun skipTo(index: Int) {
        queue.skipTo(index)
        _revealedIndex.value = queue.revealedIndex
    }

    /** 停掉朗读并可选清空队列。开局、续档、暂停、放弃、切入复盘演出都走这里。 */
    fun stop(clearQueue: Boolean) {
        speakJob?.cancel()
        speakJob = null
        if (clearQueue) queue.clear()
        _speakingSeat.value = null
        _speechBusy.value = false
        engine.stop()
    }

    fun release() {
        stop(clearQueue = true)
        engine.onReady = null
        engine.release()
    }

    private fun startSpeakLoop() {
        if (speakJob?.isActive == true) return
        speakJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                while (isActive && _autoSpeak.value) {
                    val line = queue.poll() ?: break
                    _revealedIndex.value = queue.revealedIndex
                    if (!line.speakable) continue
                    _speakingSeat.value = line.seat
                    val finished = withTimeoutOrNull(lineTimeoutMs(line.text)) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            engine.speak(
                                text = line.text,
                                utteranceId = line.index.toString(),
                                onDone = { if (cont.isActive) cont.resume(Unit) },
                                voiceId = line.voiceId,
                                ttsConfigId = line.ttsConfigId,
                            )
                            cont.invokeOnCancellation { engine.stop() }
                        }
                        true
                    }
                    if (finished != true) {
                        onWarn("朗读超时，跳过第 ${line.index} 条（${line.text.length} 字）")
                        engine.stop()
                    }
                    if (speakJob === self) {
                        _speakingSeat.value = null
                    }
                }
            } finally {
                // 只认自己还是当前循环：防止旧循环延迟收尾把新循环的状态抹掉
                if (speakJob === self) {
                    speakJob = null
                    _speakingSeat.value = null
                    _speechBusy.value = false
                }
            }
        }
    }
}
