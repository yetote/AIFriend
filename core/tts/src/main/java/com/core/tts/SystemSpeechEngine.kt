package com.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 系统 TTS 实现。
 *
 * 为什么用系统引擎而不是云服务：狼人杀一局几十次朗读，都是同一批短句，
 * 走网络等于给每次翻页都加一次 RTT；而且 APK 里不该再塞一把语音服务的 Key。
 * 中文语音包由系统提供，没有就降级成静音，不影响对局本身。
 *
 * TextToSpeech 的初始化是**异步**的，构造完还不能立刻 speak。这里用一个
 * [queuedSpeak] 存 init 完成前发来的请求，onInit 里再补上——否则开局第一句
 * 发言（往往恰好在 TTS 刚创建后）会被吞掉。
 */
class SystemSpeechEngine(context: Context) : SpeechEngine {

    private var tts: TextToSpeech? = null

    /** 三态：null = 还在初始化；true/false = 中文语音可用与否。 */
    @Volatile
    private var ready: Boolean? = null

    override val available: Boolean get() = ready == true

    override val initialized: Boolean get() = ready != null

    override var onReady: ((Boolean) -> Unit)? = null

    /** init 完成前积压的那一句。串行播放下最多只有一句，不需要队列。 */
    private var queuedSpeak: (() -> Unit)? = null

    private var done: (() -> Unit)? = null

    private val initListener = TextToSpeech.OnInitListener { status ->
        val engine = tts
        ready = if (status == TextToSpeech.SUCCESS && engine != null) {
            val result = engine.setLanguage(Locale.CHINA)
            result == TextToSpeech.LANG_AVAILABLE || result == TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            false
        }
        // 不管成功失败，积压的那句都要放出去：成功则读，失败则走
        // speak() 里的 !available 分支立刻回调，绝不能让调用方等不到结果。
        queuedSpeak?.invoke()
        queuedSpeak = null
        onReady?.invoke(ready == true)
    }

    init {
        tts = TextToSpeech(context.applicationContext, initListener).also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finish()
                override fun onError(utteranceId: String?) = finish()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish()
            })
        }
    }

    override fun speak(text: String, utteranceId: String, onDone: () -> Unit) {
        val state = ready
        if (state == null) {
            // 初始化还没回来：存住这一句和它的回调，onInit 里再发。
            // 串行播放保证同时只会有一句在等，不需要 Queue。
            queuedSpeak = { speak(text, utteranceId, onDone) }
            return
        }
        val engine = tts
        if (!state || engine == null || text.isBlank()) {
            // 没语音包 / 空文本：立刻回调，让驱动循环继续下一句，而不是卡死。
            onDone()
            return
        }
        done = onDone
        @Suppress("DEPRECATION")
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun finish() {
        val callback = done
        done = null
        callback?.invoke()
    }

    override fun stop() {
        queuedSpeak = null
        tts?.stop()
        // done 故意不清：stop 不触发 onDone，但驱动循环是被调用方 cancel 的，
        // 清掉反而会让那个回调永远等不到。交给循环自己的 finally 兜底。
    }

    override fun release() {
        queuedSpeak = null
        done = null
        tts?.shutdown()
        tts = null
        ready = false
    }
}
