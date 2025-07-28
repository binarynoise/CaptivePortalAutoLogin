@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.Routing
import de.binarynoise.captiveportalautologin.server.Tables
import de.binarynoise.captiveportalautologin.server.module
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested

class ApiClientTests {
    
    private lateinit var server: ApiServer
    private lateinit var client: ApiClient
    
    private val base = "http://localhost:8080/".toHttpUrl()
    private val apiBase = base.resolve("api/")!!
    
    @BeforeTest
    fun setup() {
        server = ApiServer(tempDirectory)
        Routing.api = server
        client = ApiClient(apiBase)
    }
    
    companion object {
        private val tempDirectory: Path = Files.createTempDirectory("api-client-test")
        
        private val httpServer: EmbeddedServer<*, *> = embeddedServer(
            Netty,
            port = 8080,
            host = "::",
            module = Application::module,
        )
        
        @AfterAll
        @JvmStatic
        fun cleanup() {
            httpServer.stop()
            tempDirectory.deleteRecursively()
        }
        
        @BeforeAll
        @JvmStatic
        fun start() {
            httpServer.start(wait = false)
        }
    }
    
    @Nested
    inner class AHelloWorld {
        @Test
        fun `test hello world`() {
            val http = OkHttpClient()
            assertEquals("Hello World!", http.get(base, null).readText())
            assertEquals(
                "api/har/{name} here, name is test",
                http.get(apiBase, "har/test") { method("ECHO", null) }.readText(),
            )
        }
    }
    
    @Nested
    inner class Har {
        @Test
        fun submitHar() {
            val har = HAR(Log("", Creator("", ""), null, null, mutableListOf()))
            client.har.submitHar("test", har)
            assertEquals(har, server.jsonDb.load<HAR>("test", "har"))
        }
    }
    
    @Nested
    inner class Liberator {
        
        @Test
        @Ignore
        fun getLiberatorVersion() {
        }
        
        @Test
        @Ignore
        fun fetchLiberatorUpdate() {
        }
        
        @Test
        fun reportError() {
            client.liberator.reportError(
                Api.Liberator.Error(
                    "test ssid",
                    System.currentTimeMillis(),
                    "test host",
                    "test url",
                    "test error",
                )
            )
        }
        
        @Test
        fun reportSuccess() {
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    "test ssid",
                    System.currentTimeMillis(),
                    "test ssid",
                    "test url",
                )
            )
        }
        
        @Test
        fun `reportSuccess - count`() {
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    "test ssid",
                    System.currentTimeMillis(),
                    "test ssid",
                    "test url",
                )
            )
            val before = transaction {
                Tables.Successes.selectAll().where {
                    Tables.Successes.ssid eq "test ssid"
                    Tables.Successes.url eq "test url"
                }.let { result ->
                    assertEquals(1, result.count())
                    result.first()[Tables.Successes.count]
                }
            }
            log("before: $before")
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    "test ssid",
                    System.currentTimeMillis(),
                    "test ssid",
                    "test url",
                )
            )
            val after = transaction {
                Tables.Successes.selectAll().where {
                    Tables.Successes.ssid eq "test ssid"
                    Tables.Successes.url eq "test url"
                }.let { result ->
                    assertEquals(1, result.count())
                    result.first()[Tables.Successes.count]
                }
            }
            log("after: $after")
            assertEquals(before + 1, after)
        }
    }
}
