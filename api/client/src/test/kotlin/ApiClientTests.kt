@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.createServer
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.serializer
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import io.ktor.server.engine.EmbeddedServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested

class ApiClientTests {
    
    private lateinit var server: ApiServer
    private lateinit var client: ApiClient
    
    @BeforeTest
    fun setup() {
        server = ApiServer(tempDirectory)
        ApiServer.api = server
        client = ApiClient(apiBase)
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
            val har = HAR(Log("", Creator("", ""), null, null, mutableListOf()))
            client.har.submitHar("test", har)
            assertEquals(har, serializer.decodeFromString(server.harDB.load("test")))
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
                    version = "test version",
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
        fun reportSuccess() {
            client.liberator.reportSuccess(
                Api.Liberator.Success(
                    version = "test version",
                    timestamp = System.currentTimeMillis(),
                    ssid = "test ssid",
                    url = "test url",
                    solver = "test solver",
                )
            )
        }
        
        @Test
        fun `reportSuccess - count`() {
            val success = Api.Liberator.Success(
                version = "test version",
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
