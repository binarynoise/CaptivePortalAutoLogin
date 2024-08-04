package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Header(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String,
) : Comparable<Header> {
    override fun compareTo(other: Header): Int {
        return name.compareTo(other.name).takeIf { it != 0 } ?: value.compareTo(other.value)
    }
}
