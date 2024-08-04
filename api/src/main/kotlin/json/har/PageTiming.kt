package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param onContentLoad Content of the page loaded. Number of milliseconds since the page load started (page.startedDateTime).
 * @param onLoad Page is loaded (onLoad event fired). Number of milliseconds since the page load started (page.startedDateTime).
 */
@Serializable
data class PageTiming(
    @SerialName("onContentLoad") var onContentLoad: Int?,
    @SerialName("onLoad") var onLoad: Int?,
)
