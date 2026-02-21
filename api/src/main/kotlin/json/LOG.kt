package de.binarynoise.captiveportalautologin.api.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 */
@Serializable
data class LOG(
    @SerialName("name") var name: String,
    @SerialName("timestamp") var timestamp: String,
    @SerialName("version") var version: String,
    @SerialName("content") var content: String,
)
