package de.binarynoise.captiveportalautologin.json.webRequest

import de.binarynoise.captiveportalautologin.json.toList
import org.json.JSONObject

/**
 * Contains data uploaded in a URL request.
 * @param bytes An ArrayBuffer with a copy of the data.
 * @param file A string with the file's path and name.
 */
class UploadData(
    val bytes: ByteArray? = null,
    val file: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): UploadData {
            return UploadData(
                json.optJSONArray("bytes")?.toList()?.map { (it as Number).toByte() }?.toByteArray(),
                json.optString("file"),
            )
        }
    }
}
