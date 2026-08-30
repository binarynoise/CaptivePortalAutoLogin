package de.binarynoise.captiveportalautologin.api.json.har

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// http://www.softwareishard.com/blog/har-12-spec/
@Serializable
data class HAR(
    @SerialName("log") var log: Log,
    @SerialName("comment") var comment: String? = null,
)

/**
 * Generates a filename for a HAR file based on the SSID, domain, and timestamp.
 * 
 * @param ssid The SSID of the network
 * @param domain The domain of the website
 * @param timestamp The timestamp of the HAR file in ISO 8601 format
 * @return The filename of the HAR file
 */
fun generateHarFileName(ssid: String, domain: String, timestamp: Instant): String = "$ssid $domain $timestamp"

val harFileNameRegex =
    """^(?:(?<ssid>.+) )?(?<domain>\S+) (?<timestamp>[\d-]+T[\d:]+(?:\.\d+)?Z(?:[\d+:.-]+)?)$""".toRegex()

/**
 * Parses a HAR filename and returns the SSID, domain, and timestamp (in ISO 8601 format).
 * 
 * @param name The filename of the HAR file
 * @return A triple containing the SSID, domain, and timestamp, or null if the filename is invalid
 */
fun parseHarFileName(name: String): Triple<String, String, String>? {
    val match = harFileNameRegex.matchEntire(name.trim()) ?: return null
    
    val ssid = match.groups["ssid"]?.value.orEmpty()
    val domain = match.groups["domain"]?.value.orEmpty()
    val timestamp = match.groups["timestamp"]?.value.orEmpty()
    
    return Triple(ssid, domain, timestamp)
}
