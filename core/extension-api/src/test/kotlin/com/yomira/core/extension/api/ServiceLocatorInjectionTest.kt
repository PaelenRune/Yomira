// ServiceLocator injection test (task V811-RT-A): network shims must
// delegate to the host-injected OkHttpClient and fail fast, naming the
// member, when the host has not injected one.
package com.yomira.core.extension.api

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.shim.ShimServiceLocator
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ServiceLocatorInjectionTest {

    @Before
    fun setUp() {
        ShimServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ShimServiceLocator.reset()
    }

    @Test
    fun networkHelperFailsFastNamingTheMemberBeforeHostInjection() {
        try {
            NetworkHelper().client
            throw AssertionError("Expected ShimNotSupportedException")
        } catch (e: eu.kanade.tachiyomi.shim.ShimNotSupportedException) {
            assertEquals(
                "Extension API shim member not implemented: " +
                    "eu.kanade.tachiyomi.network.NetworkHelper.client - host must inject an OkHttpClient " +
                    "via eu.kanade.tachiyomi.shim.ShimServiceLocator.configure(...) before extension code runs",
                e.message,
            )
        }
    }

    @Test
    fun injectedClientIsUsedByNetworkHelperAndSources() {
        val injected = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        ShimServiceLocator.configure(injected)

        assertSame(injected, NetworkHelper().client)
        assertSame(injected, NetworkHelper(context = null).client)
        assertSame(injected, FakeSource().client)
        assertSame(injected, FakeSource().networkClientForTest())
    }

    @Test
    fun userAgentDefaultsAndCanBeOverriddenByTheHost() {
        assertEquals(ShimServiceLocator.DEFAULT_USER_AGENT, NetworkHelper().defaultUserAgent)

        ShimServiceLocator.configure(OkHttpClient(), userAgent = "YomiraTest/1.0")
        assertEquals("YomiraTest/1.0", NetworkHelper().defaultUserAgent)
        assertEquals("YomiraTest/1.0", FakeSource().headers["User-Agent"])
    }

    @Test
    fun resetRestoresTheUninjectedState() {
        ShimServiceLocator.configure(OkHttpClient())
        ShimServiceLocator.reset()
        try {
            NetworkHelper().client
            throw AssertionError("Expected ShimNotFoundException after reset")
        } catch (_: eu.kanade.tachiyomi.shim.ShimNotSupportedException) {
            // expected
        }
    }
}
