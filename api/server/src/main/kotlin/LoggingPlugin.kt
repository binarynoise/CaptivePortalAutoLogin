package de.binarynoise.captiveportalautologin.server

import kotlinx.coroutines.CancellationException
import de.binarynoise.logger.Logger
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.util.AttributeKey

private val ResponseBodyKey = AttributeKey<Any>("LoggingPlugin.responseBody")

val LoggingPlugin: ApplicationPlugin<Unit> = createApplicationPlugin(name = "LoggingPlugin") {
    onCallReceive { call, body ->
        val body = body.toString().substringBefore("\n").take(100)
        Logger.log("receiving call to ${call.request.httpMethod.value} ${call.request.origin.uri} with body $body")
    }
    onCallRespond { call, body ->
        call.attributes.put(ResponseBodyKey, body)
    }
    on(ResponseSent) { call ->
        val body = call.attributes.getOrNull(ResponseBodyKey)
        Logger.log(buildString {
            append("responded to call ")
            append(call.request.httpMethod.value)
            append(" ")
            append(call.request.origin.uri)
            append(" with status code ")
            append(call.response.status()?.value)
            append(" and body '")
            append(body.toString().substringBefore("\n").take(100))
            append("'")
        })
    }
    
    on(CallFailed, handler = object : suspend (ApplicationCall, Throwable) -> Unit {
        override suspend fun invoke(call: ApplicationCall, cause: Throwable) {
            when (cause) {
                is CancellationException -> throw cause
                else -> Logger.log("call failed", cause)
            }
        }
    })
    
    Logger.log("Logger is installed!")
}
