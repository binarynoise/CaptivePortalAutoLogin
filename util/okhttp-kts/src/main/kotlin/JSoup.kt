package de.binarynoise.util.okhttp

import org.jsoup.nodes.Element
import org.jsoup.nodes.FormElement


fun Element.getInput(name: String) = selectFirst("input[name=$name]")?.attr("value") ?: error("no $name")
fun Element.hasInput(name: String) = selectFirst("input[name=$name]") != null


/**
 * Convert all of this forms inputs into a parameter map that can be used in requests.
 */
fun FormElement.toParameterMap(): Map<String, String> {
    return this.getElementsByTag("input")
        .filter { it.attr("name").isNotEmpty() }
        .associate { it.attr("name") to it.attr("value") }
}

/**
 * return the action string of this form
 */
fun FormElement.getAction(): String? {
    return this.attribute("action")?.value
}
