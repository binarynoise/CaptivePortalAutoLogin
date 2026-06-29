package de.binarynoise.captiveportalautologin.server.routes.stats

import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun Route.errorRoutes() {
    get("errors") {
        call.response.header("Location", "errors/")
        call.respondStatus(HttpStatusCode.MovedPermanently)
    }
    
    get("errors/") {
        val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
            ColumnDefinition("version", "Version", Comparators.VersionComparator),
            ColumnDefinition("majorVersion", "Major Version", Comparators.RegularComparator),
            ColumnDefinition("year", "Year", Comparators.RegularComparator),
            ColumnDefinition("month", "Month", Comparators.RegularComparator),
            ColumnDefinition("day", "Day", Comparators.RegularComparator),
            ColumnDefinition("timestamp", "Timestamp", Comparators.RegularComparator),
            ColumnDefinition("ssid", "SSID", Comparators.RegularComparator),
            ColumnDefinition("url", "URL", Comparators.RegularComparator),
            ColumnDefinition("domain", "Domain", Comparators.DomainComparator),
            ColumnDefinition("message", "Message", Comparators.RegularComparator),
            ColumnDefinition("solver", "Solver", Comparators.RegularComparator),
            ColumnDefinition("stackTrace", "Stack Trace", Comparators.RegularComparator),
        )
        val defaultGroups: Set<String> = setOf("year", "month", "majorVersion", "message")
        
        val preFilterDefinitions: List<PreFilterDefinition> = listOf(
            PreFilterDefinition("all", "All") {
                ApiServer.api.database.errorDao().getAll().map { it.toExtendedErrorEntity() }.toDataFrame()
            },
            PreFilterDefinition("unknown", "Unknown Portals") {
                ApiServer.api.database.errorDao().getUnknownPortals().map { it.toExtendedErrorEntity() }.toDataFrame()
            },
            PreFilterDefinition("no_noise", "No Noise") {
                ApiServer.api.database.errorDao().getNoNoise().map { it.toExtendedErrorEntity() }.toDataFrame()
            },
        )
        
        val tableData = generateTableData(
            call,
            columnDefinitions,
            preFilterDefinitions,
            defaultGroups = defaultGroups,
            defaultPreFilter = "no_noise",
        )
        
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
