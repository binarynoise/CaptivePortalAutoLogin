package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.binarynoise.logger.Logger.log

/**
 * Posted data info
 * @param mimeType Mime type of posted data
 * @param params List of posted parameters
 * @param text Plain text posted data
 */
@Serializable
class PostData(
    /**
     * Mime type of posted data.
     */
    @SerialName("mimeType") var mimeType: String,
    /**
     * List of posted parameters (in case of URL encoded parameters).
     */
    @SerialName("params") var params: List<PostParam>?,
    /**
     * Plain text posted data.
     */
    @SerialName("text") var text: String?,
)
