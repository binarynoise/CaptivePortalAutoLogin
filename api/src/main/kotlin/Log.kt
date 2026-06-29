package de.binarynoise.captiveportalautologin.api

import java.security.MessageDigest
import kotlin.time.Instant

val SHA256Digest: MessageDigest = MessageDigest.getInstance("SHA-256")

fun generateLogFileName(timestamp: Instant, version: String, content: String): String {
    val checksum = SHA256Digest.digest(content.toByteArray(Charsets.UTF_8)).toHexString()
    return "$timestamp $version $checksum"
}

val logFileNameRegex =
    """^(?<timestamp>[\d-]+T[\d:]+(?:\.\d+)?Z(?:[\d+:.-]+)?) (?<version>.+) (?<checksum>[0-9a-fA-F]+)$""".toRegex()

fun parseLogFileName(name: String): Triple<Instant, String, String> {
    val match = logFileNameRegex.matchEntire(name.trim()) ?: error("regex didn't match")
    
    val timestamp = match.groups["timestamp"]?.value?.let { Instant.parse(it) } ?: error("timestamp unparseable")
    val version = match.groups["version"]?.value ?: error("no version")
    val checksum = match.groups["checksum"]?.value ?: error("no checksum")
    
    return Triple(timestamp, version, checksum)
}
