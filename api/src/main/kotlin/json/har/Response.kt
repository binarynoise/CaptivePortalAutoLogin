package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param status Response status.
 * @param statusText Response status description.
 * @param httpVersion Response HTTP Version.
 * @param cookies List of cookie objects.
 * @param headers List of header objects.
 * @param content Details about the response body.
 * @param redirectURL Redirection target URL from the Location response header.
 * @param headersSize Total number of bytes from the start of the HTTP response message until (and including) the double CRLF before the body.
 * @param bodySize Size of the response body (length or encoded length, depending on the response Content-Encoding header) in bytes.
 */
@Serializable
data class Response(
    @SerialName("status") var status: Int,
    @SerialName("statusText") var statusText: String,
    @SerialName("httpVersion") var httpVersion: String,
    @SerialName("cookies") var cookies: MutableSet<Cookie>,
    @SerialName("headers") var headers: MutableSet<Header>,
    @SerialName("content") var content: Content,
    @SerialName("redirectURL") var redirectURL: String,
    @SerialName("headersSize") var headersSize: Int,
    @SerialName("bodySize") var bodySize: Int,
)
