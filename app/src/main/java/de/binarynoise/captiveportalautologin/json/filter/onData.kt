import kotlinx.serialization.SerialName
import org.json.JSONObject

class FilterOnStopDetails(
    @SerialName("requestId") var requestId: String,
    @SerialName("content") var content: String,
) {
    companion object {
        fun fromJson(json: JSONObject): FilterOnStopDetails {
            return FilterOnStopDetails(
                json.getString("requestId"),
                json.getString("content"),
            )
        }
    }
}
