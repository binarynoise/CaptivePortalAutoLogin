package de.binarynoise.util.okhttp

import java.net.URLDecoder
import org.jsoup.parser.Parser

/**
 * Decodes HTML entities in the given string.
 *
 * @receiver The string to decode.
 * @return The decoded string.
 */
fun String.decodeHtml(): String = Parser.unescapeEntities(this, false)

/**
 * Decodes URL-encoded characters in the given string.
 *
 * @receiver The string to decode.
 * @return The decoded string.
 */
fun String.decodeUrl(): String = URLDecoder.decode(this, "UTF-8")
