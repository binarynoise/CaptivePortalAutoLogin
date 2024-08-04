package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Creator(
    @SerialName("name") var name: String,
    @SerialName("version") var version: String,
)
