package de.binarynoise.util.okhttp

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl


/**
 * Decodes the path of the HttpUrl.
 *
 * @return the decoded path of the HttpUrl
 */
val HttpUrl.decodedPath: String
    get() = URLDecoder.decode(encodedPath, "UTF-8")

/**
 * Returns the first path segment of the HttpUrl.
 *
 * @return the first path segment of the HttpUrl, or null if the path is empty
 */
val HttpUrl.firstPathSegment
    get() = pathSegments.firstOrNull()

/**
 * Returns the last path segment of the HttpUrl.
 *
 * @return the last path segment of the HttpUrl, or null if the path is empty
 */
val HttpUrl.lastPathSegment
    get() = pathSegments.lastOrNull()

/**
 * Tests whether the given [HttpUrl] has an IP address as host.
 * Both IPv4 and IPv6 are supported.
 */
val HttpUrl.isIp: Boolean
    get() = this.host.matches("^\\d{1,3}(\\.\\d{1,3}){0,3}$|^\\[?([a-f0-9:]{1,4}:+){1,7}[a-f0-9]{0,4}]?$".toRegex())

/**
 * Check whether the given [HttpUrl] has one or more query parameters named [name]
 */
fun HttpUrl.hasQueryParameter(name: String): Boolean {
    return this.queryParameterValues(name).isNotEmpty()
}

/**
 * Returns a new [HttpUrl] representing this.
 *
 * @throws IllegalArgumentException If this is not a well-formed HTTP or HTTPS URL.
 * @param base [HttpUrl] base to resolve relative paths to
 */
fun String.toHttpUrl(base: HttpUrl?): HttpUrl {
    return base?.newBuilder(this)?.build() ?: this.toHttpUrl()
}

/**
 * Returns a new `HttpUrl` representing `url` if it is a well-formed HTTP or HTTPS URL, or null
 * if it isn't.
 * @param base [HttpUrl] base to resolve relative paths to
 */
fun String.toHttpUrlOrNull(base: HttpUrl?): HttpUrl? {
    return try {
        this.toHttpUrl(base)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Returns a new [HttpUrl] object with the [scheme] set to `https`.
 */
fun HttpUrl.enforceHttps(): HttpUrl {
    return this.newBuilder().scheme("https").build()
}

/**
 * return a new [HttpUrl] with the [HttpUrl.encodedPath] set to "`/`"
 */
val HttpUrl.origin: HttpUrl
    get() = this.newBuilder().encodedPath("/").build()

/**
 * returns this [HttpUrl] [origin] as a [String] that can be used in the `Origin` header
 */
val HttpUrl.originHeader: String
    get() = this.origin.toString().removeSuffix("/")

/**
 * construct a new [HttpUrl] where the common [encodedPath] of [base] is removed from [this] [HttpUrl]
 * 
 * the resulting [HttpUrl] should only be used for path comparisons, and not for further requests
 * 
 * @throws IllegalArgumentException if the origins do not match
 * @throws IllegalArgumentException if [this] is not based on [base]
 */
fun HttpUrl.relativeTo(base: HttpUrl): HttpUrl {
    require(this.origin == base.origin) { "origin does not match" }
    require(this.encodedPath.startsWith(base.encodedPath)) { "$this is not based on $base" }
    return this.newBuilder().encodedPath(this.encodedPath.removePrefix(base.encodedPath)).build()
}

fun HttpUrl.queryEntries(): Map<String, String> {
    return (0 until this.querySize).associate {
        this.queryParameterName(it) to (this.queryParameterValue(it) ?: "")
    }
}
