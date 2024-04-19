package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Browser(
    @SerialName("name") var name: String,
    @SerialName("version") var version: String,
)
