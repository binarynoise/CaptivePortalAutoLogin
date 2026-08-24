package de.binarynoise.captiveportalautologin.server.routes.api

import kotlin.time.Duration
import kotlin.time.Instant
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.isInRelativeRange
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import de.binarynoise.captiveportalautologin.server.routes.stats.Comparators
import de.binarynoise.captiveportalautologin.server.routes.toDuration
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

val feedbackPastAllowance: Duration = System.getenv("STATS_PAST_DURATION")?.toDuration() ?: Duration.INFINITE
val feedbackFutureAllowance: Duration = System.getenv("STATS_FUTURE_DURATION")?.toDuration() ?: Duration.ZERO

fun Routing.api() {
    route("/api") {
        get("/") {
            call.respondText("Welcome to Captive Portal Auto Login API")
        }
        route("/har") {
            put("/{name}") {
                val name = call.parameters["name"] ?: missingParameter("name")
                val har = call.receive<HAR>()
                if (!har.log.creator.version.matches(Comparators.VersionComparator.pattern)) {
                    return@put call.respondStatus(HttpStatusCode.UnprocessableEntity)
                }
                if (har.log.entries.isEmpty()) {
                    return@put call.respondStatus(HttpStatusCode.UnprocessableEntity)
                }
                ApiServer.api.har.submitHar(name, har)
                call.respondStatus(HttpStatusCode.Created)
            }
        }
        route("/liberator") {
            put<Api.Liberator.Error>("error") { it: Api.Liberator.Error ->
                if (!it.version.matches(Comparators.VersionComparator.pattern)) {
                    return@put call.respondStatus(HttpStatusCode.UnprocessableEntity)
                }
                val timestamp = Instant.fromEpochMilliseconds(it.timestamp)
                if (!timestamp.isInRelativeRange(minus = feedbackPastAllowance, plus = feedbackFutureAllowance)) {
                    return@put call.respondStatus(HttpStatusCode.NotAcceptable)
                }
                ApiServer.api.liberator.reportError(it)
                call.respondStatus(HttpStatusCode.Created)
            }
            put<Api.Liberator.Success>("success") { it: Api.Liberator.Success ->
                if (!it.version.matches(Comparators.VersionComparator.pattern)) {
                    return@put call.respondStatus(HttpStatusCode.UnprocessableEntity)
                }
                val timestamp = Instant.fromEpochMilliseconds(it.timestamp)
                if (!timestamp.isInRelativeRange(minus = feedbackPastAllowance, plus = feedbackFutureAllowance)) {
                    return@put call.respondStatus(HttpStatusCode.NotAcceptable)
                }
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
