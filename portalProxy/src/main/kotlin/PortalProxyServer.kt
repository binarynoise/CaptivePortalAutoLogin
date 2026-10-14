package de.binarynoise.captiveportalautologin.portalproxy

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import de.binarynoise.captiveportalautologin.portalproxy.portal.PortalStateStore
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.coAwait
import kotlinx.coroutines.runBlocking

/**
 * Runs the portal proxy in this process, on the [Vertx] instance it owns.
 *
 * This is what the integration tests use. The deployed service runs [main] instead,
 * which never stops, like `deployment/portal-proxy.service` expects.
 */
class PortalProxyServer(
    val config: PortalProxyConfig,
    val store: PortalStateStore = PortalStateStore(),
) : AutoCloseable {
    private var vertx: Vertx? = null
    private var deploymentId: String? = null
    private val closed = AtomicBoolean(false)
    
    val isRunning: Boolean get() = !closed.get() && deploymentId != null
    
    fun start() {
        check(deploymentId == null) { "the portal proxy is already running" }
        closed.set(false)
        val vertx = Vertx.builder().build()
        try {
            runBlocking {
                vertx.exceptionHandler { e ->
                    log("Unhandled exception", e)
                }
                deploymentId = vertx.deployVerticle(MainVerticle(config, store)).coAwait()
            }
            this.vertx = vertx
        } catch (failure: Exception) {
            runCatching { vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS) }
            deploymentId = null
            throw failure
        }
    }
    
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        deploymentId = null
        val vertx = vertx
        this.vertx = null
        if (vertx != null) {
            runCatching {
                runBlocking { vertx.close().coAwait() }
            }.onFailure { failure ->
                println("warning: closing the portal proxy failed with $failure")
            }
        }
    }
}
