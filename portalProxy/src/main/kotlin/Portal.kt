package de.binarynoise.captiveportalautologin.portalproxy.portal

import kotlinx.coroutines.CoroutineScope
import kotlinx.html.*
import kotlinx.html.stream.*
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.coroutineRouter

private val database = HashMap<String, Boolean>()

fun CoroutineScope.portalRouter(vertx: Vertx): Router {
    val router = Router.router(vertx)
    
    coroutineRouter {
        // Root route
        router.get("/").handler { ctx ->
            servePortalPage(ctx.request())
        }
        
        // Login route
        router.route("/login").handler { ctx ->
            val ip = ctx.request().remoteAddress().host()
            database[ip] = false
            log("logged in $ip")
            ctx.response().putHeader("Location", "/").setStatusCode(302).end()
        }
        
        // Logout route
        router.route("/logout").handler { ctx ->
            val ip = ctx.request().remoteAddress().host()
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

fun redirect(request: HttpServerRequest) {
    request.response().putHeader("Location", "http://portal/").setStatusCode(303).end()
}

fun checkCaptured(request: HttpServerRequest): Boolean {
    val ip = request.remoteAddress().host()
    return database[ip] ?: true
}

private fun servePortalPage(request: HttpServerRequest) {
    val captured = checkCaptured(request)
    
    val html = createHTML().html {
        head {
            title { +"Captive Portal" }
        }
        body {
            h1 { +"Captive Portal" }
            p { +"You are currently ${if (captured) "captured" else "not captured"}" }
            
            form("/login", method = FormMethod.post) {
                p {
                    button(type = ButtonType.submit) { +"Get out of the portal" }
                }
            }
            
            form("/logout", method = FormMethod.post) {
                p {
                    button(type = ButtonType.submit) { +"Back into the portal" }
                }
            }
        }
    }
    
    request.response().putHeader("Content-Type", "text/html").end(html)
}
