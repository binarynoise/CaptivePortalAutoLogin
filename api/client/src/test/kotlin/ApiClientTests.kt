@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.createServer
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import io.ktor.server.engine.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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
        ApiServer.api = server
        client = ApiClient(apiBase)
    }
    
    companion object {
        private val tempDirectory: Path = Files.createTempDirectory("api-client-test")
        
        private val httpServer: EmbeddedServer<*, *> = createServer()
        
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
            assertEquals("Welcome to Captive Portal Auto Login API", http.get(apiBase, null).readText())
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
                    "test solver",
                    "test stack trace",
                )
            )
        }
        
        @Test
        fun reportSuccess() {
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    "test ssid",
                    System.currentTimeMillis(),
                    "test url",
                    "test solver",
                    "test ssid",
                )
            )
        }
        
        @Test
        fun `reportSuccess - count`() {
            val success = Api.Liberator.Success(
                "test version",
                System.currentTimeMillis(),
                "test ssid",
                "test url",
                "test solver",
            )
            
            client.liberator.reportSuccess(success)
            
            val dateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val count = runBlocking {
                server.database.successDao().getCount(
                    success.version,
                    dateTime.year,
                    dateTime.month.number,
                    success.ssid,
                    success.url,
                    success.solver.orEmpty()
                )
            }
            assertEquals(1, count)
            
            client.liberator.reportSuccess(success)
            
            val count2 = runBlocking {
                server.database.successDao().getCount(
                    success.version,
                    dateTime.year,
                    dateTime.month.number,
                    success.ssid,
                    success.url,
                    success.solver.orEmpty()
                )
            }
            assertEquals(2, count2)
        }
    }
}
