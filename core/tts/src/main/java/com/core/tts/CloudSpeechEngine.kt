package com.core.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.storage.TTSCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * 云端 TTS 调度器：按角色指定的配置选服务商，委托 [TTSProtocols] 合成音频，
 * 再用 MediaPlayer 播放，播完才放行朗读队列（不卡显示闸门）。
 *
 * 为什么放弃系统 TTS：国产 ROM 上 TextToSpeech 的异步绑定和 setLanguage 判定都不稳，
 * 同一个 APK 有时有声有时静音。云端每次一句、结果确定。
 *
 * Key 从设置页独立配置的 TTS 凭证里取（[TTSCredentials]），不和 chat 的 Key 共用。
 * [ttsConfigId] 指定用哪套配置（TTSProvider.id），null 回落到默认配置。
 * 每个角色可以按自己的配置朗读，从而用不同的服务商 / Key / 音色。
 */
class CloudSpeechEngine(context: Context) : SpeechEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    /**
     * 有没有任意一套配置好了的 TTS Key。凭证在 ViewModel init 里异步 load，
     * 所以构造瞬间可能是 false；load 完成后 ViewModel 会重读一次刷新界面。
     * 具体某句用哪家、那家有没有 Key，在 [speak] 里按 ttsConfigId 再判。
     */
    override val available: Boolean
        get() = TTSCredentials.hasAny()

    /** 云端不需要异步初始化，凭证是同步可读的内存快照。 */
    override val initialized: Boolean = true

    override var onReady: ((Boolean) -> Unit)? = null

    override fun speak(
        text: String,
        utteranceId: String,
        onDone: () -> Unit,
        voiceId: String?,
        ttsConfigId: String?,
    ) {
        currentJob?.cancel()
        currentJob = scope.launch {
            // [done] 只能被调用一次，且**必须**在每条路径上被调用：
            // 驱动朗读的循环是 suspend 在这个回调上的，漏一次就是整局朗读永久停摆
            // （界面的显示闸门跟着朗读走，于是连文字也不动了）。
            //
            // 这里刻意**不判 isActive**：播放是异步的，`playFile` 返回（prepareAsync
            // 已提交）时这个协程就已经结束，`isActive` 变成 false；等 MediaPlayer
            // 真正播完再回调过来，就会因为这条判断被吞掉。曾经的线上表现正是
            // 「前两个人说完，第三个人既没声音、也没有新消息」。
            var fired = false
            fun done() {
                if (fired) return
                fired = true
                onDone()
            }

            try {
                // 先清洗成「给耳朵听的话」：括号里的舞台说明、markdown、表情都不读。
                // 不清洗的话 TTS 会把「（指尖轻点白角）」逐字念出来，听起来就是念稿。
                val spoken = SpeechText.forSpeech(text)
                if (spoken == null) {
                    Log.d(TAG, "speak $utteranceId: 没有可朗读的内容，跳过")
                    done()
                    return@launch
                }

                // 按角色指定的配置取凭证；id 无效或没配过就回落到默认配置。
                val provider = TTSProviderCatalog.byId(ttsConfigId)
                    ?: TTSProviderCatalog.default()
                val credential = TTSCredentials.get(provider.id)
                // 讯飞新版流式接口三个凭证缺一不可（API Key / API Secret / App ID）。
                if (provider.id == "iflytek") {
                    val missing = buildList {
                        if (credential?.apiKey.isNullOrBlank()) add("API Key")
                        if (credential?.apiSecret.isNullOrBlank()) add("API Secret")
                        if (credential?.appId.isNullOrBlank()) add("App ID")
                    }
                    if (missing.isNotEmpty()) {
                        Log.w(TAG, "speak $utteranceId: 讯飞缺少 ${missing.joinToString("、")}，跳过")
                        done()
                        return@launch
                    }
                } else if (credential?.apiKey.isNullOrBlank()) {
                    Log.w(TAG, "speak $utteranceId: ${provider.name} 没有配置 Key，跳过")
                    done()
                    return@launch
                }

                val effectiveVoice = voiceId?.takeIf { it.isNotBlank() } ?: provider.defaultVoice
                val baseUrl = TTSCredentials.baseUrlOf(provider)
                Log.d(TAG, "speak $utteranceId: ${provider.name}/${provider.protocol}, voice=$effectiveVoice")

                // 单句合成的硬超时。Ktor 的 socket 超时是 120 秒，对一句话来说太久了：
                // 服务端把连接挂着不回（限流、WebSocket 半开），朗读队列就会在那干等两分钟，
                // 期间界面一个字都不放。宁可这一句不出声，也不能让整局停住。
                val bytes = withTimeout(SYNTH_TIMEOUT_MS) {
                    TTSProtocols.synthesize(
                        provider.protocol,
                        TTSProtocols.Request(
                            text = spoken,
                            voiceId = effectiveVoice,
                            apiKey = credential?.apiKey.orEmpty(),
                            apiSecret = credential?.apiSecret.orEmpty(),
                            appId = credential?.appId.orEmpty(),
                            baseUrl = baseUrl,
                        ),
                    )
                }

                // 扩展名按内容定而不是按服务商定：讯飞走了 PCM + 自己套的 WAV 头，
                // 其余几家还是 mp3。MediaPlayer 本来就按文件头识别格式，但缓存里
                // 留一堆「.mp3 其实是 wav」的文件，排查问题时只会浪费一次时间。
                val isWav = bytes.size > 12 &&
                    bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                    bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
                val file = File(appContext.cacheDir, "tts_$utteranceId.${if (isWav) "wav" else "mp3"}")
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                currentFile = file
                Log.d(TAG, "speak $utteranceId: 音频 ${bytes.size} 字节，开始播放")
                playFile(file, ::done)
            } catch (t: Throwable) {
                Log.w(TAG, "speak $utteranceId: 失败 ${t.javaClass.simpleName}: ${t.message}")
                done()
            }
        }
    }

    /** 主线程的 MediaPlayer 回调走 main looper；解码/网络在 IO，只有播放切回 main。 */
    private suspend fun playFile(file: File, onDone: () -> Unit) = withContext(Dispatchers.Main) {
        val player = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
        player.reset()
        player.setDataSource(file.absolutePath)
        player.setOnCompletionListener { onDone() }
        player.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
            onDone()
            true
        }
        player.setOnPreparedListener { it.start() }
        player.prepareAsync()
    }

    private fun cleanup() {
        mediaPlayer?.let { runCatching { it.reset(); it.release() } }
        mediaPlayer = null
        currentFile?.delete()
        currentFile = null
    }

    override fun stop() {
        currentJob?.cancel()
        currentJob = null
        mediaPlayer?.let { runCatching { it.stop() } }
        cleanup()
    }

    /** 重新读一次凭证状态并通知界面。用户点灰色喇叭走这里。 */
    override fun retry() {
        onReady?.invoke(available)
    }

    override fun release() {
        scope.cancel()
        cleanup()
    }

    private companion object {
        const val TAG = "CloudSpeechEngine"
        const val SYNTH_TIMEOUT_MS = SpeechTiming.SYNTH_TIMEOUT_MS
    }
}
