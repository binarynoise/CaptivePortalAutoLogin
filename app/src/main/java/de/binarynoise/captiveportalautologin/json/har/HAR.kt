package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
private val serializer = Json {
    this.encodeDefaults = false
    this.explicitNulls = false
}

// http://www.softwareishard.com/blog/har-12-spec/
@Serializable
class HAR(
    @SerialName("log") var log: Log,
    @SerialName("comment") var comment: String? = null,
) {
    fun toJson(): String {
        return serializer.encodeToString(this)
    }
}
