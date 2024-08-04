package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param size Length of the returned content in bytes. Should be equal to response.bodySize if there is no compression and bigger when the content has been compressed.
 * @param mimeType MIME type of the response text (value of the Content-Type response header). The charset attribute of the MIME type is included (if available).
 * @param text Response body sent from the server or loaded from the browser cache. This field is populated with textual content only.
 * @param encoding Encoding used for response text field e.g. "base64". Leave out this field if the text field is HTTP decoded (decompressed & unchunked), then trans-coded from its original character set into UTF-8.
 */
@Serializable
data class Content(
    @SerialName("size") var size: Int,
    @SerialName("mimeType") var mimeType: String,
    @SerialName("text") var text: String?,
    @SerialName("encoding") var encoding: String?,
)
