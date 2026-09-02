package de.binarynoise.captiveportalautologin.server

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Head
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.authorization
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond

private const val AUTH_SIGNATURE = "Signature"

val SignatureCheckPlugin: RouteScopedPlugin<Unit> = createRouteScopedPlugin(name = "SignatureCheckPlugin") {
    val signature = System.getenv("SIGNATURE")
    
    if (signature.isNullOrEmpty()) {
        return@createRouteScopedPlugin
    }
    
    onCall { call ->
        if (call.request.httpMethod in listOf(Get, Head, Options)) {
            return@onCall
        }
        
        if (!call.request.authorization().orEmpty().startsWith("$AUTH_SIGNATURE ")) {
            call.respond(HttpStatusCode.Unauthorized, "Missing authorization header")
            return@onCall
        }
        
        if (!call.request.authorization()
                .orEmpty()
                .removePrefix("$AUTH_SIGNATURE ")
                .equals(signature, ignoreCase = true)
        ) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid authorization header")
            return@onCall
        }
    }
}
