package de.binarynoise.captiveportalautologin.portalproxy

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.launch
import de.binarynoise.captiveportalautologin.portalproxy.portal.portalPort
import de.binarynoise.captiveportalautologin.portalproxy.portal.portalRouter
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forward
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forwardConnect
import de.binarynoise.captiveportalautologin.portalproxy.proxy.proxyPort
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
        
        // Portal routes
        val portalRouter = Router.router(vertx)
        portalRouter.route().handler { ctx ->
            log("route portal")
            ctx.next()
        }.subRouter(portalRouter(vertx))
        
        // Proxy routes
        val proxyRouter = Router.router(vertx)
        proxyRouter.route().handler { ctx ->
            log("route /http")
            val request = ctx.request()
            if (request.authority().port() == portalPort) {
                portalRouter.handle(request)
            } else {
                forward(request)
            }
        }
        
        val proxyRequestHandler: (HttpServerRequest) -> Unit = { request ->
            launch(vertx.dispatcher() + EmptyCoroutineContext) {
                log(buildString {
                    append("< ")
                    append(request.method())
                    append(" ")
                    append(request.uri())
                    append(" -> ")
                    append(request.scheme())
                    append(" ")
                    append(request.authority()?.host())
                    append(" : ")
                    append(request.authority()?.port())
                    append(" ")
                    append(request.path())
                    append(" from ")
                    append(request.remoteAddress().host().replace("0:0:0:0:0:0:0:1", "::1"))
                    append(":")
                    append(request.remoteAddress().port())
                })
                
                if (request.method() == HttpMethod.CONNECT) {
                    forwardConnect(request, vertx)
                } else {
                    proxyRouter.handle(request)
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
        
        val portalRequestHandler: (HttpServerRequest) -> Unit = { request ->
            launch(vertx.dispatcher() + EmptyCoroutineContext) {
                portalRouter.handle(request)
            }
        }
        
        val exceptionHandler = { t: Throwable ->
            log("Unhandled exception during connection", t)
        }
        val invalidRequestHandler = { r: HttpServerRequest ->
            log("Invalid request: $r")
        }
        
        val proxyServer = vertx.createHttpServer()
            .requestHandler(proxyRequestHandler)
            .exceptionHandler(exceptionHandler)
            .invalidRequestHandler(invalidRequestHandler)
            .listen(proxyPort, "::")
            .coAwait()
        log("Started proxy server on port " + proxyServer.actualPort())
        
        val portalServer = vertx.createHttpServer()
            .requestHandler(portalRequestHandler)
            .exceptionHandler(exceptionHandler)
            .invalidRequestHandler(invalidRequestHandler)
            .listen(portalPort, "::")
            .coAwait()
        log("Started portal server on port " + portalServer.actualPort())
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
