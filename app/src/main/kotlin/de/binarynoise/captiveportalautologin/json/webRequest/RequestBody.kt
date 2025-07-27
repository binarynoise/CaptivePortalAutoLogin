package de.binarynoise.captiveportalautologin.json.webRequest

import de.binarynoise.captiveportalautologin.json.toList
import de.binarynoise.captiveportalautologin.json.toMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contains the HTTP request body data. Only provided if extraInfoSpec contains 'requestBody'.
 * @param error Errors when obtaining request body data.
 * @param formData If the request method is POST and the body is a sequence of key-value pairs
encoded in UTF8, encoded as either multipart/form-data, or
application/x-www-form-urlencoded, this dictionary is present and for each key contains the
list of all values for that key. If the data is of another media type, or if it is
malformed, the dictionary is not present. An example value of this dictionary is {'key':
['value1', 'value2']}.
 * @param raw If the request method is PUT or POST, and the body is not already parsed in formData,
then the unparsed request body elements are contained in this array.
 */
class RequestBody(
    val error: String? = null,
    val formData: Map<String, Array<String>>? = null,
    val raw: Array<UploadData>? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): RequestBody {
            return RequestBody(
                json.optString("error"),
                json.optJSONObject("formData")
                    ?.toMap()
                    ?.mapValues { (_, value) -> (value as JSONArray).toList().map { it as String }.toTypedArray() },
                json.optJSONArray("raw")?.toList()?.map { UploadData.fromJson(it as JSONObject) }?.toTypedArray(),
            )
        }
    }
}
