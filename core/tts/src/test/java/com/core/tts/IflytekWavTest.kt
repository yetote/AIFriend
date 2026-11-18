package com.core.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 讯飞这条链路的音频封装。
 *
 * 为什么值得单测：这里出的错**听起来**不像程序错误。之前用 aue=lame + sfl=1，
 * 拿回来的是一堆各自独立的 mp3 片段，拼起来有上百个 LAME 头，
 * 播出来的效果是「叽里咕噜听不懂」——第一反应会怀疑音色选了方言，
 * 而不是怀疑封装。WAV 头写错也是同一类问题：能播，但全是噪声。
 */
class IflytekWavTest {

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) =
        String(bytes, offset, length, Charsets.US_ASCII)

    @Test
    fun `WAV 头是标准的 44 字节 RIFF 结构`() {
        val header = TTSProtocols.wavHeader(dataSize = 100)

        assertEquals(44, header.size)
        assertEquals("RIFF", ascii(header, 0, 4))
        assertEquals("WAVE", ascii(header, 8, 4))
        assertEquals("fmt ", ascii(header, 12, 4))
        assertEquals("data", ascii(header, 36, 4))
    }

    @Test
    fun `长度字段按实际 PCM 字节数算`() {
        val header = TTSProtocols.wavHeader(dataSize = 32_000)

        // RIFF 块长度 = 文件总长 - 8 = (44 + dataSize) - 8
        assertEquals(36 + 32_000, le32(header, 4))
        assertEquals(32_000, le32(header, 40))
    }

    @Test
    fun `格式字段与讯飞 raw 输出一致 16kHz 单声道 16bit`() {
        val header = TTSProtocols.wavHeader(dataSize = 2)

        assertEquals("音频格式，1 = PCM", 1, le16(header, 20))
        assertEquals("声道数", 1, le16(header, 22))
        assertEquals("采样率", 16_000, le32(header, 24))
        assertEquals("字节率 = 16000 × 1 × 2", 32_000, le32(header, 28))
        assertEquals("块对齐 = 声道 × 位深 / 8", 2, le16(header, 32))
        assertEquals("位深", 16, le16(header, 34))
    }
}
