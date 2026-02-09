package de.binarynoise.captiveportalautologin.portalproxy.portal

import java.util.concurrent.*
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

private val database = ConcurrentHashMap<String, Boolean>()

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
            
            database[ip] = false
            log("logged in $ip")
            ctx.response().putHeader("Location", "/").setStatusCode(302).end()
        }
        
        // Logout route
        router.route("/logout").handler { ctx ->
            val ip = ctx.request().getRealRemoteIP()
            
            database[ip] = true
            log("logged out $ip")
            redirect(ctx.request())
        }
        
        // 404 handler
        router.route().handler { ctx ->
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

fun checkCaptured(request: HttpServerRequest): Boolean {
    val ip = request.getRealRemoteIP()
    return database[ip] ?: true
}

private fun servePortalPage(request: HttpServerRequest) {
    val captured = checkCaptured(request)
    
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
            p { +"You are currently ${if (captured) "captured" else "not captured"}" }
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
        }
    }
    
    request.response().putHeader("Content-Type", "text/html").end("<!DOCTYPE html>\n$html")
}
