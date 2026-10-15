package de.binarynoise.captiveportalautologin.integration

import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import de.binarynoise.captiveportalautologin.portalproxy.portal.PortalState
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Walks through the portal like `de.binarynoise.liberator.portals.BinarynoisePortalProxy` does, through the
 * proxy the emulator is configured with, against the real `portalProxy` of this repository.
 *
 * This covers the portal protocol without an emulator in the loop.
 */
class PortalProxyLoginTest {
    @Test
    fun `logs in through the portal like the app does`() {
        // free ports, so this test never talks to the portal proxy of a concurrently running integration test
        PortalProxy.start(proxyPort = freePort(), portalPort = freePort(), portalHost = "portalHost")
            .use { portalProxy ->
                HttpCaptureProxy(
                    listenPort = 0,
                    probeTargets = setOf("portalHost:${portalProxy.proxyPort}"),
                    redirectTarget = "http://portalHost:${portalProxy.portalPort}/",
                    aliases = mapOf("portalHost:${portalProxy.portalPort}" to "127.0.0.1:${portalProxy.portalPort}"),
                    isCaptured = { true },
                    onError = { target, failure -> throw AssertionError("forwarding $target failed", failure) },
                ).apply { start() }.use { proxy ->
                    val client = OkHttpClient.Builder()
                        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                        .followRedirects(false) // the app follows redirects on its own, see `Response.followRedirects`
                        .build()
                    val portalUrl = "http://portalHost:${portalProxy.portalPort}/".toHttpUrl()
                    val loginUrl = "http://portalHost:${portalProxy.portalPort}/login".toHttpUrl()
                    
                    assertEquals(PortalState.CAPTURED, client.portalState(portalUrl))
                    
                    client.newCall(Request.Builder().url(loginUrl).post(ByteArray(0).toRequestBody(null)).build())
                        .execute()
                        .use { login ->
                            assertEquals(303, login.code)
                            assertEquals("http://portalHost:${portalProxy.portalPort}/", login.header("Location"))
                        }
                    
                    assertEquals(PortalState.NOT_CAPTURED, client.portalState(portalUrl))
                }
            }
    }
    
    private fun OkHttpClient.portalState(url: HttpUrl): PortalState =
        newCall(Request.Builder().url(url).build()).execute().use { it.body.string().toPortalState() }
}
