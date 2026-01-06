package de.binarynoise.captiveportalautologin.server

import kotlinx.coroutines.CancellationException
import de.binarynoise.logger.Logger
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

val LoggingPlugin: ApplicationPlugin<Unit> = createApplicationPlugin(name = "LoggingPlugin") {
    onCallReceive { call, body ->
        val body = body.toString().substringBefore("\n").take(100)
        Logger.log("receiving call to ${call.request.httpMethod.value} ${call.request.origin.uri} with body $body")
    }
    onCallRespond { call, body ->
        Logger.log(buildString {
            append("responding to call ")
            append(call.request.httpMethod.value)
            append(" ")
            append(call.request.origin.uri)
            append(" with body ")
            append(body.toString().substringBefore("\n").take(100))
        })
    }
    
    on(CallFailed, handler = object : suspend (ApplicationCall, Throwable) -> Unit {
        override suspend fun invoke(call: ApplicationCall, cause: Throwable) {
            Logger.log("call failed", cause)
            if (cause is CancellationException) throw cause
        }
    })
    
    Logger.log("Logger is installed!")
}
