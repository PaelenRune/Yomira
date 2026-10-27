package com.yomira.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import com.yomira.core.network.NetworkConfiguration
import com.yomira.core.network.StealthShield
import com.yomira.feature.settings.SettingsStore
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(settings: SettingsStore, configuration: NetworkConfiguration): OkHttpClient {
        val shield = StealthShield(configuration)
        // Respect a persisted opt-out before the first request, not just once UI opens.
        shield.enabled = runBlocking(Dispatchers.IO) { settings.read().stealthShieldEnabled }
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }.trustManagers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply {
            init(null, arrayOf(trustManager), null)
        }.socketFactory
        // Android's native FD TLS sockets bypass OutputStream wrappers. Explicitly
        // use engine sockets so record fragmentation actually reaches the wire.
        Conscrypt.setUseEngineSocket(tls, true)
        val client = shield.configure(OkHttpClient.Builder().sslSocketFactory(tls, trustManager))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
        // Application-lifetime observation also covers updates outside Settings UI.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settings.settings.map { it.stealthShieldEnabled }.distinctUntilChanged().collect { enabled ->
                if (shield.enabled != enabled) {
                    shield.enabled = enabled
                    client.connectionPool.evictAll()
                }
            }
        }
        return client
    }
}
