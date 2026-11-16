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

    /**
     * 读一句话。
     *
     * [voiceId] 是可选的云端音色；系统引擎忽略它，云端引擎用它选音色。
     * [ttsConfigId] 指定用哪套 TTS 凭证（TTSProvider.id）；null = 默认配置。
     * 两者都带默认值，让现有调用方（含测试替身）不用改签名。
     */
    fun speak(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
        voiceId: String? = null,
        ttsConfigId: String? = null,
    )

    /** 立即停止当前朗读。[onDone] 不会被调用，调用方应自行取消驱动循环。 */
    fun stop()

    fun release()

    /**
     * 重新初始化引擎。
     *
     * 系统 TTS 的异步绑定在国产 ROM 上偶发失败，失败后不重建就一直静音。
     * 默认空实现——没有这种问题的引擎（含测试替身）不用管。
     */
    fun retry() = Unit
}

/** 没有语音包时的空实现：让界面能照常显示开关，只是不出声。 */
class NoopSpeechEngine : SpeechEngine {
    override val available: Boolean = false
    override val initialized: Boolean = true
    override var onReady: ((Boolean) -> Unit)? = null
        set(value) { field = value; value?.invoke(false) }
    override fun speak(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
        voiceId: String?,
        ttsConfigId: String?,
    ) = onDone()
    override fun stop() = Unit
    override fun release() = Unit
}
