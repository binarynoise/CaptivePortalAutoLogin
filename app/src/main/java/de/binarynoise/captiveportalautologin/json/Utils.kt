@file:Suppress("unused", "MemberVisibilityCanBePrivate", "RedundantSuppression")

package de.binarynoise.captiveportalautologin.json

import org.json.JSONArray
import org.json.JSONObject

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
