package de.binarynoise.captiveportalautologin.json.filter

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import de.binarynoise.util.json.getString

class FilterOnStopDetails(
    @SerialName("requestId") var requestId: String,
    @SerialName("content") var content: String,
) {
    companion object {
        fun fromJson(json: JsonObject): FilterOnStopDetails {
            return FilterOnStopDetails(
                json.getString("requestId"),
                json.getString("content"),
            )
        }
    }
}
