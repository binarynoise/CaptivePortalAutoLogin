package de.binarynoise.captiveportalautologin.server.routes.api

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.LOG
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun dateTime() = Clock.System.now().toLocalDateTime(TimeZone.UTC)

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
        route("/log") {
            put("/{name}") {
                val file = call.receive<LOG>()
                val name = dateTime().toString()
                ApiServer.api.log.submitLog(name, file)
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
