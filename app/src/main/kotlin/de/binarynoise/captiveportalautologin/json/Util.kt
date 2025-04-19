package de.binarynoise.captiveportalautologin.json

import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

val serializer = Json {
    encodeDefaults = false
    explicitNulls = false
}

fun String.looksLikeBinaryData(threshold: Double): Boolean {
    val totalChars = length
    var nonAsciiChars = 0
    
    for (char in this) {
        if (char.code !in 0..127) {
            nonAsciiChars++
        }
    }
    
    return (nonAsciiChars.toDouble() / totalChars) > threshold
}

fun JSONObject.toMap(): Map<String, Any> {
    val result = LinkedHashMap<String, Any>(this.length())
    for (key in this.keys()) {
        result[key] = this.get(key)
    }
    return result
}

fun JSONArray.toList(): List<Any> {
    val result = ArrayList<Any>(this.length())
    for (i in 0 until this.length()) {
        result.add(this.get(i))
    }
    return result
}
