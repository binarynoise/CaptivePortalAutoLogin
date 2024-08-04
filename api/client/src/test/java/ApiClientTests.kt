@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.server.ApiImpl
import de.binarynoise.captiveportalautologin.server.api
import de.binarynoise.captiveportalautologin.server.module
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.post
import de.binarynoise.util.okhttp.readText
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterAll

object ApiClientTests {
    val tempDirectory = Files.createTempDirectory("api-client-test")
    
    private val httpServer: NettyApplicationEngine = embeddedServer(
        Netty,
        port = 8080,
        host = "::",
        module = Application::module,
    ).apply {
        application.install(LoggingPlugin)
        start(wait = false)
    }
    
    @AfterAll
    @JvmStatic
    fun cleanup() {
        tempDirectory.deleteRecursively()
        httpServer.stop()
    }
    
    @Test
    fun `test hello world`() {
        val client = OkHttpClient()
        assertEquals("Hello World!", client.get(base, null).readText())
        assertEquals("api/har/{name} here, name is test", client.get(apiBase, "har/test") { method("echo", null) }.readText())
        try {
            client.post(apiBase, "har/test") {
                post("{}".toRequestBody(MEDIA_TYPE_JSON))
            }.checkSuccess()
            fail("Did not throw")
        } catch (e: IllegalStateException) {
            assertEquals("HTTP error: 400 Bad Request", e.message)
        }
    }
    
    private val base = "http://localhost:8080/".toHttpUrl()
    private val apiBase = base.resolve("api/")!!
    
    @Test
    fun `test submit har`() {
        val server = ApiImpl(tempDirectory)
        api = server
        val client = ApiClient(apiBase)
        
        val har = HAR(Log("", Creator("", ""), null, null, mutableListOf()))
        
        client.har.submitHar("test", har)
        assertEquals(har, server.db.load<HAR>("test", "har"))
    }
}

val LoggingPlugin: ApplicationPlugin<Unit> = createApplicationPlugin(name = "LoggingPlugin") {
    onCallReceive { call, body ->
        println("receiving call to ${call.request.httpMethod} ${call.request.origin.uri} with body $body")
    }
    onCallRespond { call, body ->
        println("responding to call ${call.request.httpMethod} ${call.request.origin.uri} with body $body")
    }
    
    on(CallFailed, handler = object : suspend (ApplicationCall, Throwable) -> Unit {
        override suspend fun invoke(call: ApplicationCall, cause: Throwable) {
            System.err.println("call failed:\n${cause.stackTraceToString()}")
        }
    })
    
    println("Logger is installed!")
}
