package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Cache(
    /**
     * State of a cache entry before the request. Leave out this field if the information is not available.
     */
    @SerialName("afterRequest") var afterRequest: CacheEntry?,
    /**
     * State of a cache entry after the request. Leave out this field if the information is not available.
     */
    @SerialName("beforeRequest") var beforeRequest: CacheEntry?,
) {
    constructor() : this(null, null)
}
