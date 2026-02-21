package de.binarynoise.util.okhttp

import java.nio.charset.Charset
import okhttp3.RequestBody
import okio.Buffer

fun RequestBody.readText(): String {
    val charset: Charset = contentType()?.charset() ?: Charsets.UTF_8
    
    Buffer().use { buffer ->
        writeTo(buffer)
        return buffer.readString(charset)
    }
}
