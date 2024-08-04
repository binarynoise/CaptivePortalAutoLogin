package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Cookie(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String,
    @SerialName("path") var path: String?,
    @SerialName("domain") var domain: String?,
    @SerialName("expires") var expires: LocalDateTime?,
    @SerialName("httpOnly") var httpOnly: Boolean?,
    @SerialName("secure") var secure: Boolean?,
)
