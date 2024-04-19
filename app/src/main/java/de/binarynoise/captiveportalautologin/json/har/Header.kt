package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.binarynoise.captiveportalautologin.json.webRequest.HttpHeader

@Serializable
data class Header(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String,
) : Comparable<Header> {
    constructor(httpHeader: HttpHeader) : this(httpHeader.name, httpHeader.value ?: "")
    
    override fun compareTo(other: Header): Int {
        return name.compareTo(other.name).takeIf { it != 0 } ?: value.compareTo(other.value)
    }
}
