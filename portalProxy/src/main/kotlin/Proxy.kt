@file:OptIn(ExperimentalAtomicApi::class)

package de.binarynoise.captiveportalautologin.portalproxy.proxy

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import de.binarynoise.captiveportalautologin.portalproxy.portal.checkCaptured
import de.binarynoise.captiveportalautologin.portalproxy.portal.redirect
import de.binarynoise.logger.Logger.log
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.net.NetSocket
import io.vertx.kotlin.coroutines.coAwait

private val allowlistDomain = listOf("localhost", "127.0.0.1", "www.google.com", "am-i-captured.binarynoise.de")
private val allowlistPort = listOf("80", "443")

fun forward(ctx: HttpServerRequest) {
    if (checkCaptured(ctx)) {
        redirect(ctx)
        return
    }
    
    ctx.response().setStatusCode(204).end()
}

suspend fun forwardConnect(request: HttpServerRequest, vertx: Vertx) {
    try {
        if (checkCaptured(request)) {
            log("captured")
            redirect(request)
            return
        }
        
        val uri = request.uri()
        log("connect request to $uri")
        
        val hostPort = uri.split(":", limit = 2)
        if (hostPort.size != 2) {
            request.response().setStatusCode(400).end("Invalid CONNECT request")
            return
        }
        
        val (host, port) = hostPort
        
        if (host !in allowlistDomain || port !in allowlistPort) {
            request.response().setStatusCode(405).end("CONNECT to $host:$port is not allowed")
            return
        }
        
        // Create TCP client for tunneling
        val netClient = vertx.createNetClient()
        
        val netSocket = try {
            // Connect to the target server
            netClient.connect(port.toInt(), host).coAwait()
        } catch (e: Exception) {
            log("Failed to connect to $host:$port", e)
            request.response().setStatusCode(502).end("Failed to connect to $host:$port")
            return
        }
        handleServerConnected(request, netSocket)
    } catch (e: Exception) {
        log("Failed to handle CONNECT request", e)
        request.response().setStatusCode(500).end("Failed to handle CONNECT request")
    }
}

private suspend fun handleServerConnected(request: HttpServerRequest, serverSocket: NetSocket) {
    // Send 200 OK to client to establish the tunnel
    val clientSocket = request.toNetSocket().coAwait()
    
    // Handle data flow between client and server
    serverSocket.handler { data ->
        if (!clientSocket.writeQueueFull()) {
            clientSocket.write(data)
        } else {
            // Pause reading from server if client is busy
            serverSocket.pause()
            clientSocket.drainHandler {
                serverSocket.resume()
            }
        }
    }
    
    clientSocket.handler { data ->
        if (!serverSocket.writeQueueFull()) {
            serverSocket.write(data)
        } else {
            // Pause reading from client if server is busy
            clientSocket.pause()
            serverSocket.drainHandler {
                clientSocket.resume()
            }
        }
    }
    
    val serverClosed = AtomicBoolean(false)
    val clientClosed = AtomicBoolean(false)
    
    // Handle connection close
    serverSocket.closeHandler {
        serverClosed.store(true)
        if (!clientClosed.load()) {
            clientSocket.close()
        }
    }
    
    clientSocket.closeHandler {
        clientClosed.store(true)
        if (!serverClosed.load()) {
            serverSocket.close()
        }
    }
    
    // Handle errors
    serverSocket.exceptionHandler { cause ->
        log("Server socket error", cause)
        if (!clientClosed.load()) {
            clientSocket.close()
        }
    }
    
    clientSocket.exceptionHandler { cause ->
        log("Client socket error", cause)
        if (!serverClosed.load()) {
            serverSocket.close()
        }
    }
}
