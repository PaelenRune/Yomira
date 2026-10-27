package com.yomira.core.network

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StealthShieldTest {
    private val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test
    fun `shield is enabled by default and respects opt out`() {
        assertTrue(StealthShield().enabled)
        assertFalse(StealthShield(NetworkConfiguration(stealthShieldEnabled = false)).enabled)
    }

    @Test
    fun `DNS fallback skips failures and empty answers in order`() {
        val calls = mutableListOf<Int>()
        val dns = FallbackDns(listOf(
            dns { calls += 1; throw UnknownHostException("blocked") },
            dns { calls += 2; emptyList() },
            dns { calls += 3; listOf(address) },
            dns { error("must stop after success") },
        ))
        assertEquals(listOf(address), dns.lookup("example.org"))
        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test
    fun `DNS failure retains encrypted resolver causes without system fallback`() {
        val dns = FallbackDns(listOf(
            dns { throw UnknownHostException("first resolver") },
            dns { throw UnknownHostException("second resolver") },
        ))
        val error = assertThrows(UnknownHostException::class.java) { dns.lookup("example.org") }
        assertEquals(listOf("first resolver", "second resolver"), error.suppressed.map { it.message })
    }

    @Test
    fun `disabled DNS uses system resolution and socket selection changes live`() {
        val shield = StealthShield(NetworkConfiguration(stealthShieldEnabled = false))
        val client = shield.configure(OkHttpClient.Builder()).build()
        assertEquals(Dns.SYSTEM.lookup("localhost"), client.dns.lookup("localhost"))
        client.socketFactory.createSocket().use { assertEquals(java.net.Socket::class.java, it.javaClass) }
        shield.enabled = true
        client.socketFactory.createSocket().use { assertTrue(it.javaClass != java.net.Socket::class.java) }
        shield.enabled = false
        client.socketFactory.createSocket().use { assertEquals(java.net.Socket::class.java, it.javaClass) }
    }

    @Test
    fun `fragmented ClientHello completes a real TLS handshake without changing HTTP`() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody("shield works"))
            server.start()
            val client = StealthShield().configure(OkHttpClient.Builder()
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager))
                .dns(dns { listOf(address) })
                .build()
            try {
                val url = server.url("/reading").newBuilder().host("localhost").build()
                client.newCall(Request.Builder().url(url).build()).execute().use {
                    assertEquals("shield works", it.body!!.string())
                    assertTrue(it.handshake != null)
                }
                assertEquals("/reading", server.takeRequest().path)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    private fun dns(lookup: (String) -> List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = lookup.invoke(hostname)
    }
}

class ClientHelloOutputStreamTest {
    @Test
    fun `every write boundary preserves handshake bytes and subsequent records`() {
        val payload = ByteArray(255) { it.toByte() }.apply { this[0] = 1 }
        val hello = record(payload)
        val subsequent = byteArrayOf(20, 3, 3, 0, 1, 1)
        val input = hello + subsequent
        for (boundary in 0..input.size) {
            val target = ByteArrayOutputStream()
            val stream = ClientHelloOutputStream(target)
            stream.write(input, 0, boundary)
            stream.flush()
            stream.write(input, boundary, input.size - boundary)
            val fragmented = target.toByteArray()
            val reconstructed = ByteArrayOutputStream()
            var position = 0
            while (reconstructed.size() < payload.size) {
                assertEquals(22, fragmented[position].toInt())
                assertEquals(3, fragmented[position + 1].toInt())
                assertEquals(3, fragmented[position + 2].toInt())
                val size = ((fragmented[position + 3].toInt() and 255) shl 8) or
                    (fragmented[position + 4].toInt() and 255)
                assertTrue(size in 1..32)
                reconstructed.write(fragmented, position + 5, size)
                position += 5 + size
            }
            assertArrayEquals(payload, reconstructed.toByteArray())
            assertArrayEquals(subsequent, fragmented.copyOfRange(position, fragmented.size))
        }
    }

    @Test
    fun `one byte writes and large record lengths are supported`() {
        val payload = ByteArray(16384) { it.toByte() }.apply { this[0] = 1 }
        val target = ByteArrayOutputStream()
        val stream = ClientHelloOutputStream(target)
        record(payload).forEach { stream.write(it.toInt()) }
        assertEquals(16384 + (16384 / 32) * 5, target.size())
    }

    @Test
    fun `HTTP invalid TLS and non ClientHello records pass through unchanged`() {
        val inputs = listOf(
            "GET / HTTP/1.1\r\n\r\n".toByteArray(),
            "CONNECT example.org:443 HTTP/1.1\r\n\r\n".toByteArray(),
            byteArrayOf(22, 2, 0, 0, 1, 1),
            byteArrayOf(22, 3, 3, 0, 0),
            byteArrayOf(22, 3, 3, 127, 127, 1),
            record(byteArrayOf(2, 0, 0)),
        )
        for (input in inputs) {
            val target = ByteArrayOutputStream()
            ClientHelloOutputStream(target).write(input)
            assertArrayEquals(input, target.toByteArray())
        }
    }

    @Test
    fun `close drains incomplete headers and empty writes are harmless`() {
        val input = byteArrayOf(22, 3, 3)
        val target = ByteArrayOutputStream()
        ClientHelloOutputStream(target).use {
            it.write(byteArrayOf())
            it.write(input)
            it.flush()
            assertEquals(0, target.size())
        }
        assertArrayEquals(input, target.toByteArray())
    }

    private fun record(payload: ByteArray) = byteArrayOf(
        22, 3, 3, (payload.size shr 8).toByte(), payload.size.toByte(),
    ) + payload
}
