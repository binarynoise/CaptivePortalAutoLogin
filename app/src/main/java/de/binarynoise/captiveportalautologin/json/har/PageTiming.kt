package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PageTiming(
    /**
     * Content of the page loaded. Number of milliseconds since the page load started (page.startedDateTime).
     * Use 0 if the timing does not apply to the current request.
     */
    @SerialName("onContentLoad") var onContentLoad: Int?,
    /**
     * Page is loaded (onLoad event fired). Number of milliseconds since the page load started (page.startedDateTime).
     * Use 0 if the timing does not apply to the current request.
     */
    @SerialName("onLoad") var onLoad: Int?,
)
