package de.binarynoise.captiveportalautologin.server.routes.stats

import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.server.ApiServer
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun Route.errorRoutes() {
    get("errors") {
        call.response.header("Location", "errors/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    get("errors/") {
        val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
            ColumnDefinition("version", "Version", Comparators.VersionComparator),
            ColumnDefinition("majorVersion", "Major Version", Comparators.RegularComparator),
            ColumnDefinition("year", "Year", Comparators.RegularComparator),
            ColumnDefinition("month", "Month", Comparators.RegularComparator),
            ColumnDefinition("timestamp", "Timestamp", Comparators.RegularComparator),
            ColumnDefinition("ssid", "SSID", Comparators.RegularComparator),
            ColumnDefinition("url", "URL", Comparators.RegularComparator),
            ColumnDefinition("domain", "Domain", Comparators.DomainComparator),
            ColumnDefinition("message", "Message", Comparators.RegularComparator),
            ColumnDefinition("solver", "Solver", Comparators.RegularComparator),
            ColumnDefinition("stackTrace", "Stack Trace", Comparators.RegularComparator),
        )
        val groupDefault: Set<String> = setOf("year", "month", "majorVersion", "message")
        
        val preFilterDefinitions: List<PreFilterDefinition> = listOf(
            PreFilterDefinition("all", "All") {
                ApiServer.api.database.errorDao()
                    .getAllErrors(Int.MAX_VALUE)
                    .toDataFrame()
                    .add("domain") { if (it.url.isNotEmpty()) URLBuilder(urlString = it.url).host else "" }
                    .add("majorVersion") { it.version.split('-', '+').first().toInt() }
                    .add("year") { it.timestamp.toLocalDateTime(UTC).year }
                    .add("month") { it.timestamp.toLocalDateTime(UTC).month.number }
            },
            PreFilterDefinition("unknown", "Unknown Portals") {
                ApiServer.api.database.errorDao()
                    .getUnknownPortalErrors(Int.MAX_VALUE)
                    .toDataFrame()
                    .add("domain") { if (it.url.isNotEmpty()) URLBuilder(urlString = it.url).host else "" }
                    .add("majorVersion") { it.version.split('-', '+').first().toInt() }
                    .add("year") { it.timestamp.toLocalDateTime(UTC).year }
                    .add("month") { it.timestamp.toLocalDateTime(UTC).month.number }
            },
            PreFilterDefinition("no_noise", "No Noise") {
                ApiServer.api.database.errorDao()
                    .getNoNoiseErrors(Int.MAX_VALUE)
                    .toDataFrame()
                    .add("domain") { if (it.url.isNotEmpty()) URLBuilder(urlString = it.url).host else "" }
                    .add("majorVersion") { it.version.split('-', '+').first().toInt() }
                    .add("year") { it.timestamp.toLocalDateTime(UTC).year }
                    .add("month") { it.timestamp.toLocalDateTime(UTC).month.number }
            },
        )
        
        val tableData = generateTableData(call, columnDefinitions, groupDefault, preFilterDefinitions)
        
        call.respond(
            MustacheContent(
                "errors.mustache", mapOf(
                    "title" to "Errors",
                    "backLink" to "../",
                ) + tableData.toMap()
            )
        )
    }
}
