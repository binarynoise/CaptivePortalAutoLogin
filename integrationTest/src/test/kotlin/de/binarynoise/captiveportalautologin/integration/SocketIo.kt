package de.binarynoise.captiveportalautologin.integration

import java.io.InputStream
import java.io.OutputStream

/**
 * Reads one line of text, terminated by `\n`, without buffering anything beyond it.
 */
fun InputStream.readLineUnbuffered(): String? {
    val line = StringBuilder()
    while (true) {
        val character = read()
        if (character < 0) return line.takeIf { it.isNotEmpty() }?.toString()
        if (character == '\n'.code) return line.toString().removeSuffix("\r")
        line.append(character.toChar())
    }
}

fun InputStream.copyExactly(length: Long, destination: OutputStream) {
    val buffer = ByteArray(8 * 1024)
    var remaining = length
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) throw IllegalStateException("the request ended after ${length - remaining} of $length bytes")
        destination.write(buffer, 0, read)
        remaining -= read
    }
}

/**
 * Copies a chunked body, including its terminating zero-length chunk.
 */
fun InputStream.copyChunks(destination: OutputStream) {
    while (true) {
        val sizeLine = readLineUnbuffered() ?: throw IllegalStateException("the chunked body ended unexpectedly")
        val size = sizeLine.substringBefore(';').trim().toIntOrNull(16)
            ?: throw IllegalStateException("'$sizeLine' is not a chunk size")
        destination.write("$sizeLine\r\n".toByteArray())
        if (size == 0) {
            while (true) {
                val trailer = readLineUnbuffered() ?: return
                destination.write("$trailer\r\n".toByteArray())
                if (trailer.isEmpty()) return
            }
        }
        copyExactly(size.toLong(), destination)
        // every chunk is terminated by CRLF, which has to be consumed before the next size line is read
        check(readLineUnbuffered() != null) { "the chunked body ended unexpectedly" }
        destination.write("\r\n".toByteArray())
    }
}

/**
 * Copies everything that is left, used for responses without a length and without chunked encoding.
 */
fun InputStream.copyUntilEnd(destination: OutputStream) {
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        destination.write(buffer, 0, read)
    }
}

internal fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { (header, _) -> header.equals(name, ignoreCase = true) }?.value

private fun Map<String, String>.isChunked(): Boolean =
    header("transfer-encoding")?.contains("chunked", ignoreCase = true) == true

/**
 * Copies the body framed by [headers]. A request without a length has no body at all, while a response
 * without one lasts until the connection closes.
 */
fun InputStream.copyBody(headers: Map<String, String>, destination: OutputStream, readUntilEndWithoutLength: Boolean) {
    when {
        headers.isChunked() -> copyChunks(destination)
        headers.header("content-length") != null -> copyExactly(
            headers.header("content-length")!!.toLong(), destination
        )
        readUntilEndWithoutLength -> copyUntilEnd(destination)
        // a request without either header has no body at all, waiting for one would block forever
        else -> Unit
    }
}

/**
 * Reads the header lines of one http message, without consuming anything beyond the empty line.
 */
fun InputStream.readHeaders(): Map<String, String> = buildMap {
    while (true) {
        val header = readLineUnbuffered() ?: break
        if (header.isEmpty()) break
        put(header.substringBefore(':'), header.substringAfter(':', "").trim())
    }
}
