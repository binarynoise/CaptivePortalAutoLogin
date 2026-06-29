package de.binarynoise.captiveportalautologin.portalproxy.portal

import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope
import kotlinx.html.*
import kotlinx.html.stream.*
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coroutineRouter

val portalPort = System.getenv("PORTAL_PORT")?.toInt() ?: 8001
val friendlyHost: String? = System.getenv("PORTAL_HOST")

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

private val database = ConcurrentHashMap<String, PortalState>()

fun CoroutineScope.portalRouter(vertx: Vertx): Router {
    val router = Router.router(vertx)
    
    coroutineRouter {
        // Root route
        router.get("/").handler { ctx ->
            servePortalPage(ctx.request())
        }
        
        // Login route
        router.route("/login").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            val oldState = database[ip]
            when (oldState) {
                null, PortalState.CAPTURED -> {
                    log("logged in $ip")
                    database[ip] = PortalState.NOT_CAPTURED
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
            redirect(ctx.request())
        }
        
        // Logout route
        router.route("/logout").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            database[ip] = PortalState.CAPTURED
            log("logged out $ip")
            redirect(ctx.request())
        }
        
        // Update state route
        router.route("/update-state").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            val oldState = database[ip]
            val newState = ctx.request().getParam("state")
            
            if (newState !in PortalState) {
                log("invalid state for $ip: $newState")
                ctx.response().setStatusCode(400).end()
                return@handler
            }
            
            database[ip] = PortalState.valueOf(newState)
            log("updated state for $ip: $oldState → $newState")
            redirect(ctx.request())
        }
        
        // 404 handler
        router.route().handler { ctx ->
            log("404 for ${ctx.request().uri()}")
            ctx.response().setStatusCode(404).end()
        }
    }
    
    return router
}

fun getPortalHost(request: HttpServerRequest): String {
    return friendlyHost ?: request.getHeader("Host")!!.substringBefore(":")
}

fun redirect(request: HttpServerRequest) {
    val host = getPortalHost(request)
    request.response().putHeader("Location", "http://$host:$portalPort/").setStatusCode(303).end()
}

fun getCaptured(request: HttpServerRequest): Boolean {
    val ip = request.getRealRemoteIP()
    return database[ip] != PortalState.NOT_CAPTURED
}

fun getPortalState(request: HttpServerRequest): PortalState {
    val ip = request.getRealRemoteIP()
    return database[ip] ?: PortalState.CAPTURED
}

private fun servePortalPage(request: HttpServerRequest) {
    val state = getPortalState(request)
    
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
                val href = "http://${getPortalHost(request)}:$portalPort/"
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
