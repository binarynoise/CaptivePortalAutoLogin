package de.binarynoise.captiveportalautologin.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Entry(
    /**
     * Reference to the parent page. Leave out this field if the application does not support grouping by pages.
     */
    @SerialName("pageref") var pageRef: String?,
    /**
     * Date and time stamp of the request start (ISO 8601).
     */
    @SerialName("startedDateTime") var startedDateTime: LocalDateTime,
    /**
     * Detailed info about the request.
     */
    @SerialName("request") var request: Request,
    /**
     * Detailed info about the response.
     */
    @SerialName("response") var response: Response,
    /**
     * Info about cache usage.
     */
    @SerialName("cache") var cache: Cache,
    /**
     * Detailed timing info about the request/response round trip.
     */
    @SerialName("timings") var timings: Timings,
    /**
     * IP address of the server that was connected (result of DNS resolution).
     */
    @SerialName("serverIPAddress") var serverIPAddress: String?,
    /**
     * Unique ID of the parent TCP/IP connection.
     * Can be the client port number.
     * Note that a port number doesn't have to be a unique identifier in cases where the port is shared for more connections.
     * If the port isn't available for the application, any other unique connection ID can be used instead (e.g. connection index).
     * Leave out this field if the application doesn't support this info.
     */
    @SerialName("connection") var connection: String?,
) {
    /**
     * Total elapsed time of the request in milliseconds.
     * This is the sum of all timings available in the timings object (i.e. not including 0 values).
     */
    @SerialName("time")
    val time: Int get() = timings.time
}
