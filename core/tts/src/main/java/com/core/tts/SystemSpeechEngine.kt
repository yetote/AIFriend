package com.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
 *
 * 关于可用性判定：只看 init 的 [TextToSpeech.SUCCESS]，不再拿 setLanguage 的
 * 返回值当硬门槛。国产 ROM 上即使引擎和中文包都正常，setLanguage 也可能返回
 * LANG_MISSING_DATA / LANG_NOT_SUPPORTED，把这些当失败会让明明能出声的设备
 * 被判成「无语音包」而彻底静音（用户看到的喇叭变灰、文字全刷就是这么来的）。
 * setLanguage 仍会调用，让引擎按中文发音；它的结果只记日志，不参与可用性判断。
 */
class SystemSpeechEngine(context: Context) : SpeechEngine {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    /** 三态：null = 还在初始化；true/false = 引擎是否绑定成功。 */
    @Volatile
    private var ready: Boolean? = null

    /**
     * 引擎绑定成功就算可用。
     *
     * 真读不出中文时 [speak] 会立刻收到 onError/onDone，队列不会卡住，
     * 只是没声音——这比「误判成不可用、连试都不试」要好：后者连恢复机会都没有。
     */
    override val available: Boolean get() = ready == true

    override val initialized: Boolean get() = ready != null

    override var onReady: ((Boolean) -> Unit)? = null

    /** init 完成前积压的那一句。串行播放下最多只有一句，不需要队列。 */
    private var queuedSpeak: (() -> Unit)? = null

    private var done: (() -> Unit)? = null

    /**
     * 读一句时带上 id 打日志，真机上过滤 "SystemSpeechEngine" 就能看到
     * 引擎到底有没有绑定、有没有开始读、读完了还是报错。
     */
    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "onStart $utteranceId")
        }

        override fun onDone(utteranceId: String?) = finish("onDone $utteranceId")

        override fun onError(utteranceId: String?) = finish("onError $utteranceId")

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            finish("onStop $utteranceId interrupted=$interrupted")
    }

    private val initListener = TextToSpeech.OnInitListener { status ->
        // 可用性只认绑定结果。setLanguage 在国产 ROM 上会误报，不能当门槛。
        val ok = status == TextToSpeech.SUCCESS && tts != null
        ready = ok
        if (ok) {
            // 仍按中文设置，让引擎尽量用中文发音；结果只记日志。
            @Suppress("DEPRECATION")
            val result = tts!!.setLanguage(Locale.CHINA)
            Log.i(
                TAG,
                "init SUCCESS, setLanguage(zh)=$result（只记日志，不影响 available）",
            )
        } else {
            Log.w(TAG, "init FAILED status=$status, tts=$tts")
        }
        // 不管成功失败，积压的那句都要放出去：成功则读，失败则走
        // speak() 里的 !available 分支立刻回调，绝不能让调用方等不到结果。
        queuedSpeak?.invoke()
        queuedSpeak = null
        onReady?.invoke(ok)
    }

    private fun createEngine() {
        tts = TextToSpeech(appContext, initListener).also {
            it.setOnUtteranceProgressListener(progressListener)
        }
    }

    init {
        createEngine()
    }

    override fun speak(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
        voiceId: String?,
        ttsConfigId: String?,
    ) {
        val state = ready
        if (state == null) {
            // 初始化还没回来：存住这一句和它的回调，onInit 里再发。
            // 串行播放保证同时只会有一句在等，不需要 Queue。
            queuedSpeak = { speak(text, utteranceId, onDone) }
            return
        }
        val engine = tts
        if (!state || engine == null || text.isBlank()) {
            // 没绑定 / 空文本：立刻回调，让驱动循环继续下一句，而不是卡死。
            onDone()
            return
        }
        done = onDone
        Log.d(TAG, "speak $utteranceId: ${text.take(30)}")
        @Suppress("DEPRECATION")
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun finish(why: String) {
        Log.d(TAG, "$why, hadCallback=${done != null}")
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

    /**
     * 重建引擎。国产 ROM 上 init 偶发失败，一旦失败不重建就一直静音。
     * 灰色喇叭点一下走这里：先释放旧的，再 new 一个新的重新走异步绑定。
     */
    override fun retry() {
        Log.i(TAG, "retry: 重建 TextToSpeech")
        // shutdown 可能同步触发旧引擎的 onStop → finish() → done 回调，让播放循环
        // resume 并立刻 poll 下一句。此时把 ready 设成 false（不是 null），
        // 让这段时间里的 speak() 直接短路回调，而不是塞进 queuedSpeak 后被下面清掉。
        tts?.shutdown()
        tts = null
        ready = false
        done = null
        queuedSpeak = null
        onReady?.invoke(false)
        createEngine()
    }

    override fun release() {
        queuedSpeak = null
        done = null
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "SystemSpeechEngine"
    }
}
