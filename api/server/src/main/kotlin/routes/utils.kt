package de.binarynoise.captiveportalautologin.server.routes

import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.name
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toJavaZoneId
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondPath
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.TrailingSlashRouteSelector
import nl.jacobras.humanreadable.HumanReadable


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

class FileSize(val value: Long) : Comparable<FileSize> {
    override fun toString(): String = HumanReadable.fileSize(value, decimals = 1)
    override fun compareTo(other: FileSize): Int = value.compareTo(other.value)
}

suspend fun ApplicationCall.respondPathWithContentDisposition(path: Path, inline: Boolean = false) {
    val contentDisposition = if (inline) {
        ContentDisposition.Inline
    } else {
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name)
    }
    response.header(
        HttpHeaders.ContentDisposition,
        contentDisposition.toString(),
    )
    respondPath(path)
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
