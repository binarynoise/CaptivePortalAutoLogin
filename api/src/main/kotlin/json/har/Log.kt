package de.binarynoise.captiveportalautologin.api.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param version Version number of the format.
 * @param creator An object of type `Creator` that contains the name and version information of the log creator application.
 * @param browser An object of type browser that contains the name and version information of the user agent.
 * @param pages An array of objects of type `Page`, each representing one exported (tracked) page. Leave out this field if the application does not support grouping by pages.
 * @param entries A list of objects of type `Entry`, each representing one exported (tracked) HTTP request.
 */
@Serializable
data class Log(
    @SerialName("version") var version: String, // 1.2
    @SerialName("creator") var creator: Creator,
    @SerialName("browser") var browser: Browser?,
    @SerialName("pages") var pages: MutableList<Page>?,
    @SerialName("entries") var entries: MutableList<Entry>,
)
