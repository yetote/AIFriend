package com.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

class NetFailuresTest {

    @Test
    fun `isConnectionResetLike 识别常见重置与协议异常及 cause 链`() {
        assertTrue(IOException("stream was reset: PROTOCOL_ERROR").isConnectionResetLike())
        assertTrue(SocketException("Connection reset").isConnectionResetLike())
        assertTrue(EOFException("broken").isConnectionResetLike())

        val wrapped = RuntimeException("网络被重置", IOException("stream was reset: CANCEL"))
        assertTrue(wrapped.isConnectionResetLike())
    }

    @Test
    fun `isConnectionResetLike 不误伤超时与普通错误`() {
        assertFalse(SocketTimeoutException("timeout").isConnectionResetLike())
        assertFalse(IllegalStateException("401 unauthorized").isConnectionResetLike())
        assertFalse(IOException("网络开小差了").isConnectionResetLike())
    }
}
