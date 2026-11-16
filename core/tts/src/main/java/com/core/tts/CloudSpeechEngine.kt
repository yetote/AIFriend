package com.core.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.core.network.KtorClient
import com.core.repository.settings.TTSProviderCatalog
import com.core.repository.storage.TTSCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * 云端 TTS（MiniMax 同步语音合成）。
 *
 * 为什么放弃系统 TTS：国产 ROM 上 TextToSpeech 的异步绑定和 setLanguage 判定都不稳，
 * 同一个 APK 有时有声有时静音。云端每次一句、结果确定。
 *
 * Key 从设置页独立配置的 TTS 凭证里取（[TTSCredentials]），不和 chat 的 Key 共用——
 * MiniMax 的语音权限要单独开通，共用会让「聊天能用、朗读不行」很难查。
 * [ttsConfigId] 指定用哪套配置（TTSProvider.id），null 回落到默认配置。
 * 每个角色可以按自己的配置朗读，从而用不同的 Key / 网关。
 *
 * 链路：POST {baseUrl}/t2a_v2（非流式）→ base_resp 校验 → data.audio（hex 编码 mp3）
 * → 解码写临时文件 → MediaPlayer 播放 → onDone。
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
            var fired = false
            fun done() {
                if (fired) return
                fired = true
                if (isActive) onDone()
            }

            try {
                // 按角色指定的配置取凭证；id 无效或没配过就回落到默认配置。
                val provider = TTSProviderCatalog.byId(ttsConfigId)
                    ?: TTSProviderCatalog.default()
                val apiKey = TTSCredentials.apiKeyOf(provider.id)
                if (apiKey.isBlank()) {
                    Log.w(TAG, "speak $utteranceId: ${provider.name} 没有配置 Key，跳过")
                    done()
                    return@launch
                }
                val baseUrl = TTSCredentials.baseUrlOf(provider).trimEnd('/')
                val request = TtsRequest(
                    text = text,
                    voiceSetting = VoiceSetting(
                        voiceId = voiceId?.takeIf { it.isNotBlank() } ?: provider.defaultVoice,
                    ),
                )
                Log.d(TAG, "speak $utteranceId: 请求 ${baseUrl}/t2a_v2, voice=${request.voiceSetting.voiceId}")

                val response = KtorClient.instance.apiPost<TtsResponse>(
                    url = "$baseUrl/t2a_v2",
                    body = request,
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                )

                val status = response.baseResp?.statusCode ?: -1
                val audioHex = response.data?.audio
                if (status != 0 || audioHex.isNullOrBlank()) {
                    // 最典型的是 Key 没开语音权限组：status_code=2013 / 401，status_msg 会说清楚。
                    Log.w(TAG, "speak $utteranceId: 合成失败 status=$status, msg=${response.baseResp?.statusMsg}")
                    done()
                    return@launch
                }

                val bytes = audioHex.hexToBytes()
                val file = File(appContext.cacheDir, "tts_$utteranceId.mp3")
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

    /** hex 字符串（MiniMax 的 data.audio）解成字节。非法字符直接抛，交给 speak 的 catch。 */
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex 长度必须为偶数" }
        return ByteArray(length / 2) { i ->
            ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
        }
    }

    @Serializable
    private data class TtsRequest(
        val model: String = MODEL,
        val text: String,
        val stream: Boolean = false,
        @SerialName("voice_setting") val voiceSetting: VoiceSetting,
        @SerialName("audio_setting") val audioSetting: AudioSetting = AudioSetting(),
        @SerialName("output_format") val outputFormat: String = "hex",
    )

    @Serializable
    private data class VoiceSetting(
        @SerialName("voice_id") val voiceId: String,
        val speed: Float = 1.0f,
        val vol: Float = 1.0f,
        val pitch: Int = 0,
    )

    @Serializable
    private data class AudioSetting(
        @SerialName("sample_rate") val sampleRate: Int = 32_000,
        val bitrate: Int = 128_000,
        val format: String = "mp3",
        val channel: Int = 1,
    )

    @Serializable
    private data class TtsResponse(
        val data: AudioData? = null,
        @SerialName("base_resp") val baseResp: BaseResp? = null,
    )

    @Serializable
    private data class AudioData(val audio: String? = null)

    @Serializable
    private data class BaseResp(
        @SerialName("status_code") val statusCode: Int = -1,
        @SerialName("status_msg") val statusMsg: String = "",
    )

    private companion object {
        const val TAG = "CloudSpeechEngine"
        // turbo 比 hd 快、便宜，狼人杀都是短句，够用；用户想要更自然可换 speech-2.8-hd
        const val MODEL = "speech-2.8-turbo"
    }
}
