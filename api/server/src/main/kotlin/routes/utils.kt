package de.binarynoise.captiveportalautologin.server.routes

import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toJavaZoneId
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.TrailingSlashRouteSelector


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

suspend fun RoutingCall.respondStatus(httpStatusCode: HttpStatusCode) {
    this.respond(httpStatusCode, httpStatusCode.description)
}

fun Instant.isInRelativeRange(
    minus: Duration = Duration.INFINITE,
    plus: Duration = Duration.ZERO,
    base: Instant = Clock.System.now(),
): Boolean {
    return this > base.minus(minus) && this < base.plus(plus)
}

fun String.toDuration(): Duration {
    return Duration.parse(this)
}
