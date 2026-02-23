@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.FragmentKey
import com.github.mustachejava.Mustache
import de.binarynoise.captiveportalautologin.server.ApiServer.Companion.api
import de.binarynoise.captiveportalautologin.server.routes.configureRouting
import de.binarynoise.logger.Logger.log
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.mustache.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*

val hostname = Path("/proc/sys/kernel/hostname").takeIf { it.exists() }?.readText()?.trim()
val isDevelopment = hostname != "captiveportalautologin"

fun main() {
    val server = createServer("::", 8080)
    server.start(wait = true)
}

fun createServer(host: String, port: Int): EmbeddedServer<*, *> {
    System.setProperty("io.ktor.development", isDevelopment.toString())
    val server = embeddedServer(
        factory = Netty,
        port = port,
        host = host,
        watchPaths = listOf("classes", "resources"),
        module = Application::module,
    )
    with(server.engineConfig) {
        shutdownTimeout = 1000
        enableHttp2 = false
        enableH2c = false
    }
    return server
}

suspend fun Application.module() {
    api = ApiServer(Path(System.getenv("API_SERVER_PATH") ?: "."))
    
    check(developmentMode == isDevelopment) { "developmentMode != isDevelopment" }
    log("launching in ${if (isDevelopment) "development" else "production"} mode")
    
    install(Mustache) {
        mustacheFactory = object : DefaultMustacheFactory("templates") {
            override fun compile(name: String?): Mustache? {
                val mustache = super.compile(name)
                if (isDevelopment) {
                    mustacheCache.clear()
                }
                return mustache
            }
            
            override fun getFragment(templateKey: FragmentKey?): Mustache? {
                val fragment = super.getFragment(templateKey)
                if (isDevelopment) {
                    mustacheCache.clear()
                }
                return fragment
            }
        }
    }
    install(ContentNegotiation) {
        json(json = Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        })
    }
    install(StatusPages) {
        exception<CancellationException> { call, cause ->
            throw cause
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Illegal Arguments")
        }
        unhandled { call ->
            System.err.println("unhandled call: ${call.request.httpMethod.value} ${call.request.uri}")
        }
    }
    
    install(LoggingPlugin)
    
    configureRouting()
}
