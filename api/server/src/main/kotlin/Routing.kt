package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ErrorType.All
import de.binarynoise.captiveportalautologin.server.ErrorType.NoNoise
import de.binarynoise.captiveportalautologin.server.ErrorType.Unknown
import de.binarynoise.captiveportalautologin.server.Routing.api
import de.binarynoise.logger.Logger.log
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate as JavaLocalDate

object Routing {
    lateinit var api: ApiServer
}

fun Application.configureRouting() {
    val routes = routing {
        route("/api") {
            get("/") {
                call.respondText("Welcome to Captive Portal Auto Login API")
            }
            route("/har") {
                put("/{name}") {
                    val name = call.parameters["name"] ?: missingParameter("name")
                    val har = call.receive<HAR>()
                    api.har.submitHar(name, har)
                    call.respond(HttpStatusCode.Created)
                }
            }
            route("/liberator") {
                put<Api.Liberator.Error>("error") {
                    api.liberator.reportError(it)
                    call.respond(HttpStatusCode.Created)
                }
                put<Api.Liberator.Success>("success") {
                    api.liberator.reportSuccess(it)
                    call.respond(HttpStatusCode.Created)
                }
            }
        }
        
        get("/stats") { call.response.header("Location", "/stats/"); call.respond(HttpStatusCode.MovedPermanently) }
        route("/stats/", Route::stats)
    }
    
    routes.getAllRoutes().forEach { route ->
        log(route.toLogString())
    }
}

@OptIn(ExperimentalTime::class)
private fun Route.stats() {
    get {
        call.respond(
            MustacheContent(
                "home.mustache", mapOf("title" to "Stats")
            )
        )
    }
    
    get("successes") {
        call.response.header(
            "Location", "successes/"
        ); call.respond(HttpStatusCode.MovedPermanently)
    }
    get("successes/") {
        val successes = transaction {
            Tables.Successes.selectAll().orderBy(
                Tables.Successes.year to SortOrder.DESC,
                Tables.Successes.month to SortOrder.DESC,
                Tables.Successes.count to SortOrder.DESC,
            ).limit(50).map {
                mapOf(
                    "year" to it[Tables.Successes.year],
                    "month" to it[Tables.Successes.month],
                    "version" to it[Tables.Successes.version],
                    "ssid" to it[Tables.Successes.ssid],
                    "url" to it[Tables.Successes.url],
                    "count" to it[Tables.Successes.count]
                )
            }.toList()
        }
        
        call.respond(
            MustacheContent(
                "successes.mustache", mapOf(
                    "title" to "Successes",
                    "backLink" to "../",
                    "successes" to successes,
                )
            )
        )
    }
    
    get("errors") {
        call.response.header(
            "Location", "errors/"
        ); call.respond(HttpStatusCode.MovedPermanently)
    }
    route("errors/") {
        get {
            val type = ErrorType.valueOf(call.parameters["type"] ?: All.name)
            val unlimited = call.parameters["unlimited"] == "true"
            
            val errors = transaction {
                Tables.Errors.selectAll().orderBy(Tables.Errors.timestamp to SortOrder.DESC).let { query ->
                    when (type) {
                        All -> query
                        Unknown -> query.where { Tables.Errors.message like "unknown portal" }
                        NoNoise -> {
                            query.where {
                                not(
                                    (Tables.Errors.message like "unknown portal") //
                                        or (Tables.Errors.message like "connection closed") //
                                        or (Tables.Errors.message like "Failed to connect to %") // 
                                        or (Tables.Errors.message like "Unable to resolve host %") //
                                        or (Tables.Errors.message like "Software caused connection abort")
                                )
                            }
                        }
                    }.let { query ->
                        if (unlimited) query
                        else query.limit(100)
                    }
                }.map {
                    val dateTime = it[Tables.Errors.timestamp].toLocalDateTime(UTC)
                    val url = it[Tables.Errors.url]
                    
                    mutableMapOf<String, Comparable<*>>(
                        "version" to it[Tables.Errors.version],
                        "year" to dateTime.year,
                        "month" to dateTime.month.number,
                        "ssid" to it[Tables.Errors.ssid],
                        "message" to it[Tables.Errors.message],
                        "domain" to if (url.isNotEmpty()) URLBuilder(urlString = url).host else "",
                    )
                }
            }
                .groupingBy { it }
                .eachCount()
                .map { (key, count) ->
                    key.apply {
                        put("count", count)
                    }
                }
                .sortedWith(compareByDescending<MutableMap<String, Comparable<*>>> { it["year"] as Int }
                    .thenByDescending { it["month"] as Int }
                    .thenByDescending { it["count"] as Int }
                    .thenBy { it["domain"] as String })
            
            call.respond(
                MustacheContent(
                    "errors.mustache", mapOf(
                        "title" to "Errors: " + when (type) {
                            All -> "All"
                            Unknown -> "Unknown Portals"
                            NoNoise -> "No Noise"
                        },
                        "backLink" to "../",
                        "errors" to errors,
                    )
                )
            )
        }
        
        // http://localhost:8080/stats/errors/details?year=2025&month=8&version=95-04a0433-20250803&domain=auth.hotsplots.de&message=unknown+portal
        get("details") {
            val year: Int = call.request.queryParameters["year"]?.toIntOrNull() ?: missingParameter("year")
            val month: Int = call.request.queryParameters["month"]?.toIntOrNull() ?: missingParameter("month")
            val version = call.request.queryParameters["version"] ?: missingParameter("version")
            val domain = call.request.queryParameters["domain"] ?: missingParameter("domain")
            val message = call.request.queryParameters["message"] ?: missingParameter("message")
            
            val monthStart: JavaLocalDate = JavaLocalDate.of(year, month, 1)
            val monthEnd: JavaLocalDate = monthStart.plusMonths(1)
            val errors = transaction {
                Tables.Errors.select(Tables.Errors.url, Tables.Errors.ssid).where {
                    (Tables.Errors.timestamp.between(toInstant(monthStart), toInstant(monthEnd)) //
                        and (Tables.Errors.version eq version) //
                        and (Tables.Errors.message eq message) //
                        and (Tables.Errors.url like "%$domain%"))
                }.orderBy(
                    Tables.Errors.timestamp to SortOrder.DESC,
                ).map {
                    mapOf(
                        "url" to it[Tables.Errors.url],
                        "ssid" to it[Tables.Errors.ssid],
                    )
                }
            }
            call.respond(
                MustacheContent(
                    "errors_details.mustache", mapOf(
                        "title" to "Errors - $domain - $message",
                        "backLink" to "./",
                        "errors" to errors,
                    )
                )
            )
            
        }
    }
    
    get("har") {
        call.response.header(
            "Location", "har/"
        ); call.respond<HttpStatusCode>(HttpStatusCode.MovedPermanently)
    }
    route("har/") {
        get {
            val harEntries = api.jsonDb.listAll<HAR>("har")
            
            call.respond<MustacheContent>(
                MustacheContent(
                    "hars.mustache", mapOf(
                        "title" to "HAR Files",
                        "backLink" to "../",
                        "hasEntries" to harEntries.isNotEmpty(),
                        "harEntries" to harEntries,
                    )
                )
            )
        }
        
        get("download/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            
            val path = api.jsonDb.base<HAR>().resolve(id)
            if (!path.exists()) {
                log("file not found: $path")
                call.respond<HttpStatusCode>(HttpStatusCode.NotFound)
                return@get
            }
            call.respondPath(path)
        }
        
        get("delete/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            call.respond<MustacheContent>(
                MustacheContent(
                    "delete_confirm.mustache", mapOf(
                        "title" to "Delete HAR File",
                        "id" to id,
                    )
                )
            )
        }
        
        post("delete/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            val path = api.jsonDb.base<HAR>().resolve(id)
            path.deleteIfExists()
            
            call.response.header("Location", "../")
            call.respond<HttpStatusCode>(HttpStatusCode.SeeOther)
        }
    }
}

private enum class ErrorType {
    All, Unknown, NoNoise
}

@ExperimentalTime
private fun toInstant(monthStart: JavaLocalDate): Instant =
    monthStart.atStartOfDay(UTC.toJavaZoneId()).toInstant().toKotlinInstant()

private fun missingParameter(name: String): Nothing {
    throw IllegalArgumentException("parameter '$name' not set")
}


private fun RoutingNode.toLogString(): String {
    val parentLogString = parent?.toLogString() ?: ""
    val routeSelector = selector
    return when (routeSelector) {
        is HttpMethodRouteSelector -> routeSelector.method.toString() + " " + parentLogString
        is TrailingSlashRouteSelector -> parentLogString + "|" + "/"
        else -> parentLogString + "|" + routeSelector.toString()
    }
}
