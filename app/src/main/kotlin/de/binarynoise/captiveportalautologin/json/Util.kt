package de.binarynoise.captiveportalautologin.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject as OrgJSONObject
import org.json.JSONArray as OrgJSONArray

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

fun OrgJSONObject.toJsonObject(): JsonObject {
    val original = this
    return buildJsonObject {
        original.keys().forEach { key ->
            put(key, convertJsonElement(original.get(key)))
        }
    }
}

fun OrgJSONArray.toJsonArray(): JsonArray {
    val original = this
    val result = buildList {
        for (i in 0 until original.length()) {
            this.add(convertJsonElement(original.get(i)))
        }
    }
    return JsonArray(result)
}

fun convertJsonElement(value: Any?): JsonElement = when (value) {
    null, OrgJSONObject.NULL -> JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is OrgJSONObject -> value.toJsonObject()
    is OrgJSONArray -> value.toJsonArray()
    else -> JsonUnquotedLiteral(value.toString())
}
