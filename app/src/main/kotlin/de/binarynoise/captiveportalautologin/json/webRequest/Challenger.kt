package de.binarynoise.captiveportalautologin.json.webRequest

import kotlinx.serialization.json.JsonObject
import de.binarynoise.util.json.getInt
import de.binarynoise.util.json.getString

/**
 * The server requesting authentication.
 */
class Challenger(val host: String, val port: Int) {
    companion object {
        fun fromJson(json: JsonObject): Challenger {
            return Challenger(
                json.getString("host"),
                json.getInt("port"),
            )
        }
    }
}
