package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param method Request method (GET, POST, ...).
 * @param url Absolute URL of the request (fragments are not included).
 * @param httpVersion Request HTTP Version.
 * @param cookies List of cookie objects.
 * @param headers List of header objects.
 * @param query List of query parameter objects.
 * @param postData Posted data info.
 * @param bodySize Total number of bytes from the start of the HTTP request message until (and including) the double CRLF before the body.
 * @param headersSize Size of the request body (POST data payload) in bytes.
 */
@Serializable
data class Request(
    @SerialName("method") var method: String,
    @SerialName("url") var url: String,
    @SerialName("httpVersion") var httpVersion: String,
    @SerialName("cookies") var cookies: MutableSet<Cookie>,
    @SerialName("headers") var headers: MutableSet<Header>,
    @SerialName("queryString") var query: MutableList<Query>,
    @SerialName("postData") var postData: PostData?,
    @SerialName("headersSize") var headersSize: Int,
    @SerialName("bodySize") var bodySize: Int,
) {
    val totalSize get() = headersSize + bodySize
}
