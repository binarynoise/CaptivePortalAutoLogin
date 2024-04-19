package de.binarynoise.captiveportalautologin.json.webRequest

import org.json.JSONObject

/**
 * The server requesting authentication.
 */
class Challenger(val host: String, val port: Int) {
    companion object {
        fun fromJson(json: JSONObject): Challenger {
            return Challenger(
                json.getString("host"),
                json.getInt("port"),
            )
        }
    }
}
