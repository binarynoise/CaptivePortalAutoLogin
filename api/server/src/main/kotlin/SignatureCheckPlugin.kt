package de.binarynoise.captiveportalautologin.server

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Head
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.authorization
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond

private const val AUTH_SIGNATURE = "Signature"

val SignatureCheckPlugin: ApplicationPlugin<Unit> = createApplicationPlugin(name = "SignatureCheckPlugin") {
    val signature = System.getenv("SIGNATURE")
    
    if (signature.isNullOrEmpty()) {
        return@createApplicationPlugin
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
