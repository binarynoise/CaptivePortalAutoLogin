@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.server.Routing.api
import de.binarynoise.logger.Logger.log
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri

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
        log("responding to call ${call.request.httpMethod.value} ${call.request.origin.uri} with body $body")
    }
    
    on(CallFailed, handler = object : suspend (ApplicationCall, Throwable) -> Unit {
        override suspend fun invoke(call: ApplicationCall, cause: Throwable) {
            System.err.println("call failed:\n${cause.stackTraceToString()}")
        }
    })
    
    log("Logger is installed!")
}
