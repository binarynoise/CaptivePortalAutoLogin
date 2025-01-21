package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param startedDateTime Date and time stamp for the beginning of the page load (ISO 8601).
 * @param id Unique identifier of a page. Entries use it to refer the parent page.
 * @param title Page title.
 * @param pageTiming Detailed timing info about the page load.
 */
@Serializable
data class Page(
    @SerialName("startedDateTime") var startedDateTime: LocalDateTime,
    @SerialName("id") var id: String,
    @SerialName("title") var title: String,
    @SerialName("pageTimings") var pageTiming: PageTiming,
)
