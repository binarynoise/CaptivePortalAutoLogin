package de.binarynoise.captiveportalautologin.server.routes.stats

import de.binarynoise.captiveportalautologin.server.ApiServer
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun Route.successRoutes() {
    get("successes") {
        call.response.header("Location", "successes/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    get("successes/") {
        val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
            ColumnDefinition("version", "Version", Comparators.VersionComparator),
            ColumnDefinition("majorVersion", "Major Version", Comparators.RegularComparator),
            ColumnDefinition("year", "Year", Comparators.RegularComparator),
            ColumnDefinition("month", "Month", Comparators.RegularComparator),
            ColumnDefinition("ssid", "SSID", Comparators.RegularComparator),
            ColumnDefinition("solver", "Solver", Comparators.RegularComparator),
            ColumnDefinition("url", "URL", Comparators.RegularComparator),
            ColumnDefinition("domain", "Domain", Comparators.DomainComparator),
            ColumnDefinition("count", "Count", Comparators.RegularComparator),
        )
        val groupDefault: Set<String> = setOf("year", "month", "majorVersion", "solver")
        
        val preFilterDefinitions: List<PreFilterDefinition> = listOf(
            PreFilterDefinition("all", "All") {
                ApiServer.api.database.successDao()
                    .getAllSuccesses()
                    .toDataFrame()
                    .add("domain") { if (it.url.isNotEmpty()) URLBuilder(urlString = it.url).host else "" }
                    .add("majorVersion") { it.version.split('-', '+').first().toInt() }
            },
        )
        
        val tableData = generateTableData(call, columnDefinitions, groupDefault, preFilterDefinitions)
        
        call.respond(
            MustacheContent(
                "successes.mustache", mapOf(
                    "title" to "Successes",
                    "backLink" to "../",
                ) + tableData.toMap()
            )
        )
    }
}
