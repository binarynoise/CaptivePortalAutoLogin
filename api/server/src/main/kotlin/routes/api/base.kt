package de.binarynoise.captiveportalautologin.server.routes.api

import kotlin.time.Duration
import kotlin.time.Instant
import CaptivePortalAutoLogin.api.server.BuildConfig
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.hashLogFile
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.parseLogFileName
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.SignatureCheckPlugin
import de.binarynoise.captiveportalautologin.server.routes.isInRelativeRange
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import de.binarynoise.captiveportalautologin.server.routes.stats.Comparators
import de.binarynoise.captiveportalautologin.server.routes.stats.logDB
import de.binarynoise.captiveportalautologin.server.routes.stats.logDBArchived
import de.binarynoise.captiveportalautologin.server.routes.toDuration
import de.binarynoise.liberator.portals.allPortalLiberatorsFileMapping
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

// limit feedback record time
val feedbackPastAllowance: Duration = System.getenv("STATS_PAST_DURATION")?.toDuration() ?: Duration.INFINITE
val feedbackFutureAllowance: Duration = System.getenv("STATS_FUTURE_DURATION")?.toDuration() ?: Duration.ZERO

// limit feedback version age
val feedbackVersionPastAllowance: Duration =
    System.getenv("STATS_VERSION_PAST_DURATION")?.toDuration() ?: Duration.INFINITE
val feedbackVersionFutureAllowance: Duration =
    System.getenv("STATS_VERSION_FUTURE_DURATION")?.toDuration() ?: Duration.ZERO

// reject unknown version commits and unknown solvers
val feedbackVersionStrictMode: Boolean = System.getenv("STATS_VERSION_STRICT_MODE")?.isNotEmpty() ?: false

fun getCommitByPartialHash(partialHash: String): Map.Entry<String, Long>? {
    return BuildConfig.GITCOMMITS.entries.find { it.key.startsWith(partialHash) }
}

/**
 * Check whether this feedback adheres to the defined limits.
 * Any value of `null` means this field is not evaluated. 
 * @return true if the request is ok, false if rejected, [call] will be answered already
 */
suspend fun enforceFeedbackLimits(
    call: RoutingCall,
    sourceObject: Any,
    version: String?,
    timestamp: Long?,
    solver: String?,
): Boolean {
    if (version != null) {
        val match = Comparators.VersionComparator.pattern.matchEntire(version)
        if (match == null) {
            call.respondStatus(HttpStatusCode.UnprocessableEntity)
            return false
        }
        val versionCommit = getCommitByPartialHash(match.groups["hash"]!!.value)
        if (versionCommit == null && feedbackVersionStrictMode) {
            call.respondStatus(HttpStatusCode.NotAcceptable)
            return false
        }
        if (versionCommit != null) {
            val versionTimestamp = Instant.fromEpochSeconds(versionCommit.value)
            if (!versionTimestamp.isInRelativeRange(
                    minus = feedbackVersionPastAllowance, plus = feedbackVersionFutureAllowance
                )
            ) {
                call.respondStatus(HttpStatusCode.NotAcceptable)
                return false
            }
            if (solver != null) {
                val solverFileName = allPortalLiberatorsFileMapping[solver]
                val solverTimestamp =
                    BuildConfig.PORTALLIBERATORCOMMITTERDATES[solverFileName]?.let(Instant::fromEpochSeconds)
                if (solverFileName != null && solverTimestamp != null) {
                    if (sourceObject is Api.Liberator.Error && versionTimestamp < solverTimestamp) {
                        call.respondStatus(HttpStatusCode.UpgradeRequired)
                        return false
                    }
                } else if (feedbackVersionStrictMode) {
                    call.respondStatus(HttpStatusCode.NotAcceptable)
                    return false
                }
            }
        }
    }
    if (timestamp != null) {
        val timestamp = Instant.fromEpochMilliseconds(timestamp)
        if (!timestamp.isInRelativeRange(minus = feedbackPastAllowance, plus = feedbackFutureAllowance)) {
            call.respondStatus(HttpStatusCode.NotAcceptable)
            return false
        }
    }
    return true
}

fun harPutHandler(): RoutingHandler = call@{
    val name = call.parameters["name"] ?: missingParameter("name")
    val har = call.receive<HAR>()
    if (!enforceFeedbackLimits(call, har, har.log.creator.version, null, null)) return@call
    if (har.log.entries.isEmpty()) {
        return@call call.respondStatus(HttpStatusCode.UnprocessableEntity)
    }
    ApiServer.api.har.submitHar(name, har)
    call.respondStatus(HttpStatusCode.Created)
}

fun logPutHandler(): RoutingHandler = call@{
    val name = call.parameters["name"] ?: missingParameter("name")
    val (_, _, parsedChecksum) = try {
        parseLogFileName(name)
    } catch (e: IllegalStateException) {
        return@call call.respond(HttpStatusCode.BadRequest, e.message.toString())
    }
    if (logDB.exists(name) || logDBArchived.exists(name)) {
        return@call call.respond(HttpStatusCode.Conflict, "file already exists")
    }
    val file = call.receive<String>()
    val checksum = hashLogFile(file)
    if (checksum != parsedChecksum) {
        return@call call.respond(HttpStatusCode.BadRequest, "hash does not match")
    }
    ApiServer.api.log.submitLog(name, file)
    call.respond(HttpStatusCode.Created)
}

fun Routing.api() {
    route("/api") {
        install(SignatureCheckPlugin)
        
        get("/") {
            call.respondText("Welcome to Captive Portal Auto Login API")
        }
        route("/har") {
            put("/{name}", harPutHandler())
        }
        route("/log") {
            put("/{name}", logPutHandler())
        }
        route("/liberator") {
            put<Api.Liberator.Error>("error") { it: Api.Liberator.Error ->
                if (!enforceFeedbackLimits(call, it, it.version, it.timestamp, it.solver)) return@put
                ApiServer.api.liberator.reportError(it)
                call.respondStatus(HttpStatusCode.Created)
            }
            put<Api.Liberator.Success>("success") { it: Api.Liberator.Success ->
                if (!enforceFeedbackLimits(call, it, it.version, it.timestamp, it.solver)) return@put
                ApiServer.api.liberator.reportSuccess(it)
                call.respondStatus(HttpStatusCode.Created)
            }
        }
        get("/ssid") {
            call.respond(
                ApiServer.api.getSSIDs(
                    limit = call.queryParameters["limit"]?.toInt(),
                    maximumMajorVersion = call.queryParameters["maximumMajorVersion"]?.toInt(),
                    since = call.queryParameters["since"]?.let { Instant.parse(it) },
                    minimumSuccesses = call.queryParameters["minimumSuccesses"]?.toInt(),
                    minimumBayesianRating = call.queryParameters["minimumBayesianRating"]?.toFloat(),
                    bayesianWeight = call.queryParameters["bayesianWeight"]?.toInt(),
                )
            )
        }
        post("/checkUpdate") {
            val form = call.receiveParameters()
            val installedVersion = form["installedVersion"]
            val manual = form["manual"]?.toBooleanStrictOrNull()
            if (installedVersion == null || manual == null) {
                call.respondStatus(HttpStatusCode.BadRequest)
                return@post
            }
            val update = ApiServer.api.checkUpdate(installedVersion, manual)
            if (update == null) call.respondStatus(HttpStatusCode.NoContent)
            else call.respond(update)
        }
    }
}
