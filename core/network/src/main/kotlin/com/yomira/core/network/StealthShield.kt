package com.yomira.core.network

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * In-app encrypted DNS and best-effort ClientHello fragmentation. This is not a
 * VPN: IP addresses and SNI remain observable to an observer that reassembles TLS.
 * Certificate and hostname verification are deliberately unchanged.
 */
class StealthShield(configuration: NetworkConfiguration = NetworkConfiguration()) {
    @Volatile var enabled: Boolean = configuration.stealthShieldEnabled

    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        // A separate, non-recursive transport bootstraps resolvers by literal IP.
        // Fragment resolver handshakes too; never fall back to plaintext DNS.
        val bootstrap = builder.build().newBuilder()
            .socketFactory(ClientHelloSocketFactory { true })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        val encrypted = FallbackDns(listOf(
            resolver(bootstrap, "https://1.1.1.1/dns-query", "1.1.1.1", "1.0.0.1"),
            resolver(bootstrap, "https://dns.quad9.net/dns-query", "9.9.9.9", "149.112.112.112"),
            resolver(bootstrap, "https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        ))
        return builder
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    (if (enabled) encrypted else Dns.SYSTEM).lookup(hostname)
            })
            .socketFactory(ClientHelloSocketFactory { enabled })
    }

    private fun resolver(client: OkHttpClient, url: String, vararg addresses: String): Dns =
        DnsOverHttps.Builder().client(client).url(url.toHttpUrl())
            .bootstrapDnsHosts(addresses.map { InetAddress.getByName(it) })
            .build()
}

/** Only encrypted resolvers participate in fallback, including negative answers. */
internal class FallbackDns(private val resolvers: List<Dns>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val failure = UnknownHostException("Encrypted DNS lookup failed for $hostname")
        for (resolver in resolvers) {
            try {
                val addresses = resolver.lookup(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (error: UnknownHostException) {
                failure.addSuppressed(error)
            }
        }
        throw failure
    }
}
