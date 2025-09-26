package de.binarynoise.captiveportalautologin.server.routes

import java.time.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toJavaZoneId
import io.ktor.server.routing.*


@ExperimentalTime
internal fun LocalDate.toInstant() = this.atStartOfDay(UTC.toJavaZoneId()).toInstant().toKotlinInstant()

internal fun missingParameter(name: String): Nothing {
    throw IllegalArgumentException("parameter '$name' not set")
}

internal fun RoutingNode.toLogString(): String {
    val parentLogString = parent?.toLogString() ?: ""
    return when (val routeSelector = selector) {
        is HttpMethodRouteSelector -> "${routeSelector.method} $parentLogString"
        is TrailingSlashRouteSelector -> "$parentLogString|/"
        else -> "$parentLogString|$routeSelector"
    }
}
