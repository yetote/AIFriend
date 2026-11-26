package com.core.network

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

class NetworkExceptionTest {

    @Test
    fun `stream was reset 被翻译为可理解的连接重置提示且保留原始 cause`() {
        val raw = IOException("stream was reset: PROTOCOL_ERROR")
        val wrapped = DomainException.NetworkException(raw)

        assertSame(raw, wrapped.cause)
        assertTrue(wrapped.message, wrapped.message.contains("流被重置"))
        assertTrue(getReadableMessage(wrapped).contains("流被重置"))
    }

    @Test
    fun `Connection reset 与 EOFException 识别为连接重置`() {
        val reset = DomainException.NetworkException(SocketException("Connection reset"))
        val eof = DomainException.NetworkException(EOFException("unexpected end of stream"))

        assertTrue(reset.message, reset.message.contains("流被重置"))
        assertTrue(eof.message, eof.message.contains("流被重置"))
    }

    @Test
    fun `超时异常仍走超时提示并保留 cause`() {
        val timeout = SocketTimeoutException("Read timed out")
        val wrapped = DomainException.NetworkException(timeout)

        assertSame(timeout, wrapped.cause)
        assertTrue(wrapped.message, wrapped.message.contains("超时"))
    }
}
