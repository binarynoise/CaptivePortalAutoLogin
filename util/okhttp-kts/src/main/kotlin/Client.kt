@file:OptIn(ExperimentalContracts::class)

package de.binarynoise.util.okhttp

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Media type for JSON with UTF-8 character set for sending JSON data.
 */
val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()


/**
 * Sends a GET request to the specified URL using the provided OkHttpClient.
 *
 * @param url The URL to send the request to. Can be null if context is provided.
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws IllegalArgumentException if both url and context are null.
 */
fun OkHttpClient.get(
    base: HttpUrl?,
    url: String?,
    queryParameters: Map<String, String> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            base?.newBuilder() ?: throw IllegalArgumentException("url and context cannot both be null")
        } else {
            base?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
        }
        
        queryParameters.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws IllegalArgumentException if both url and context are null.
 */
fun OkHttpClient.call(
    base: HttpUrl?,
    url: String?,
    queryParameters: Map<String, String?> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    val request = Request.Builder().apply {
        val urlBuilder = if (url == null) {
            base?.newBuilder() ?: throw IllegalArgumentException("url and context cannot both be null")
        } else {
            base?.newBuilder(url) ?: url.toHttpUrl().newBuilder()
        }
        queryParameters.forEach { (key, value) ->
            if (value != null) urlBuilder.addQueryParameter(key, value)
        }
        url(urlBuilder.build())
        
        preConnectSetup()
    }.build()
    
    return newCall(request).execute()
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param json The JSON string to include in the request body.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the Request.Builder before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws IllegalArgumentException if both url and context are null.
 */
fun OkHttpClient.postJson(
    base: HttpUrl?,
    url: String?,
    json: String,
    queryParameters: Map<String, String?> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        post(json.toRequestBody(MEDIA_TYPE_JSON))
        preConnectSetup()
    }
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param json The JSON string to include in the request body.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the [Request.Builder] before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.putJson(
    base: HttpUrl?,
    url: String?,
    json: String,
    queryParameters: Map<String, String?> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        put(json.toRequestBody(MEDIA_TYPE_JSON))
        preConnectSetup()
    }
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param form The key-value pairs to include in the request body for form-encoded data.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the [Request.Builder] before building the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.postForm(
    base: HttpUrl?,
    url: String?,
    form: Map<String, String?>,
    queryParameters: Map<String, String?> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        val formBodyBuilder = FormBody.Builder()
        form.forEach { (key, value) ->
            if (value != null) formBodyBuilder.add(key, value)
        }
        post(formBodyBuilder.build())
        preConnectSetup()
    }
}

/**
 * Sends a POST request to the specified URL using the provided OkHttpClient.
 *
 * @param base The HttpUrl to use as the base URL. Can be null if url is provided.
 * @param url The URL to send the request to, will be merged with the base URL. Can be null if context is provided.
 * @param form The key-value pairs to include in the request body for form-encoded data.
 * @param queryParameters The query parameters to include in the request URL. Defaults to an empty map.
 * @param preConnectSetup A function to customize the [Request.Builder] before building the request. Defaults to noop.
 * @param multipartBody A function to customize the [MultipartBody.Builder] before sending the request. Defaults to noop.
 * @return The Response object representing the server's response to the request.
 * @throws Error if both url and context are null.
 */
fun OkHttpClient.postMultipartForm(
    base: HttpUrl?,
    url: String?,
    form: Map<String, String?>,
    queryParameters: Map<String, String?> = emptyMap(),
    preConnectSetup: Request.Builder.() -> Unit = {},
    multipartBody: MultipartBody.Builder.() -> Unit = {},
    multipartType: MediaType = MultipartBody.FORM,
): Response {
    contract {
        callsInPlace(preConnectSetup, InvocationKind.AT_MOST_ONCE)
        callsInPlace(multipartBody, InvocationKind.AT_MOST_ONCE)
    }
    return call(base, url, queryParameters) {
        val formBodyBuilder = MultipartBody.Builder()
        formBodyBuilder.setType(multipartType)
        form.forEach { (key, value) ->
            if (value != null) formBodyBuilder.addFormDataPart(key, value)
        }
        multipartBody(formBodyBuilder)
        post(formBodyBuilder.build())
        preConnectSetup()
    }
}
