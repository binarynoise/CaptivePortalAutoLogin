@file:OptIn(ExperimentalTime::class)

package de.binarynoise.captiveportalautologin.server.routes.stats

import java.time.LocalDate
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.server.Tables
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.toInstant
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


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
            
            val errors = transaction {
                Tables.Errors.selectAll().orderBy(Tables.Errors.timestamp to SortOrder.DESC).let { query ->
                    when (type) {
                        ErrorType.All -> query
                        ErrorType.Unknown -> query.where { Tables.Errors.message like "unknown portal" }
                        ErrorType.NoNoise -> {
                            query.where {
                                not(
                                    (Tables.Errors.message like "unknown portal") or //
                                        (Tables.Errors.message like "connection closed") or //
                                        (Tables.Errors.message like "Failed to connect to %") or //
                                        (Tables.Errors.message like "Unable to resolve host %") or //
                                        (Tables.Errors.message like "Software caused connection abort") or //
                                        (Tables.Errors.message like "Binding socket to network % failed: %") or //
                                        (Op.TRUE)
                                )
                            }
                        }
                    }.let { query ->
                        if (unlimited) query else query.limit(1000)
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
            }.groupingBy { it }
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
            
            val errors = transaction {
                Tables.Errors.selectAll().where {
                    (Tables.Errors.timestamp.between(monthStart.toInstant(), monthEnd.toInstant()) //
                        and (Tables.Errors.version eq version) // 
                        and (Tables.Errors.message eq message)  // 
                        and (Tables.Errors.url like "%$domain%"))
                }.orderBy(
                    Tables.Errors.timestamp to SortOrder.DESC,
                ).map {
                    mapOf(
                        "url" to it[Tables.Errors.url],
                        "ssid" to it[Tables.Errors.ssid],
                        "timestamp" to it[Tables.Errors.timestamp].toLocalDateTime(UTC),
                    )
                }
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
