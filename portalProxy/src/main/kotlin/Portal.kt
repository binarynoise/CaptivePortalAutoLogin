package de.binarynoise.captiveportalautologin.portalproxy.portal

import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope
import kotlinx.html.*
import kotlinx.html.stream.*
import de.binarynoise.captiveportalautologin.portalproxy.PortalProxyConfig
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coroutineRouter

class PortalStateStore {
    internal val database = ConcurrentHashMap<String, PortalState>()
}

enum class PortalState {
    CAPTURED, ERROR_STILL_CAPTURED, ERROR_403, NOT_CAPTURED;
    
    companion object {
        val names = entries.map(PortalState::name)
        
        @OptIn(ExperimentalContracts::class)
        operator fun contains(state: String?): Boolean {
            contract {
                returns(true) implies (state != null)
            }
            return state != null && names.contains(state)
        }
    }
}

fun CoroutineScope.portalRouter(vertx: Vertx, config: PortalProxyConfig, store: PortalStateStore): Router {
    val router = Router.router(vertx)
    
    coroutineRouter {
        // Root route
        router.get("/").handler { ctx ->
            servePortalPage(ctx.request(), config, store)
        }
        
        // Login route
        router.route("/login").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            val oldState = store.database[ip]
            when (oldState) {
                null, PortalState.CAPTURED -> {
                    log("logged in $ip")
                    store.database[ip] = PortalState.NOT_CAPTURED
                }
                PortalState.NOT_CAPTURED -> {}
                PortalState.ERROR_STILL_CAPTURED -> {
                    log("did not log in $ip (simulating still captured)")
                }
                PortalState.ERROR_403 -> {
                    log("did not log in $ip (simulating 403)")
                    ctx.response().setStatusCode(403).end()
                    return@handler
                }
            }
            redirect(ctx.request(), config)
        }
        
        // Logout route
        router.route("/logout").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            store.database[ip] = PortalState.CAPTURED
            log("logged out $ip")
            redirect(ctx.request(), config)
        }
        
        // Update state route
        router.route("/update-state").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            val oldState = store.database[ip]
            val newState = ctx.request().getParam("state")
            
            if (newState !in PortalState) {
                log("invalid state for $ip: $newState")
                ctx.response().setStatusCode(400).end()
                return@handler
            }
            
            store.database[ip] = PortalState.valueOf(newState)
            log("updated state for $ip: $oldState → $newState")
            redirect(ctx.request(), config)
        }
        
        // 404 handler
        router.route().handler { ctx ->
            log("404 for ${ctx.request().uri()}")
            ctx.response().setStatusCode(404).end()
        }
    }
    
    return router
}

fun getPortalHost(request: HttpServerRequest, config: PortalProxyConfig): String {
    return config.portalHost ?: request.getHeader("Host")!!.substringBefore(":")
}

fun redirect(request: HttpServerRequest, config: PortalProxyConfig) {
    val host = getPortalHost(request, config)
    request.response().putHeader("Location", "http://$host:${config.portalPort}/").setStatusCode(303).end()
}

fun getCaptured(request: HttpServerRequest, store: PortalStateStore): Boolean {
    val ip = request.getRealRemoteIP()
    return store.database[ip] != PortalState.NOT_CAPTURED
}

fun getPortalState(request: HttpServerRequest, store: PortalStateStore): PortalState {
    val ip = request.getRealRemoteIP()
    return store.database[ip] ?: PortalState.CAPTURED
}

private fun servePortalPage(request: HttpServerRequest, config: PortalProxyConfig, store: PortalStateStore) {
    val state = getPortalState(request, store)
    
    val html = createHTML().html {
        attributes += "lang" to "en"
        
        head {
            title { +"Captive Portal" }
            meta { name = "viewport"; content = "width=device-width, initial-scale=1" }
            meta { charset = "utf-8" }
            style {
                unsafe {
                    raw(
                        """
                            @media (prefers-color-scheme: dark) {
                                html {
                                    color-scheme: dark;
                                }
                            }
                            
                            html {
                                font-family: sans-serif;
                            }
                        """.trimIndent()
                    )
                }
            }
        }
        body {
            h1 { +"Captive Portal" }
            p { +"You are currently in state $state" }
            p {
                +"Your IP is "
                code { +request.getRealRemoteIP() }
            }
            
            form("/login") {
                p {
                    button(type = ButtonType.submit) { +"Get out of the portal" }
                }
            }
            
            form("/logout") {
                p {
                    button(type = ButtonType.submit) { +"Back into the portal" }
                }
            }
            
            p {
                +"This page can be opened again at"
                br()
                val href = "http://${getPortalHost(request, config)}:${config.portalPort}/"
                a(href = href) { +href }
            }
            
            
            h2 { +"Simulate State" }
            
            for (state in PortalState.entries) {
                form("/update-state") {
                    input(type = InputType.hidden, name = "state") { value = state.name }
                    p {
                        button(type = ButtonType.submit) { +"Set state to $state" }
                    }
                }
            }
        }
    }
    
    request.response().putHeader("Content-Type", "text/html").end("<!DOCTYPE html>\n$html")
}
