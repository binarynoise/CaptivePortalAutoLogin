package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Posted data info
 * @param mimeType Mime type of posted data
 * @param params List of posted parameters
 * @param text Plain text posted data
 */
@Serializable
data class PostData(
    @SerialName("mimeType") var mimeType: String,
    @SerialName("params") var params: List<PostParam>?,
    @SerialName("text") var text: String?,
)
