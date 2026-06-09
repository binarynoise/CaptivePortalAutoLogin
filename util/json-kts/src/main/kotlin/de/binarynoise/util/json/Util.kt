package de.binarynoise.util.json

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

val serializer = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    isLenient = true
}
val prettyPrinter = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
}

fun JsonObject(json: String) = serializer.parseToJsonElement(json).jsonObject
fun JsonObject(map: Map<String, Any>) = map.toJsonElement().jsonObject
fun JsonObject() = JsonObject("{}")
fun JsonArray(json: String) = serializer.parseToJsonElement(json).jsonArray
fun JsonArray(array: Array<Any>) = array.toJsonElement().jsonArray
fun JsonArray() = JsonArray("[]")
fun JsonElement(map: Map<String, Any>) = map.toJsonElement()

fun JsonObject.getString(key: String): String = this.getValue(key).jsonPrimitive.content
fun JsonObject.getOptString(key: String): String? = if (key !in this) null else this.getValue(key).jsonPrimitive.content
fun JsonObject.getInt(key: String): Int = this.getValue(key).jsonPrimitive.run { doubleOrNull?.roundToInt() ?: int }
fun JsonObject.getLong(key: String): Long = this.getValue(key).jsonPrimitive.run { doubleOrNull?.roundToLong() ?: long }
fun JsonObject.getFloat(key: String): Float = this.getValue(key).jsonPrimitive.float
fun JsonObject.getDouble(key: String): Double = this.getValue(key).jsonPrimitive.double
fun JsonObject.getBoolean(key: String): Boolean = this.getValue(key).jsonPrimitive.boolean
fun JsonObject.getJsonObject(key: String): JsonObject = this.getValue(key).jsonObject
fun JsonObject.getJsonArray(key: String): JsonArray = this.getValue(key).jsonArray

fun JsonObject.has(key: String): Boolean = containsKey(key)

fun JsonObject.getOptJsonObject(key: String): JsonObject? =
    if (key !in this || this[key] is JsonNull) null else this.getValue(key).jsonObject

fun JsonObject.getOptJsonArray(key: String): JsonArray? = if (key !in this) null else this.getValue(key).jsonArray

fun JsonArray.getJsonObject(index: Int): JsonObject = get(index).jsonObject
fun JsonArray.getString(index: Int): String = get(index).jsonPrimitive.content

fun JsonObject.toMapDeep(): Map<String, Any?> {
    return this.mapValues { (_, value) -> value.toAny() }
}

fun JsonArray.toListDeep(): List<Any?> {
    return this.map { it.toAny() }
}

fun JsonElement.toAny(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> {
        if (isString) content
        else booleanOrNull ?: content.toIntOrNull() ?: content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
    }
    is JsonObject -> toMapDeep()
    is JsonArray -> toListDeep()
}

// adapted from https://github.com/Kotlin/kotlinx.serialization/issues/746#issuecomment-863099397
private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    is Map<*, *> -> JsonObject(this.map { it.key.toString() to it.value.toJsonElement() }.toMap())
    else -> error("${this::class.java.name} is not encodable to JSON")
}
