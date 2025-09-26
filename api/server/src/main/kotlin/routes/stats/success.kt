package de.binarynoise.captiveportalautologin.server.routes.stats

import de.binarynoise.captiveportalautologin.server.Tables
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal fun Route.successRoutes() {
    get("successes") {
        call.response.header("Location", "successes/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    get("successes/") {
        val successes = transaction {
            Tables.Successes.selectAll()
                .map {
                    val url = it[Tables.Successes.url]
                    val domain = if (url.isNotEmpty()) URLBuilder(urlString = url).host else ""
                    val key = mutableMapOf<String, Comparable<*>>(
                        "year" to it[Tables.Successes.year],
                        "month" to it[Tables.Successes.month],
                        "version" to it[Tables.Successes.version],
                        "domain" to domain,
                    )
                    key to it[Tables.Successes.count]
                }
                .groupingBy { it.first }
                .fold(0) { acc, e -> acc + e.second }
                .map { (key, sum) ->
                    key.apply {
                        put("count", sum)
                        put("majorVersion", (this["version"] as String).takeWhile { it.isDigit() })
                    }
                }
                .sortedWith(compareByDescending<MutableMap<String, Comparable<*>>> { it["year"] as Int }.thenByDescending { it["month"] as Int }
                    .thenByDescending { it["count"] as Int }
                    .thenBy { it["domain"] as String })
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
    
    get("successes/details") {
        val year: Int = call.request.queryParameters["year"]?.toIntOrNull() ?: missingParameter("year")
        val month: Int = call.request.queryParameters["month"]?.toIntOrNull() ?: missingParameter("month")
        val version = call.request.queryParameters["version"] ?: missingParameter("version")
        val domain = call.request.queryParameters["domain"] ?: missingParameter("domain")
        
        val entries = transaction {
            Tables.Successes.selectAll().where {
                (Tables.Successes.year eq year) and //
                    (Tables.Successes.month eq month) and //
                    (Tables.Successes.version like "%$version%") and // 
                    (Tables.Successes.url like "%$domain%")
            }.orderBy(
                Tables.Successes.count to SortOrder.DESC,
                Tables.Successes.ssid to SortOrder.ASC,
                Tables.Successes.url to SortOrder.ASC,
            ).map {
                mapOf(
                    "ssid" to it[Tables.Successes.ssid],
                    "url" to it[Tables.Successes.url],
                    "count" to it[Tables.Successes.count],
                )
            }
        }
        
        call.respond(
            MustacheContent(
                "successes-details.mustache", mapOf(
                    "title" to "Successes - $domain - $year-$month - $version",
                    "backLink" to "./",
                    "version" to version,
                    "successes" to entries,
                )
            )
        )
    }
}
