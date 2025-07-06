package de.binarynoise.captiveportalautologin.portalproxy

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.launch
import de.binarynoise.captiveportalautologin.portalproxy.portal.portalRouter
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forward
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forwardConnect
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait
import io.vertx.kotlin.coroutines.dispatcher

class MainVerticle : CoroutineVerticle() {
    override suspend fun start() {
        val router = Router.router(vertx)
        
        // Portal routes
        router.route().virtualHost("portal").handler { ctx ->
            log("route portal")
            ctx.next()
        }.subRouter(portalRouter(vertx))
        
        // Proxy routes
        router.route().handler { ctx ->
            log("route /http")
            forward(ctx.request())
        }
        
        val requestHandler: (HttpServerRequest) -> Unit = { request ->
            launch(vertx.dispatcher() + EmptyCoroutineContext) {
                log(buildString {
                    append("< ")
                    append(request.method())
                    append(" ")
                    append(request.uri())
                    append(" -> ")
                    append(request.scheme())
                    append(" ")
                    append(request.authority().host())
                    append(" : ")
                    append(request.authority().port())
                    append(" ")
                    append(request.path())
                    append(" from ")
                    append(request.remoteAddress().let { it.host().replace("0:0:0:0:0:0:0:1", "::1") + ":" + it.port() })
                })
                
                if (request.method() == HttpMethod.CONNECT) {
                    forwardConnect(request, vertx)
                } else {
                    router.handle(request)
                }
                
                log(buildString {
                    append("> ")
                    append(request.response()?.statusCode)
                    append(" ")
                    append(request.response()?.statusMessage)
                    append(" [")
                    append(request.response()?.headers()?.joinToString { "${it.key}: ${it.value}" })
                    append("] ")
                })
            }
        }
        
        val exceptionHandler = { t: Throwable ->
            log("Unhandled exception during connection", t)
        }
        val invalidRequestHandler = { r: HttpServerRequest ->
            log("Invalid request: $r")
        }
        
        val server = vertx.createHttpServer()
            .requestHandler(requestHandler)
            .exceptionHandler(exceptionHandler)
            .invalidRequestHandler(invalidRequestHandler)
            .listen(8000, "::")
            .coAwait()
        log("Started server on port " + server.actualPort())
    }
}

fun main() {
    Logger.Config.debugDump = true
    
    val vertx = Vertx.builder()/*.withTracer { o -> DebugTracer() }*/.build()
    vertx.exceptionHandler { e ->
        log("Unhandled exception", e)
    }
    vertx.deployVerticle(MainVerticle()).onFailure { e ->
        log("Failed to deploy verticle", e)
        vertx.close()
    }
}
