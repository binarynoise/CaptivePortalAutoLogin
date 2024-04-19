package de.binarynoise.captiveportalautologin.json.webRequest

import org.json.JSONObject

/**
 * @param requestId The ID of the request. Request IDs are unique within a browser session. As a
result, they could be used to relate different events of the same request.
 * @param method Standard HTTP method.
 * @param frameId The value 0 indicates that the request happens in the main frame; a positive value
indicates the ID of a subframe in which the request happens. If the document of a
(sub-)frame is loaded (<code>type</code> is <code>main_frame</code> or
<code>sub_frame</code>), <code>frameId</code> indicates the ID of this frame, not the ID of
the outer frame. Frame IDs are unique within a tab.
 * @param parentFrameId ID of frame that wraps the frame which sent the request. Set to -1 if no
parent frame exists.
 * @param originUrl URL of the resource that triggered this request.
 * @param documentUrl URL of the page into which the requested resource will be loaded.
 * @param tabId The ID of the tab in which the request takes place. Set to -1 if the request isn't
related to a tab.
 * @param type How the requested resource will be used.
 * @param timeStamp The time when this signal is triggered, in milliseconds since the epoch.
 * @param ip The server IP address that the request was actually sent to. Note that it may be a
literal IPv6 address.
 * @param fromCache Indicates if this response was fetched from disk cache.
 * @param error The error description. This string is <em>not</em> guaranteed to remain backwards
compatible between releases. You must not parse and act based upon its content.
 */
class OnErrorOccurredDetails(
    val requestId: String,
    val url: String,
    val method: String,
    val frameId: Int,
    val parentFrameId: Int,
    val originUrl: String? = null,
    val documentUrl: String? = null,
    val tabId: Int,
    val type: String,
    val timeStamp: Float,
    val ip: String? = null,
    val fromCache: Boolean,
    val error: String
) {
    companion object {
        fun fromJson(json: JSONObject): OnErrorOccurredDetails {
            return OnErrorOccurredDetails(
                json.getString("requestId"),
                json.getString("url"),
                json.getString("method"),
                json.getInt("frameId"),
                json.getInt("parentFrameId"),
                json.optString("originUrl"),
                json.optString("documentUrl"),
                json.getInt("tabId"),
                json.getString("type"),
                json.getDouble("timeStamp").toFloat(),
                json.optString("ip"),
                json.getBoolean("fromCache"),
                json.getString("error"),
            )
        }
    }
}
