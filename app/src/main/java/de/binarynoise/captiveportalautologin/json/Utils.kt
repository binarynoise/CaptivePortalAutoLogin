@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package de.binarynoise.captiveportalautologin.json

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.toMap(): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    for (key in this.keys()) {
        result[key] = this.get(key)
    }
    return result
}

fun JSONArray.toList(): List<Any> {
    val result = mutableListOf<Any>()
    for (i in 0 until this.length()) {
        result.add(this.get(i))
    }
    return result
}
