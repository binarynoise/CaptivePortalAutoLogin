package de.binarynoise.util.okhttp

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
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

/**
 * submit this form
 * @param parameters overrides parameters specified in the form
 */
fun FormElement.submit(
    client: OkHttpClient,
    baseUrl: HttpUrl,
    parameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    val method = this.attr("method").lowercase()
    check(method != "dialog") {}
    val action = this.attr("action").takeIf { it.isNotEmpty() }?.toHttpUrl(baseUrl) ?: baseUrl
    val formParameters = this.toParameterMap()
    return when (method) {
        "", "get" -> {
            client.get(
                action,
                null,
                formParameters + queryParameters + parameters,
                preConnectSetup,
            )
        }
        "post" -> {
            val enctype = this.attr("enctype")
            when (enctype) {
                "", "application/x-www-form-urlencoded" -> {
                    client.postForm(
                        action,
                        null,
                        formParameters + parameters,
                        queryParameters,
                        preConnectSetup,
                    )
                }
                "multipart/form-data" -> {
                    client.postMultipartForm(
                        action,
                        null,
                        formParameters + parameters,
                        queryParameters,
                        preConnectSetup,
                    )
                }
                else -> error("unknown post enctype $enctype")
            }
        }
        "method" -> error("submitting dialog forms is unsupported")
        else -> error("invalid form method $method")
    }
}

/**
 * Submit the only form present within this [Document]
 * @throws IllegalArgumentException if more than one form is present
 * @throws NoSuchElementException if no form is present
 */
fun Document.submitOnlyForm(
    client: OkHttpClient,
    baseUrl: HttpUrl,
    parameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    val form = this.forms().single()
    return form.submit(client, baseUrl, parameters, queryParameters, preConnectSetup)
}
