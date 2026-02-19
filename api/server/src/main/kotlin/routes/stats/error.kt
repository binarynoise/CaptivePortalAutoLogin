@file:OptIn(ExperimentalTime::class)

package de.binarynoise.captiveportalautologin.server.routes.stats

import java.time.LocalDate
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.toInstant
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


internal enum class ErrorType {
    All, Unknown, NoNoise
}

internal fun Route.errorRoutes() {
    get("errors") {
        call.response.header("Location", "errors/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("errors/") {
        get {
            val type = ErrorType.valueOf(call.parameters["type"] ?: ErrorType.All.name)
            val unlimited = call.parameters["unlimited"] == "true"
            val limit = if (unlimited) Int.MAX_VALUE else 1000
            
            val errors = when (type) {
                ErrorType.All -> ApiServer.api.database.errorDao().getAllErrors(limit)
                ErrorType.Unknown -> ApiServer.api.database.errorDao().getUnknownPortalErrors(limit)
                ErrorType.NoNoise -> ApiServer.api.database.errorDao().getNoNoiseErrors(limit)
            }.map { error ->
                val dateTime = error.timestamp.toLocalDateTime(UTC)
                val url = error.url
                
                mutableMapOf<String, Comparable<*>>(
                    "version" to error.version,
                    "year" to dateTime.year,
                    "month" to dateTime.month.number,
                    "ssid" to error.ssid,
                    "message" to error.message,
                    "domain" to if (url.isNotEmpty()) URLBuilder(urlString = url).host else "",
                )
            }
                .groupingBy { it }
                .eachCount()
                .map { (key, count) ->
                    key.apply {
                        put("count", count)
                        put("majorVersion", (this["version"] as String).substringBefore('+').substringBefore('-'))
                    }
                }
                .sortedWith(compareByDescending<MutableMap<String, Comparable<*>>> { it["year"] as Int }.thenByDescending { it["month"] as Int }
                    .thenByDescending { it["count"] as Int }
                    .thenBy { it["domain"] as String })
            
            call.respond(
                MustacheContent(
                    "errors.mustache", mapOf(
                        "title" to "Errors: ${
                            when (type) {
                                ErrorType.All -> "All"
                                ErrorType.Unknown -> "Unknown Portals"
                                ErrorType.NoNoise -> "No Noise"
                            }
                        }",
                        "backLink" to "../",
                        "errors" to errors,
                    )
                )
            )
        }
        
        get("details") {
            val year: Int = call.request.queryParameters["year"]?.toIntOrNull() ?: missingParameter("year")
            val month: Int = call.request.queryParameters["month"]?.toIntOrNull() ?: missingParameter("month")
            val version = call.request.queryParameters["version"] ?: missingParameter("version")
            val domain = call.request.queryParameters["domain"] ?: missingParameter("domain")
            val message = call.request.queryParameters["message"] ?: missingParameter("message")
            
            val monthStart = LocalDate.of(year, month, 1)
            val monthEnd = monthStart.plusMonths(1)
            
            val errors = ApiServer.api.database.errorDao()
                .getErrorDetails(monthStart.toInstant(), monthEnd.toInstant(), version, message, domain)
                .map {
                    mapOf(
                        "url" to it.url,
                        "ssid" to it.ssid,
                        "timestamp" to it.timestamp.toLocalDateTime(UTC),
                    )
                }
            
            
            call.respond(
                MustacheContent(
                    "errors-details.mustache", mapOf(
                        "title" to "Errors - $domain - $message",
                        "backLink" to "./",
                        "version" to version,
                        "errors" to errors,
                    )
                )
            )
        }
    }
}
