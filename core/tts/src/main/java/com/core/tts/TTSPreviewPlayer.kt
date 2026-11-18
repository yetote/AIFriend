package com.core.tts

import android.content.Context
import android.media.MediaPlayer
import com.core.repository.settings.TTSProvider
import com.core.repository.storage.TTSCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.util.UUID

/**
 * 设置页的「测试并保存」用：真的合成一句固定文本并播放，失败时把原因交回界面。
 *
 * 和 [CloudSpeechEngine] 的区别：引擎侧朗读失败只能放行队列、不能让用户知道 Key 错了；
 * 这里要把网络/鉴权/解码错误完整带回来，所以单独走一条同步返回 Result 的路径。
 * 凭证由调用方显式传入——测试时输入框里可能还是未落盘的新 Key。
 */
class TTSPreviewPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    suspend fun preview(
        provider: TTSProvider,
        credential: TTSCredential,
        voiceId: String?,
        text: String = PREVIEW_TEXT,
    ): Result<Unit> = runCatching {
        val effectiveVoice = voiceId?.takeIf { it.isNotBlank() } ?: provider.defaultVoice
        val bytes = TTSProtocols.synthesize(
            provider.protocol,
            TTSProtocols.Request(
                text = text,
                voiceId = effectiveVoice,
                apiKey = credential.apiKey,
                apiSecret = credential.apiSecret,
                appId = credential.appId,
                baseUrl = credential.resolvedBaseUrl(provider),
            ),
        )
        if (bytes.isEmpty()) throw TTSException("服务返回了空音频，请检查音色 ID 是否正确")
        play(bytes).getOrThrow()
    }

    /** 写文件和 MediaPlayer 都放这里：网络阶段已经结束，结果只等播放完成。 */
    private suspend fun play(bytes: ByteArray): Result<Unit> = runCatching {
        withContext(Dispatchers.Main) {
            val file = withContext(Dispatchers.IO) {
                File(appContext.cacheDir, "tts_preview_${UUID.randomUUID()}.mp3").also {
                    it.writeBytes(bytes)
                }
            }
            suspendCancellableCoroutine<Unit> { cont ->
                var settled = false
                fun finish(result: Result<Unit>) {
                    if (settled) return
                    settled = true
                    cleanup()
                    result.fold(
                        onSuccess = { cont.resume(Unit) },
                        onFailure = { cont.resumeWithException(it) },
                    )
                }

                val mp = runCatching { MediaPlayer() }
                    .onFailure { finish(Result.failure(it)) }
                    .getOrNull() ?: return@suspendCancellableCoroutine
                player = mp
                currentFile = file

                mp.setOnPreparedListener { runCatching { it.start() } }
                mp.setOnCompletionListener { finish(Result.success(Unit)) }
                mp.setOnErrorListener { _, what, extra ->
                    finish(Result.failure(TTSException("音频播放失败（$what / $extra）")))
                    true
                }
                cont.invokeOnCancellation {
                    runCatching { mp.stop() }
                    finish(Result.failure(CancellationException("试听已取消")))
                }

                runCatching {
                    mp.setDataSource(file.absolutePath)
                    mp.prepareAsync()
                }.onFailure { finish(Result.failure(it)) }
            }
        }
    }

    /** ViewModel 销毁时兜底释放，避免设置页退出了还在念。 */
    fun stop() {
        runCatching { player?.stop() }
        cleanup()
    }

    private fun cleanup() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
        currentFile?.delete()
        currentFile = null
    }

    companion object {
        const val PREVIEW_TEXT = "你好，这是一段语音试听。"
    }
}
