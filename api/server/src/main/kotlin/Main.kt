@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.server.Routing.api
import de.binarynoise.logger.Logger.log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*

fun main() {
    api = ApiServer(Path("."))
    
    embeddedServer(
        Netty,
        port = 8080,
        host = "::",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(json = Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        })
    }
    install(StatusPages) {
        unhandled { call ->
            System.err.println("unhandled call: ${call.request.httpMethod.value} ${call.request.uri}")
        }
    }
    
    install(LoggingPlugin)
    
    configureRouting()
}


val LoggingPlugin: ApplicationPlugin<Unit> = createApplicationPlugin(name = "LoggingPlugin") {
    onCallReceive { call, body ->
        log("receiving call to ${call.request.httpMethod.value} ${call.request.origin.uri} with body $body")
    }
    onCallRespond { call, body ->
        log(buildString {
            append("responding to call ")
            append(call.request.httpMethod.value)
            append(" ")
            append(call.request.origin.uri)
            append(" with body ")
            append(body.toString().substringBefore("\n"))
        })
    }
    
    on(CallFailed, handler = object : suspend (ApplicationCall, Throwable) -> Unit {
        override suspend fun invoke(call: ApplicationCall, cause: Throwable) {
            System.err.println("call failed:\n${cause.stackTraceToString()}")
        }
    })
    
    log("Logger is installed!")
}
