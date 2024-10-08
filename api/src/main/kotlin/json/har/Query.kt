package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Query(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String,
)
