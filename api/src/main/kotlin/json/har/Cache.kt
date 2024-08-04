package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param afterRequest State of a cache entry before the request. Leave out this field if the information is not available.
 * @param beforeRequest State of a cache entry after the request. Leave out this field if the information is not available.
 */
@Serializable
data class Cache(
    @SerialName("afterRequest") var afterRequest: CacheEntry?,
    @SerialName("beforeRequest") var beforeRequest: CacheEntry?,
) {
    constructor() : this(null, null)
}
