package de.binarynoise.captiveportalautologin.integration

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * The http proxy the emulator is configured to use for all of its traffic.
 *
 * Android applies the exclusion list of the global proxy to its own connectivity checks only, apps send all
 * of their traffic to the proxy. With a proxy that only speaks the portal protocol the api server of this
 * test would therefore be unreachable. This proxy behaves like a captive portal for the url the system probes
 * and forwards every other request to the destination it actually addresses.
 *
 * Requests for [probeTargets] are answered with a redirect to [redirectTarget] while [isCaptured] holds, and
 * with `204 No Content` afterwards, which is what makes the emulator report a captive portal until the app
 * logs in. `CONNECT` tunnels are refused, so that the connectivity checks of the system fail the way they do
 * behind a captive portal.
 */
class HttpCaptureProxy(
    private val listenPort: Int,
    probeTargets: Set<String>,
    private val redirectTarget: String,
    aliases: Map<String, String>,
    private val isCaptured: () -> Boolean,
    private val onRequest: (String) -> Unit = {},
    private val onResponse: (String, String) -> Unit = { _, _ -> },
    private val onError: (String?, Throwable) -> Unit = { _, _ -> },
) : AutoCloseable {
    // host names are case-insensitive, and clients are free to spell them the way they like
    private val probeTargets = probeTargets.map { it.lowercase() }.toSet()
    private val aliases = aliases.map { (destination, target) -> destination.lowercase() to target }.toMap()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val workers = Executors.newCachedThreadPool()
    
    val port: Int get() = serverSocket?.localPort ?: error("the proxy is not running")
    
    fun start() {
        check(running.compareAndSet(false, true)) { "the proxy is already running" }
        try {
            val socket = ServerSocket(listenPort, 50, InetAddress.getByName("0.0.0.0"))
            serverSocket = socket
            workers.submit {
                while (running.get() && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    workers.submit { handle(client) }
                }
            }
        } catch (failure: Exception) {
            running.set(false)
            throw failure
        }
    }
    
    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        workers.shutdown()
        if (!runCatching { workers.awaitTermination(5, TimeUnit.SECONDS) }.getOrDefault(true)) {
            workers.shutdownNow()
        }
    }
    
    private fun handle(client: Socket) {
        var target: String? = null
        try {
            client.use { client ->
                val request = Request.read(client.getInputStream()) ?: return
                target = request.target
                onRequest(request.target)
                val destination = request.target.asDestination() ?: return refuse(client.getOutputStream())
                when {
                    destination !in probeTargets -> forward(client, request, destination)
                    // the portal redirects to itself, so a probe is answered with the portal only while it is captured
                    isCaptured() -> redirect(client.getOutputStream())
                    else -> respond(client.getOutputStream(), 204, "No Content", emptyMap())
                }
            }
        } catch (failure: Exception) {
            onError(target, failure)
        }
    }
    
    /**
     * The `host:port` an absolute form request target addresses, which is what a client sends to a proxy.
     */
    private fun String.asDestination(): String? {
        val withoutScheme = substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty() || !startsWith("http://", ignoreCase = true)) return null
        val authority = withoutScheme.substringBefore('/')
        val host = authority.substringBefore(':').lowercase()
        val targetPort = authority.substringAfter(':', "80").toIntOrNull() ?: return null
        return aliases["$host:$targetPort"] ?: "$host:$targetPort"
    }
    
    private fun refuse(output: OutputStream) {
        respond(output, 400, "Bad Request", emptyMap(), "this proxy does not forward tunnelled connections")
    }
    
    private fun redirect(output: OutputStream) {
        respond(output, 303, "See Other", mapOf("Location" to redirectTarget))
    }
    
    private fun forward(client: Socket, request: Request, destination: String) {
        val host = destination.substringBefore(':')
        val targetPort = destination.substringAfter(':', "").toIntOrNull()
            ?: error("cannot forward to malformed destination $destination")
        Socket().use { upstream ->
            upstream.connect(
                InetSocketAddress(InetAddress.getByName(host), targetPort),
                forwardTimeout.inWholeMilliseconds.toInt(),
            )
            upstream.soTimeout = forwardTimeout.inWholeMilliseconds.toInt()
            upstream.getOutputStream().apply {
                write(request.originFormLine().toByteArray())
                request.headers.filterKeys { header -> header.lowercase() !in skippedRequestHeaders }
                    .forEach { (header, value) ->
                        write("$header: $value\r\n".toByteArray())
                    }
                write("Connection: close\r\n\r\n".toByteArray())
                request.copyBodyTo(this)
                flush()
            }
            val statusLine = Response.copy(upstream.getInputStream(), client.getOutputStream())
            onResponse(request.target, statusLine)
        }
    }
    
    private fun respond(
        output: OutputStream,
        code: Int,
        reason: String,
        headers: Map<String, String>,
        body: String = "",
    ) {
        val response = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            headers.forEach { (header, value) -> append("$header: $value\r\n") }
            append("Content-Length: ${body.toByteArray().size}\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
        output.write(response.toByteArray())
        output.flush()
    }
    
    private class Request(
        val method: String,
        val target: String,
        val httpVersion: String,
        val headers: Map<String, String>,
        private val source: InputStream,
    ) {
        fun originFormLine(): String {
            val path = target.substringAfter("://").substringAfter('/', "")
            return "$method /$path $httpVersion\r\n"
        }
        
        fun copyBodyTo(destination: OutputStream) {
            source.copyBody(headers, destination, readUntilEndWithoutLength = false)
        }
        
        companion object {
            fun read(source: InputStream): Request? {
                val requestLine = source.readLineUnbuffered() ?: return null
                val parts = requestLine.split(' ', limit = 3)
                return Request(
                    method = parts.getOrNull(0).orEmpty(),
                    target = parts.getOrNull(1).orEmpty(),
                    httpVersion = parts.getOrNull(2).orEmpty().ifEmpty { "HTTP/1.1" },
                    headers = source.readHeaders(),
                    source = source,
                )
            }
        }
    }
    
    /**
     * Copies a response verbatim, but framed by its own headers, so that the connection can be closed after it.
     */
    private object Response {
        fun copy(source: InputStream, destination: OutputStream): String {
            val statusLine = source.readLineUnbuffered() ?: return ""
            val headers = source.readHeaders()
            destination.write("$statusLine\r\n".toByteArray())
            headers.filterKeys { header -> header.lowercase() !in skippedResponseHeaders }.forEach { (header, value) ->
                destination.write("$header: $value\r\n".toByteArray())
            }
            destination.write("Connection: close\r\n\r\n".toByteArray())
            source.copyBody(headers, destination, readUntilEndWithoutLength = true)
            destination.flush()
            return statusLine
        }
    }
}

private val forwardTimeout = 30.seconds

private val skippedRequestHeaders = setOf("proxy-authorization", "proxy-connection", "connection", "keep-alive")

private val skippedResponseHeaders = setOf("connection", "keep-alive", "proxy-connection")
