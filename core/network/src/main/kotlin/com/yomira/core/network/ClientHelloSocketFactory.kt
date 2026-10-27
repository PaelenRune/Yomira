package com.yomira.core.network

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/** TCP sockets whose first TLS handshake record is split into small TLS records. */
internal class ClientHelloSocketFactory(private val enabled: () -> Boolean) : SocketFactory() {
    override fun createSocket(): Socket = if (enabled()) ClientHelloSocket() else Socket()
    override fun createSocket(host: String, port: Int): Socket = createSocket().apply {
        connect(InetSocketAddress(host, port))
    }
    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket().apply {
        connect(InetSocketAddress(host, port))
    }
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply { bind(InetSocketAddress(localHost, localPort)); connect(InetSocketAddress(host, port)) }
    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply { bind(InetSocketAddress(localHost, localPort)); connect(InetSocketAddress(host, port)) }
}

// Delegation is intentional: TLS engine sockets must use these streams rather
// than a native file descriptor (the Android DI layer selects Conscrypt engines).
private class ClientHelloSocket(private val delegate: Socket = Socket(Proxy.NO_PROXY)) : Socket() {
    private val output by lazy { ClientHelloOutputStream(delegate.getOutputStream()) }
    override fun getOutputStream(): OutputStream = output
    override fun getInputStream() = delegate.getInputStream()
    override fun connect(endpoint: SocketAddress) = delegate.connect(endpoint)
    override fun connect(endpoint: SocketAddress, timeout: Int) = delegate.connect(endpoint, timeout)
    override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
    override fun close() = delegate.close()
    override fun shutdownInput() = delegate.shutdownInput()
    override fun shutdownOutput() = delegate.shutdownOutput()
    override fun getInetAddress() = delegate.inetAddress
    override fun getLocalAddress() = delegate.localAddress
    override fun getPort() = delegate.port
    override fun getLocalPort() = delegate.localPort
    override fun getRemoteSocketAddress() = delegate.remoteSocketAddress
    override fun getLocalSocketAddress() = delegate.localSocketAddress
    override fun isConnected() = delegate.isConnected
    override fun isBound() = delegate.isBound
    override fun isClosed() = delegate.isClosed
    override fun isInputShutdown() = delegate.isInputShutdown
    override fun isOutputShutdown() = delegate.isOutputShutdown
    override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
    override fun getSoTimeout() = delegate.soTimeout
    override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
    override fun getTcpNoDelay() = delegate.tcpNoDelay
    override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    override fun getKeepAlive() = delegate.keepAlive
    override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
    override fun getReuseAddress() = delegate.reuseAddress
    override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
    override fun getSendBufferSize() = delegate.sendBufferSize
    override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
    override fun getReceiveBufferSize() = delegate.receiveBufferSize
    override fun setSoLinger(on: Boolean, linger: Int) = delegate.setSoLinger(on, linger)
    override fun getSoLinger() = delegate.soLinger
    override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
    override fun getTrafficClass() = delegate.trafficClass
    override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
    override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
    override fun getOOBInline() = delegate.oobInline
}

/**
 * Reframes, rather than edits, the first ClientHello. TLS handshake bytes stay
 * identical, so transcript hashes, SNI routing, ALPN and certificate checks work.
 * Handles TLS providers that deliver headers and payload in separate writes.
 */
internal class ClientHelloOutputStream(private val target: OutputStream) : OutputStream() {
    private val pending = ByteArrayOutputStream()
    private var inspecting = true
    private var payloadSize = 0

    override fun write(value: Int) = write(byteArrayOf(value.toByte()))

    @Synchronized
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (!inspecting) { target.write(bytes, offset, length); return }
        for (index in offset until offset + length) {
            pending.write(bytes[index].toInt())
            val size = pending.size()
            // Ordinary HTTP and CONNECT requests must not be held waiting for TLS.
            if (size == 1 && bytes[index].toInt() and 0xff != 22) {
                finish()
            } else if (size == 5) {
                val record = pending.toByteArray()
                payloadSize = ((record[3].toInt() and 0xff) shl 8) or (record[4].toInt() and 0xff)
                if (record[1].toInt() != 3 || payloadSize !in 1..16384) {
                    finish()
                }
            } else if (size >= 6) {
                if (size == 6 && bytes[index].toInt() != 1) {
                    finish()
                } else if (size == payloadSize + 5) {
                    val record = pending.toByteArray()
                    // 32-byte TLS records split DNS names across record boundaries.
                    var position = 5
                    while (position < record.size) {
                        val count = minOf(32, record.size - position)
                        target.write(byteArrayOf(record[0], record[1], record[2], 0, count.toByte()))
                        target.write(record, position, count)
                        position += count
                    }
                    pending.reset()
                    inspecting = false
                }
            }
            if (!inspecting) {
                target.write(bytes, index + 1, offset + length - index - 1)
                return
            }
        }
    }

    private fun finish() {
        pending.writeTo(target)
        pending.reset()
        inspecting = false
    }

    @Synchronized override fun flush() { target.flush() }
    @Synchronized override fun close() { if (pending.size() > 0) finish(); target.close() }
}
