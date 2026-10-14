package de.binarynoise.captiveportalautologin.integration

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

class HttpCaptureProxyTest {
    @Test
    fun `answers probes with a redirect to the portal while captured`() {
        withProxy(captured = { true }) { proxyPort ->
            val response = requestThroughProxy(proxyPort, "GET http://portalHost:8000/")
            assertEquals("HTTP/1.1 303 See Other", response.statusLine)
            assertTrue("Location: http://portalHost:8001/" in response.headers, response.headers)
        }
    }
    
    @Test
    fun `answers probes with no content once liberated`() {
        withProxy(captured = { false }) { proxyPort ->
            val response = requestThroughProxy(proxyPort, "GET http://portalHost:8000/")
            assertEquals("HTTP/1.1 204 No Content", response.statusLine)
        }
    }
    
    @Test
    fun `forwards everything else to its destination`() {
        val received = CopyOnWriteArrayList<String>()
        withBackend("/api/liberator/success", handler = { exchange ->
            received += "${exchange.requestMethod} ${exchange.requestURI.path}"
            val body = """{"solver":"BinarynoisePortalProxy"}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }) { backendPort ->
            withProxy(
                captured = { true }, aliases = mapOf("apiHost:$backendPort" to "127.0.0.1:$backendPort")
            ) { proxyPort ->
                val response = requestThroughProxy(proxyPort, "PUT http://apiHost:$backendPort/api/liberator/success")
                assertEquals("HTTP/1.1 200 OK", response.statusLine)
                assertTrue("""{"solver":"BinarynoisePortalProxy"}""" in response.body, response.body)
            }
        }
        assertEquals(listOf("PUT /api/liberator/success"), received)
    }
    
    @Test
    fun `forwards the body of a post request`() {
        val received = CopyOnWriteArrayList<String>()
        val form = "user=someone&password=secret"
        withBackend("/login", handler = { exchange ->
            received += exchange.requestBody.readBytes().decodeToString()
            exchange.sendResponseHeaders(200, -1)
        }) { backendPort ->
            withProxy(
                captured = { true }, aliases = mapOf("portalHost:$backendPort" to "127.0.0.1:$backendPort")
            ) { proxyPort ->
                val response = requestThroughProxy(
                    proxyPort,
                    "POST http://portalHost:$backendPort/login",
                    "Content-Length: ${form.toByteArray().size}",
                ) { it.write(form.toByteArray()) }
                assertEquals("HTTP/1.1 200 OK", response.statusLine)
            }
        }
        assertEquals(listOf(form), received)
    }
    
    @Test
    fun `forwards chunked responses`() {
        withBackend("/chunked", handler = { exchange ->
            val body = "hello chunked".toByteArray()
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write(body) }
        }) { backendPort ->
            withProxy(
                captured = { true }, aliases = mapOf("portalHost:$backendPort" to "127.0.0.1:$backendPort")
            ) { proxyPort ->
                val response = requestThroughProxy(proxyPort, "GET http://portalHost:$backendPort/chunked")
                assertEquals("HTTP/1.1 200 OK", response.statusLine)
                assertTrue("hello chunked" in response.body, response.body)
            }
        }
    }
    
    @Test
    fun `refuses tunnelled connections`() {
        withProxy(captured = { true }) { proxyPort ->
            val response = requestThroughProxy(proxyPort, "CONNECT portalHost:443")
            assertEquals("HTTP/1.1 400 Bad Request", response.statusLine)
        }
    }
    
    private fun withProxy(
        captured: () -> Boolean,
        aliases: Map<String, String> = emptyMap(),
        block: (Int) -> Unit,
    ) {
        val proxy = HttpCaptureProxy(
            listenPort = 0,
            probeTargets = setOf("portalHost:8000"),
            redirectTarget = "http://portalHost:8001/",
            aliases = aliases,
            isCaptured = captured,
            onError = { target, failure -> throw AssertionError("forwarding $target failed", failure) },
        )
        proxy.start()
        proxy.use { proxy ->
            block(proxy.port)
        }
    }
    
    private fun withBackend(
        path: String,
        handler: (HttpExchange) -> Unit,
        block: (Int) -> Unit,
    ) {
        val backend = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        backend.createContext(path, handler)
        backend.start()
        try {
            block(backend.address.port)
        } finally {
            backend.stop(0)
        }
    }
    
    private class Response(val statusLine: String, val headers: String, val body: String)
    
    private fun requestThroughProxy(
        proxyPort: Int,
        requestLine: String,
        vararg headers: String,
        body: (OutputStream) -> Unit = {},
    ): Response {
        Socket("127.0.0.1", proxyPort).use { socket ->
            socket.soTimeout = 5000
            val request = buildString {
                append("$requestLine HTTP/1.1\r\n")
                append("Host: ${requestLine.substringAfter("://").substringBefore('/')}\r\n")
                headers.forEach { append("$it\r\n") }
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(request.toByteArray())
                body(this)
                flush()
            }
            val raw = socket.getInputStream().readBytes().decodeToString()
            val head = raw.substringBefore("\r\n\r\n")
            return Response(
                statusLine = head.substringBefore("\r\n"),
                headers = head,
                body = raw.substringAfter("\r\n\r\n"),
            )
        }
    }
}
