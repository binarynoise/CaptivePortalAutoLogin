package de.binarynoise.captiveportalautologin.server.routes.stats

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.times
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.toDurationExtended
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import de.binarynoise.captiveportalautologin.server.routes.with
import io.ktor.http.HttpStatusCode
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

internal fun Route.usageRoutes() {
    get("usage") {
        call.response.header("Location", "usage/")
        call.respondStatus(HttpStatusCode.MovedPermanently)
    }
    
    route("usage/") {
        get {
            val end = Clock.System.now().toLocalDateTime(UTC).with(second = 0, nanosecond = 0).toInstant(UTC)
            
            val intervalInput = CustomInputDefinition(
                name = "interval",
                displayName = "Interval",
                type = "text",
                default = 7.days,
                unpack = { it.toDurationExtended() },
            )
            val startInput = CustomInputDefinition(
                name = "start",
                displayName = "Start",
                type = "datetime-local",
                default = end - 4 * intervalInput.typedValue,
                unpack = {
                    LocalDateTime.parse(it).toInstant(UTC)
                },
                pack = { instant ->
                    instant.toLocalDateTime(UTC).with(second = 0, nanosecond = 0).toString()
                },
            )
            
            val includeAutomaticInput = CheckboxCustomInputDefinition(
                name = "include_automatic",
                displayName = "Include Automatic",
                default = true,
            )
            val includeManualInput = CheckboxCustomInputDefinition(
                name = "include_manual",
                displayName = "Include Manual",
                default = false,
            )
            val includeDevVersionsInput = CheckboxCustomInputDefinition(
                name = "include_dev",
                displayName = "Include Dev Versions",
                default = false,
            )
            val minimumMajorVersionInput = CustomInputDefinition(
                name = "minimum_major_version",
                displayName = "Min Major Version",
                type = "number",
                default = 0,
                unpack = { it.toInt() },
            )
            val maximumMajorVersionInput = CustomInputDefinition(
                name = "maximum_major_version",
                displayName = "Max Major Version",
                type = "number",
                default = Int.MAX_VALUE,
                unpack = { it.toInt() },
            )
            val minimumEntryCountInput = CustomInputDefinition(
                name = "minimum_entry_count",
                displayName = "Min Entry Count",
                type = "number",
                default = 0,
                unpack = { it.toInt() },
            )
            
            val customInputs: List<CustomInputDefinition<*>> = listOf(
                startInput,
                intervalInput,
                includeAutomaticInput,
                includeManualInput,
                includeDevVersionsInput,
                minimumMajorVersionInput,
                maximumMajorVersionInput,
                minimumEntryCountInput,
            )
            customInputs.forEach { it.applyFrom(call.request.queryParameters) }
            
            val preFilterDefinitions: MutableList<PreFilterDefinition> = mutableListOf(
                PreFilterDefinition(name = "custom", displayName = "Custom") {
                    ApiServer.api.database.usageStatsDao().countActiveInstalls(
                        start = startInput.typedValue,
                        end = end,
                        interval = intervalInput.typedValue,
                        includeAutomatic = includeAutomaticInput.typedValue,
                        includeManual = includeManualInput.typedValue,
                        includeDevVersions = includeDevVersionsInput.typedValue,
                        minimumMajorVersion = minimumMajorVersionInput.typedValue,
                        maximumMajorVersion = maximumMajorVersionInput.typedValue,
                        minimumEntryCount = minimumEntryCountInput.typedValue,
                    ).toDataFrame()
                },
            )
            
            val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
                ColumnDefinition("start", "Start", Comparators.RegularComparator),
                ColumnDefinition("count", "Count", Comparators.RegularComparator),
            )
            
            val tableData = generateTableData(
                call,
                columnDefinitions,
                preFilterDefinitions,
                defaultGroups = emptySet(),
                defaultPreFilter = "custom",
                defaultSort = "start-desc",
                customInputs = customInputs,
            )
            
            call.respond(
                MustacheContent(
                    "usage.mustache", mapOf(
                        "title" to "Usage Stats",
                        "backLink" to "../",
                    ) + tableData.toMap()
                )
            )
        }
    }
}
