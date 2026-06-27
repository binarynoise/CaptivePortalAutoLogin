package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.reflect.jvm.javaMethod
import kotlinx.coroutines.CancellationException
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.FragmentKey
import com.github.mustachejava.Mustache
import de.binarynoise.captiveportalautologin.server.ApiServer.Companion.api
import de.binarynoise.captiveportalautologin.server.routes.configureRouting
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.serializer
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.mustache.Mustache
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respondText

val hostname = Path("/proc/sys/kernel/hostname").takeIf { it.exists() }?.readText()?.trim()
val isDevelopment = hostname != "captiveportalautologin"
val isRunningFromJar = ::main.javaMethod!!.declaringClass.protectionDomain.codeSource.location.path.endsWith(".jar")

fun main() {
    if (isDevelopment) DecoroutinatorJvmApi.install()
    
    val port = System.getenv("API_SERVER_PORT")?.toInt() ?: 8080
    val host = System.getenv("API_SERVER_HOST") ?: "::"
    log("launching server at $host:$port")
    val server = createServer(host, port)
    server.start(wait = true)
}

fun createServer(host: String, port: Int): EmbeddedServer<*, *> {
    System.setProperty("io.ktor.development", isDevelopment.toString())
    val server = embeddedServer(
        factory = Netty,
        port = port,
        host = host,
        watchPaths = if (isRunningFromJar) emptyList() else listOf("classes", "resources"),
        module = Application::module,
    )
    with(server.engineConfig) {
        shutdownTimeout = 1000
        enableHttp2 = false
//        enableH2c = false
    }
    return server
}

/*suspend*/ fun Application.module() { // TODO: make this suspend again for ktor >=3.2.0
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
        json(json = serializer)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is CancellationException -> throw cause
                else -> call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
            }
        }
        unhandled { call ->
            System.err.println("unhandled call: ${call.request.httpMethod.value} ${call.request.uri}")
        }
    }
    
    install(LoggingPlugin)
    
    configureRouting()
}
