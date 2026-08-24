@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.Cache
import de.binarynoise.captiveportalautologin.api.json.har.Content
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.Entry
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.api.json.har.Request
import de.binarynoise.captiveportalautologin.api.json.har.Response
import de.binarynoise.captiveportalautologin.api.json.har.Timings
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.createServer
import de.binarynoise.captiveportalautologin.server.routes.api.feedbackFutureAllowance
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.serializer
import de.binarynoise.util.okhttp.HttpStatusCodeException
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import io.ktor.server.engine.EmbeddedServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

class ApiClientTests {
    
    private val validTestVersion = "1-deadbe-00000000"
    
    private lateinit var server: ApiServer
    private lateinit var client: ApiClient
    
    @BeforeTest
    fun setup() {
        tempDirectory.deleteRecursively()
        server = ApiServer(tempDirectory)
        ApiServer.api = server
        client = ApiClient(apiBase, null)
    }
    
    companion object {
        private val tempDirectory: Path = Files.createTempDirectory("api-client-test")
        
        private val httpServer: EmbeddedServer<*, *> = createServer("::", 0)
        
        lateinit var base: HttpUrl
        lateinit var apiBase: HttpUrl
        
        @BeforeAll
        @JvmStatic
        fun start() {
            httpServer.start(wait = false)
            val port = runBlocking { httpServer.engine.resolvedConnectors().first().port }
            log("port: $port")
            base = "http://localhost:$port/".toHttpUrl()
            apiBase = base.resolve("api/")!!
        }
        
        @AfterAll
        @JvmStatic
        fun cleanup() {
            httpServer.stop()
            tempDirectory.deleteRecursively()
        }
    }
    
    @Nested
    inner class AHelloWorld {
        @Test
        fun `test hello world`() {
            val http = OkHttpClient()
            assertEquals("Welcome to Captive Portal Auto Login API", http.get(apiBase, null).readText())
        }
    }
    
    @Nested
    inner class Har {
        @Test
        fun submitHar() {
            val har = HAR(
                Log(
                    "", Creator("", validTestVersion), null, null, mutableListOf(
                        Entry(
                            null, Clock.System.now().toLocalDateTime(UTC),
                            Request("GET", "", "", mutableSetOf(), mutableSetOf(), mutableListOf(), null, 0, 0),
                            Response(200, "", "", mutableSetOf(), mutableSetOf(), Content(0, "", null, null), "", 0, 0),
                            Cache(),
                            Timings(),
                            null,
                            null,
                        )
                    )
                )
            )
            client.har.submitHar("test", har)
            assertEquals(har, serializer.decodeFromString(server.harDB.load("test")))
        }
        
        @Test
        fun submitEmptyHar() {
            val har = HAR(Log("", Creator("", validTestVersion), null, null, mutableListOf()))
            
            assertThrows<HttpStatusCodeException> {
                client.har.submitHar("test", har)
            }
            assertFalse { server.harDB.exists("test") }
        }
        
        @Test
        fun submitHarWithInvalidVersion() {
            val har = HAR(Log("", Creator("", ""), null, null, mutableListOf()))
            assertThrows<HttpStatusCodeException> {
                client.har.submitHar("test", har)
            }
            assertFalse { server.harDB.exists("test") }
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
                    version = validTestVersion,
                    timestamp = System.currentTimeMillis(),
                    ssid = "test ssid",
                    url = "test url",
                    message = "test error",
                    solver = "test solver",
                    stackTrace = "test stack trace",
                    har = null,
                )
            )
        }
        
        @Test
        fun `reportError - invalid version`() {
            assertThrows<HttpStatusCodeException> {
                client.liberator.reportError(
                    Api.Liberator.Error(
                        version = "",
                        timestamp = System.currentTimeMillis(),
                        ssid = "test ssid",
                        url = "test url",
                        message = "test error",
                        solver = "test solver",
                        stackTrace = "test stack trace",
                        har = null,
                    )
                )
            }
            assert(runBlocking { server.database.errorDao().getAll().isEmpty() })
        }
        
        @Test
        fun reportSuccess() {
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    version = validTestVersion,
                    timestamp = System.currentTimeMillis(),
                    ssid = "test ssid",
                    url = "test url",
                    solver = "test solver",
                )
            )
        }
        
        @Test
        fun `reportSuccess - invalid version`() {
            assert(runBlocking { server.database.successDao().getAll().isEmpty() })
            assertThrows<HttpStatusCodeException> {
                client.liberator.reportSuccess(
                    Api.Liberator.Success(
                        version = "",
                        timestamp = System.currentTimeMillis(),
                        ssid = "test ssid",
                        url = "test url",
                        solver = "test solver",
                    )
                )
            }
            assert(runBlocking { server.database.successDao().getAll().isEmpty() })
        }
        
        @Test
        fun `reportSuccess - future submission`() {
            assertThrows<HttpStatusCodeException> {
                client.liberator.reportSuccess(
                    Api.Liberator.Success(
                        version = validTestVersion,
                        timestamp = System.currentTimeMillis() + feedbackFutureAllowance.inWholeMilliseconds + 1000,
                        ssid = "test ssid",
                        url = "test url",
                        solver = "test solver",
                    )
                )
            }
            assert(runBlocking { server.database.successDao().getAll().isEmpty() })
        }
        
        @Test
        fun `reportSuccess - count`() {
            val success = Api.Liberator.Success(
                version = validTestVersion,
                timestamp = System.currentTimeMillis(),
                ssid = "test ssid",
                url = "test url",
                solver = "test solver",
            )
            
            client.liberator.reportSuccess(success)
            
            val count = runBlocking {
                server.database.successDao().getAll().size
            }
            assertEquals(1, count)
            
            client.liberator.reportSuccess(success)
            
            val count2 = runBlocking {
                server.database.successDao().getAll().size
            }
            assertEquals(2, count2)
        }
    }
}
