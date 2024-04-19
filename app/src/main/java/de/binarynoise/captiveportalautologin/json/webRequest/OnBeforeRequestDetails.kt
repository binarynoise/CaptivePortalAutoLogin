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
 * @param requestBody Contains the HTTP request body data. Only provided if extraInfoSpec contains
'requestBody'.
 * @param tabId The ID of the tab in which the request takes place. Set to -1 if the request isn't
related to a tab.
 * @param type How the requested resource will be used.
 * @param timeStamp The time when this signal is triggered, in milliseconds since the epoch.
 */
class OnBeforeRequestDetails(
    val requestId: String,
    val url: String,
    val method: String,
    val frameId: Int,
    val parentFrameId: Int,
    val originUrl: String? = null,
    val documentUrl: String? = null,
    val requestBody: RequestBody? = null,
    val tabId: Int,
    val type: String,
    val timeStamp: Long
) {
    companion object {
        fun fromJson(json: JSONObject): OnBeforeRequestDetails {
            return OnBeforeRequestDetails(
                json.getString("requestId"),
                json.getString("url"),
                json.getString("method"),
                json.getInt("frameId"),
                json.getInt("parentFrameId"),
                json.optString("originUrl"),
                json.optString("documentUrl"),
                json.optJSONObject("requestBody")?.let { RequestBody.fromJson(it) },
                json.getInt("tabId"),
                json.getString("type"),
                json.getLong("timeStamp")
            )
        }
    }
}
