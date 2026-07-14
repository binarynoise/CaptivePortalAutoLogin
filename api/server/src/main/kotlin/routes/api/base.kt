package de.binarynoise.captiveportalautologin.server.routes.api

import kotlin.time.Instant
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.hashLogFile
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.parseLogFileName
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import de.binarynoise.captiveportalautologin.server.routes.stats.logDB
import de.binarynoise.captiveportalautologin.server.routes.stats.logDBArchived
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
                call.respondStatus(HttpStatusCode.Created)
            }
        }
        route("/log") {
            put("/{name}") {
                val name = call.parameters["name"] ?: missingParameter("name")
                val parsed = try {
                    parseLogFileName(name)
                } catch (e: IllegalStateException) {
                    return@put call.respond(HttpStatusCode.BadRequest, e.message.toString())
                }
                if (logDB.exists(name) || logDBArchived.exists(name)) {
                    return@put call.respond(HttpStatusCode.Conflict, "file already exists")
                }
                val file = call.receive<String>()
                val checksum = hashLogFile(file)
                if (checksum != parsed.component3()) {
                    return@put call.respond(HttpStatusCode.BadRequest, "hash does not match")
                }
                ApiServer.api.log.submitLog(name, file)
                call.respond(HttpStatusCode.Created)
            }
        }
        route("/liberator") {
            put<Api.Liberator.Error>("error") { it: Api.Liberator.Error ->
                ApiServer.api.liberator.reportError(it)
                call.respondStatus(HttpStatusCode.Created)
            }
            put<Api.Liberator.Success>("success") { it: Api.Liberator.Success ->
                ApiServer.api.liberator.reportSuccess(it)
                call.respondStatus(HttpStatusCode.Created)
            }
        }
        get("/ssid") {
            call.respond(
                ApiServer.api.getSSIDs(
                    limit = call.queryParameters["limit"]?.toInt(),
                    majorVersion = call.queryParameters["majorVersion"]?.toInt(),
                    since = call.queryParameters["since"]?.let { Instant.parse(it) },
                    minimum = call.queryParameters["minimum"]?.toInt(),
                )
            )
        }
    }
}
