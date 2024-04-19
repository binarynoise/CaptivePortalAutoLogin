package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Content(
    /**
     * Length of the returned content in bytes. Should be equal to response.bodySize if there is no compression and bigger when the content has been compressed.
     */
    @SerialName("size") var size: Int,
    /**
     * MIME type of the response text (value of the Content-Type response header). The charset attribute of the MIME type is included (if available).
     */
    @SerialName("mimeType") var mimeType: String,
    /**
     * Response body sent from the server or loaded from the browser cache. This field is populated with textual content only.
     * The text field is either HTTP decoded text or an encoded (e.g. "base64") representation of the response body.
     * Leave out this field if the information is not available.
     */
    @SerialName("text") var text: String?,
    /**
     * Encoding used for response text field e.g. "base64".
     * Leave out this field if the text field is HTTP decoded (decompressed & unchunked), then trans-coded from its original character set into UTF-8.
     */
    @SerialName("encoding") var encoding: String?,
)
