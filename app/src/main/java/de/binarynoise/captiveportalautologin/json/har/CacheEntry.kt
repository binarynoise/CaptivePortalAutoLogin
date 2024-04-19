package de.binarynoise.captiveportalautologin.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CacheEntry(
    /**
     * Expiration time of the cache entry.
     */
    @SerialName("expires") var expires: LocalDateTime?,
    /**
     * The last time the cache entry was opened.
     */
    @SerialName("lastAccess") var lastAccess: LocalDateTime,
    /**
     * Etag
     */
    @SerialName("eTag") var eTag: String,
    /**
     * The number of times the cache entry has been opened.
     */
    @SerialName("hitCount") var hitCount: Int,
)
