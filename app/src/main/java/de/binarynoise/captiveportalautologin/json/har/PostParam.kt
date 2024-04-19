package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param name name of a posted parameter.
 * @param value value of a posted parameter or content of the posted file.
 * @param fileName name of the posted file.
 * @param contentType content type of the posted file.
 */
@Serializable
class PostParam(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String?,
    @SerialName("fileName") var fileName: String?,
    @SerialName("contentType") var contentType: String?,
)
