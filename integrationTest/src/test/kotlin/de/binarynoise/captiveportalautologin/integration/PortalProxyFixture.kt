package de.binarynoise.captiveportalautologin.integration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import de.binarynoise.captiveportalautologin.portalproxy.PortalProxyConfig
import de.binarynoise.captiveportalautologin.portalproxy.PortalProxyServer
import de.binarynoise.logger.Logger.log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * States the portal served by `portalProxy` can be in, see `portalProxy/src/main/kotlin/Portal.kt`.
 *
 * The proxy keys them by the ip of the client. Requests coming from an emulator reach the host machine
 * through `10.0.2.2`, which makes the emulator show up as `127.0.0.1` - the same ip this test uses.
 */
enum class PortalState {
    CAPTURED, ERROR_STILL_CAPTURED, ERROR_403, NOT_CAPTURED;
}

internal fun String.toPortalState(): PortalState =
    PortalState.entries.firstOrNull { state -> contains("You are currently in state ${state.name}") }
        ?: error("could not read the portal state from:\n$this")

/**
 * Runs the `portalProxy` of this repository in this process, like `deployment/portal-proxy.service` does.
 */
class PortalProxyProcess(
    val proxyPort: Int,
    val portalPort: Int,
    private val portalHost: String,
) : AutoCloseable {
    
    private val server = PortalProxyServer(
        PortalProxyConfig(proxyPort = proxyPort, portalPort = portalPort, portalHost = portalHost)
    )
    
    val isAlive: Boolean get() = server.isRunning
    
    fun start() {
        server.start()
    }
    
    override fun close() {
        server.close()
    }
    
    companion object {
        private val startTimeout = 60.seconds
        private val startPollInterval = 500.milliseconds
        
        /**
         * One client for all readiness polls, instead of a new pool and threads per poll.
         */
        private val probeClient = OkHttpClient()
        
        fun start(
            proxyPort: Int,
            portalPort: Int,
            portalHost: String,
        ): PortalProxyProcess {
            val portalProxy = PortalProxyProcess(proxyPort, portalPort, portalHost)
            try {
                portalProxy.start()
                val start = TimeSource.Monotonic.markNow()
                while (true) {
                    // a dead server never revives, so it fails fast instead of polling until the timeout
                    check(portalProxy.isAlive) { "the portal proxy died while starting" }
                    if (portalPage(portalPort)?.contains("Captive Portal") == true) return portalProxy
                    check(start.elapsedNow() < startTimeout) {
                        "timed out after $startTimeout waiting for the portal proxy to serve its portal"
                    }
                    Thread.sleep(startPollInterval.inWholeMilliseconds)
                }
            } catch (failure: Exception) {
                portalProxy.close()
                throw failure
            }
        }
        
        /**
         * The page the portal serves, or null while it is not reachable yet.
         */
        fun portalPage(portalPort: Int): String? = runCatching {
            probeClient.newCall(Request.Builder().url("http://127.0.0.1:$portalPort/".toHttpUrl()).build())
                .execute()
                .use { it.body.string() }
        }.getOrNull()
    }
}

/**
 * The portal of `portalProxy` behind an [HttpCaptureProxy], which is what the emulator is pointed at.
 */
class PortalProxyFixture(
    private val environment: TestEnvironment,
) : AutoCloseable {
    
    private val client = OkHttpClient()
    private var portalProxy: PortalProxyProcess? = null
    private val captureProxy = HttpCaptureProxy(
        listenPort = environment.proxyPort,
        probeTargets = setOf("${environment.hostAddress}:${environment.proxyPort}"),
        redirectTarget = "http://${environment.portalHost}:${environment.portalPort}/",
        aliases = mapOf(
            "${environment.portalHost}:${environment.portalPort}" to "127.0.0.1:${environment.portalPort}",
            "127.0.0.1:${environment.deviceApiServerPort}" to "127.0.0.1:${environment.apiServerPort}",
        ),
        isCaptured = { portalState() != PortalState.NOT_CAPTURED },
        onRequest = { target -> log("the emulator requested $target") },
        onResponse = { target, status -> log("$target was answered with $status") },
        onError = { target, failure -> log("forwarding $target failed", failure) },
    )
    
    fun start() {
        // a leftover proxy from an earlier run would answer the probes of this run and make every check pass
        // against a foreign server, so the ports have to be free before the proxy is started
        requirePortsAreFree(
            environment.proxyPort,
            environment.portalPort,
            environment.portalProxyPort,
            owner = "another portal proxy",
        )
        portalProxy = PortalProxyProcess.start(
            proxyPort = environment.portalProxyPort,
            portalPort = environment.portalPort,
            portalHost = environment.portalHost,
        )
        captureProxy.start()
    }
    
    fun updateState(state: PortalState) {
        client.newCall(
            Request.Builder()
                .url(portalUrl("update-state").newBuilder().addQueryParameter("state", state.name).build())
                .build()
        ).execute().use { it.body.string() }
    }
    
    fun portalState(): PortalState = fetchPortalPage().toPortalState()
    
    private fun portalUrl(path: String) = "http://127.0.0.1:${environment.portalPort}/$path".toHttpUrl()
    
    private fun fetchPortalPage(): String =
        client.newCall(Request.Builder().url(portalUrl("")).build()).execute().use { it.body.string() }
    
    /**
     * Waits until the portal accepted the login of the app and no longer considers it captured.
     */
    fun awaitLiberated(timeout: Duration) {
        retry(timeout, interval = 500.milliseconds, description = "the portal to stop considering the app captured") {
            portalState() == PortalState.NOT_CAPTURED
        }
    }
    
    override fun close() {
        try {
            captureProxy.close()
        } finally {
            portalProxy?.close()
            portalProxy = null
        }
    }
}
