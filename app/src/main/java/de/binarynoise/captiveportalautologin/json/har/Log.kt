package de.binarynoise.captiveportalautologin.json.har

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Log(
    /**
     * Version number of the format.
     */
    @SerialName("version") var version: String, // 1.2
    /**
     * An object of type `Creator` that contains the name and version information of the log creator application.
     */
    @SerialName("creator") var creator: Creator,
    /**
     * An object of type browser that contains the name and version information of the user agent.
     *
     * Optional.
     */
    @SerialName("browser") var browser: Browser?,
    /**
     * An array of objects of type `Page`, each representing one exported (tracked) page. Leave out this field if the application does not support grouping by pages.
     *
     * Optional.
     */
    @SerialName("pages") var pages: MutableList<Page>?,
    /**
     * A list of objects of type `Entry`, each representing one exported (tracked) HTTP request.
     */
    @SerialName("entries") var entries: MutableList<Entry>,
)
