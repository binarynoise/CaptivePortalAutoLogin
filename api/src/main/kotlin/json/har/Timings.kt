package de.binarynoise.captiveportalautologin.api.json.har

import kotlin.math.max
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param blocked Time spent in a queue waiting for a network connection. Use 0 if the timing does not apply to the current request.
 * @param dns DNS resolution time. The time required to resolve a host name. Use 0 if the timing does not apply to the current request.
 * @param connect Time required to create TCP connection. Use 0 if the timing does not apply to the current request.
 * @param send Time required to send HTTP request to the server.
 * @param wait Waiting for a response from the server.
 * @param receive Time required to receive HTTP response from the server (or cache).
 * @param ssl Time required for SSL/TLS negotiation. If this field is defined, then the time is also included in the connect field (to ensure backward compatibility with HAR 1.1).
 */
@Serializable
data class Timings(
    @SerialName("blocked") var blocked: Int?,
    @SerialName("dns") var dns: Int?,
    @SerialName("connect") var connect: Int?,
    @SerialName("send") var send: Int,
    @SerialName("wait") var wait: Int,
    @SerialName("receive") var receive: Int,
    @SerialName("ssl") var ssl: Int?,
) {
    val time =
        max(blocked ?: 0, 0) + max(dns ?: 0, 0) + max(connect ?: 0, 0) + max(send, 0) + max(wait, 0) + max(receive, 0)
    
    constructor() : this(null, null, null, 0, 0, 0, null)
}
