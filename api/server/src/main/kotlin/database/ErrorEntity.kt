package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@Entity(tableName = "errors")
@DataSchema
open class ErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: String,
    val timestamp: Instant,
    val ssid: String,
    val url: String,
    val message: String,
    val solver: String,
    val stackTrace: String,
) {
    fun toExtendedErrorEntity() = ExtendedErrorEntity(
        id = id,
        version = version,
        timestamp = timestamp,
        ssid = ssid,
        url = url,
        message = message,
        solver = solver,
        stackTrace = stackTrace,
        domain = url.getUrlDomain(),
        majorVersion = version.getMajorVersion(),
        year = timestamp.toLocalDateTime(UTC).year,
        month = timestamp.toLocalDateTime(UTC).month.number,
    )
}

@DataSchema
class ExtendedErrorEntity(
    id: Long = 0,
    version: String,
    timestamp: Instant,
    ssid: String,
    url: String,
    message: String,
    solver: String,
    stackTrace: String,
    val domain: String,
    val majorVersion: Int,
    val year: Int,
    val month: Int,
) : ErrorEntity(id, version, timestamp, ssid, url, message, solver, stackTrace)
