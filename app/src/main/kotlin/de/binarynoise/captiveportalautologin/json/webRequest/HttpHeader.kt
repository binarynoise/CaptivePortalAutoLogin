package de.binarynoise.captiveportalautologin.json.webRequest

import kotlinx.serialization.json.JsonObject
import de.binarynoise.util.json.getOptJsonArray
import de.binarynoise.util.json.getOptString
import de.binarynoise.util.json.getString
import de.binarynoise.util.json.toListDeep

/**
 * @param name Name of the HTTP header.
 * @param value Value of the HTTP header if it can be represented by UTF-8.
 * @param binaryValue Value of the HTTP header if it cannot be represented by UTF-8, stored as
individual byte values (0..255).
 */
class HttpHeader(
    val name: String,
    val value: String? = null,
    val binaryValue: Array<UByte>? = null,
) {
    companion object {
        fun fromJson(json: JsonObject): HttpHeader {
            return HttpHeader(
                json.getString("name"),
                json.getOptString("value"),
                json.getOptJsonArray("binaryValue")?.toListDeep()?.map { it as UByte }?.toTypedArray(),
            )
        }
    }
}
