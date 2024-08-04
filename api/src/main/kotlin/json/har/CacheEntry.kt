package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param expires Expiration time of the cache entry.
 * @param lastAccess The last time the cache entry was opened.
 * @param eTag Etag
 * @param hitCount The number of times the cache entry has been opened.
 */
@Serializable
data class CacheEntry(
    @SerialName("expires") var expires: LocalDateTime?,
    @SerialName("lastAccess") var lastAccess: LocalDateTime,
    @SerialName("eTag") var eTag: String,
    @SerialName("hitCount") var hitCount: Int,
)
