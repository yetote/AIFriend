package com.core.tts

/**
 * 把一句话读出来的能力。
 *
 * 只做「读一句 → 读完通知」这一件事，**不排队、不决定读什么**。
 * 排队和朗读范围由 [SpeechQueue] 和 ViewModel 决定，这样换引擎
 * （系统 TTS → 云端语音）时，筛选、去重、跨局重置那些规则一行都不用动。
 *
 * [speak] 的 [onDone] 必须**有且只有一次**被调用——无论正常读完、
 * 引擎出错、还是设备根本没装语音包。否则驱动它的循环会永远挂在那里。
 */
interface SpeechEngine {
    /** 中文语音是否可用。false 时 [speak] 仍会立刻回调 [onDone]，不会卡住队列。 */
    val available: Boolean

    /**
     * 初始化完成时回调，参数是中文语音是否可用。
     *
     * TextToSpeech 是异步初始化的，构造完立刻读 [available] 一定拿到 false。
     * 界面要靠这个回调才能知道「是没装语音包」还是「还在初始化」。
     */
    var onReady: ((available: Boolean) -> Unit)?

    /**
     * 初始化是否已经结束。ViewModel 设置 [onReady] 时若这个值为 true，
     * 要主动读一次 [available]——否则回调可能早于订阅发生，永远收不到通知。
     */
    val initialized: Boolean

    fun speak(text: String, utteranceId: String, onDone: () -> Unit)

    /** 立即停止当前朗读。[onDone] 不会被调用，调用方应自行取消驱动循环。 */
    fun stop()

    fun release()
}

/** 没有语音包时的空实现：让界面能照常显示开关，只是不出声。 */
class NoopSpeechEngine : SpeechEngine {
    override val available: Boolean = false
    override val initialized: Boolean = true
    override var onReady: ((Boolean) -> Unit)? = null
        set(value) { field = value; value?.invoke(false) }
    override fun speak(text: String, utteranceId: String, onDone: () -> Unit) = onDone()
    override fun stop() = Unit
    override fun release() = Unit
}
