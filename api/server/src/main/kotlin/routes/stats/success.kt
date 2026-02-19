package de.binarynoise.captiveportalautologin.server.routes.stats

import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.successRoutes() {
    get("successes") {
        call.response.header("Location", "successes/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    get("successes/") {
        val successes = ApiServer.api.database.successDao()
            .getAllSuccesses()
            .map {
                val domain = if (it.url.isNotEmpty()) URLBuilder(urlString = it.url).host else ""
                val key = mutableMapOf<String, Comparable<*>>(
                    "year" to it.year,
                    "month" to it.month,
                    "version" to it.version,
                    "domain" to domain,
                )
                key to it.count
            }
            .groupingBy { it.first }
            .fold(0) { sum, (_, count) -> sum + count }
            .map { (key, sum) ->
                key.apply {
                    put("count", sum)
                    put("majorVersion", (this["version"] as String).takeWhile { it.isDigit() })
                }
            }
            .sortedWith(compareByDescending<MutableMap<String, Comparable<*>>> { it["year"] as Int }.thenByDescending { it["month"] as Int }
                .thenByDescending { it["count"] as Int }
                .thenBy { it["domain"] as String })
        
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
        
        val entries = ApiServer.api.database.successDao().getSuccessDetails(
            year,
            month,
            version,
            domain,
        ).map {
            mapOf(
                "ssid" to it.ssid,
                "url" to it.url,
                "count" to it.count,
            )
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
