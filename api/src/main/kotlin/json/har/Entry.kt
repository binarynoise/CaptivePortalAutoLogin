package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param pageref Reference to the parent page. Leave out this field if the application does not support grouping by pages.
 * @param startedDateTime Date and time stamp of the request start (ISO 8601).
 * @param request Detailed info about the request.
 * @param response Detailed info about the response.
 * @param cache Info about cache usage.
 * @param timings Detailed timing info about the request/response round trip.
 * @param serverIPAddress IP address of the server that was connected (result of DNS resolution).
 * @param connection
 *        Unique ID of the parent TCP/IP connection.
 *        Can be the client port number.
 *        Note that a port number doesn't have to be a unique identifier in cases where the port is shared for more connections.
 *        If the port isn't available for the application, any other unique connection ID can be used instead (e.g. connection index).
 *        Leave out this field if the application doesn't support this info.
 */
@Serializable
data class Entry(
    @SerialName("pageref") var pageRef: String?,
    @SerialName("startedDateTime") var startedDateTime: LocalDateTime,
    @SerialName("request") var request: Request,
    @SerialName("response") var response: Response,
    @SerialName("cache") var cache: Cache,
    @SerialName("timings") var timings: Timings,
    @SerialName("serverIPAddress") var serverIPAddress: String?,
    @SerialName("connection") var connection: String?,
) {
    /**
     * Total elapsed time of the request in milliseconds.
     * This is the sum of all timings available in the timings object (i.e. not including 0 values).
     */
    @SerialName("time")
    val time: Int by timings::time
}
