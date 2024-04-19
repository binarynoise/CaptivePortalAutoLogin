package de.binarynoise.captiveportalautologin.json.har

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Page(
    /**
     * Date and time stamp for the beginning of the page load (ISO 8601).
     */
    @SerialName("startedDateTime") var startedDateTime: LocalDateTime,
    /**
     * Unique identifier of a page. Entries use it to refer the parent page.
     */
    @SerialName("id") var id: String,
    /**
     * Page title.
     */
    @SerialName("title") var title: String,
    /**
     * Detailed timing info about the page load.
     */
    @SerialName("pageTimings") var pageTiming: PageTiming,
)
