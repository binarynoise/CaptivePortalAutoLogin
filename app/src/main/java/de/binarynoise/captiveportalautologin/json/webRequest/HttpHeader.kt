package de.binarynoise.captiveportalautologin.json.webRequest

import de.binarynoise.captiveportalautologin.json.toList
import org.json.JSONObject

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
        fun fromJson(json: JSONObject): HttpHeader {
            return HttpHeader(
                json.getString("name"),
                json.optString("value"),
                json.optJSONArray("binaryValue")?.toList()?.map { it as UByte }?.toTypedArray(),
            )
        }
    }
}
