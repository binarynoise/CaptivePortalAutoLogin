package de.binarynoise.captiveportalautologin.json.har

import java.net.HttpCookie
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import android.os.Build

@Serializable
data class Cookie(
    @SerialName("name") var name: String,
    @SerialName("value") var value: String,
    @SerialName("path") var path: String?,
    @SerialName("domain") var domain: String?,
    @SerialName("expires") var expires: LocalDateTime?,
    @SerialName("httpOnly") var httpOnly: Boolean?,
    @SerialName("secure") var secure: Boolean?,
) {
    constructor(cookie: HttpCookie) : this(
        name = cookie.name,
        value = cookie.value,
        path = cookie.path,
        domain = cookie.domain,
        expires = Instant.fromEpochMilliseconds(cookie.maxAge).toLocalDateTime(TimeZone.currentSystemDefault()),
        httpOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cookie.isHttpOnly else false,
        secure = cookie.secure
    )
}
