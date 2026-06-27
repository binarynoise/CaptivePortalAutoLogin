package de.binarynoise.captiveportalautologin.server.routes.api

import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Routing.api() {
    route("/api") {
        get("/") {
            call.respondText("Welcome to Captive Portal Auto Login API")
        }
        route("/har") {
            put("/{name}") {
                val name = call.parameters["name"] ?: missingParameter("name")
                val har = call.receive<HAR>()
                ApiServer.api.har.submitHar(name, har)
                call.respond(HttpStatusCode.Created)
            }
        }
        route("/liberator") {
            put<Api.Liberator.Error>("error") { it: Api.Liberator.Error ->
                ApiServer.api.liberator.reportError(it)
                call.respond(HttpStatusCode.Created)
            }
            put<Api.Liberator.Success>("success") { it: Api.Liberator.Success ->
                ApiServer.api.liberator.reportSuccess(it)
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}
