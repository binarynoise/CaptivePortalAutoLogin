package de.binarynoise.captiveportalautologin.portalproxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import de.binarynoise.captiveportalautologin.portalproxy.portal.PortalStateStore
import de.binarynoise.captiveportalautologin.portalproxy.portal.portalRouter
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forward
import de.binarynoise.captiveportalautologin.portalproxy.proxy.forwardConnect
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import io.netty.handler.codec.DecoderException
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.coAwait

class MainVerticle(
    private val proxyConfig: PortalProxyConfig,
    private val store: PortalStateStore = PortalStateStore(),
) : CoroutineVerticle() {
    override suspend fun start() {
        
        // Portal routes
        val portalRouter = Router.router(vertx)
        portalRouter.route().handler { ctx ->
            log("route portal")
            ctx.next()
        }.subRouter(portalRouter(vertx, proxyConfig, store))
        
        // Proxy routes
        val proxyRouter = Router.router(vertx)
        proxyRouter.route().handler { ctx ->
            log("route /http")
            val request = ctx.request()
            if (request.authority()?.port() == proxyConfig.portalPort) {
                portalRouter.handle(request)
            } else {
                forward(request, proxyConfig, store)
            }
        }
        
        val proxyRequestHandler: (HttpServerRequest) -> Unit = { request ->
            launch(Dispatchers.IO) {
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
                    forwardConnect(request, vertx, proxyConfig, store)
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
            portalRouter.handle(request)
        }
        
        val exceptionHandler = { t: Throwable ->
            if (t is DecoderException) {
                log("Decoder error: ${t.message}")
            } else {
                log("Unhandled exception during connection", t)
            }
        }
        
        val proxyServer = vertx.createHttpServer()
            .requestHandler(proxyRequestHandler)
            .exceptionHandler(exceptionHandler)
            .listen(proxyConfig.proxyPort, "0.0.0.0")
            .coAwait()
        log("Started proxy server on port " + proxyServer.actualPort())
        
        val portalServer = vertx.createHttpServer()
            .requestHandler(portalRequestHandler)
            .exceptionHandler(exceptionHandler)
            .listen(proxyConfig.portalPort, "0.0.0.0")
            .coAwait()
        log("Started portal server on port " + portalServer.actualPort())
        
        if (proxyConfig.portalHost != null) {
            log("Friendly host: ${proxyConfig.portalHost}")
        }
    }
}

fun main() {
    main(PortalProxyConfig.fromEnvironment())
}

fun main(config: PortalProxyConfig) {
    Logger.Config.debugDump = true
    
    // disable ipv6 so entries in the portal database are deterministic for dual stack clients
    System.setProperty("java.net.preferIPv4Stack", "true")
    
    val vertx = Vertx.builder()/*.withTracer { o -> DebugTracer() }*/.build()
    vertx.exceptionHandler { e ->
        log("Unhandled exception", e)
    }
    vertx.deployVerticle(MainVerticle(config)).onFailure { e ->
        log("Failed to deploy verticle", e)
        vertx.close()
    }
}
