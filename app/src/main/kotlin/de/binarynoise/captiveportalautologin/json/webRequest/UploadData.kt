package de.binarynoise.captiveportalautologin.json.webRequest

import kotlinx.serialization.json.JsonObject
import de.binarynoise.util.json.getOptJsonArray
import de.binarynoise.util.json.getOptString
import de.binarynoise.util.json.toListDeep

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
        fun fromJson(json: JsonObject): UploadData {
            return UploadData(
                json.getOptJsonArray("bytes")?.toListDeep()?.map { (it as Number).toByte() }?.toByteArray(),
                json.getOptString("file"),
            )
        }
    }
}
