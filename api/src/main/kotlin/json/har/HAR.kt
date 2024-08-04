package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// http://www.softwareishard.com/blog/har-12-spec/
@Serializable
data class HAR(
    @SerialName("log") var log: Log,
    @SerialName("comment") var comment: String? = null,
)
