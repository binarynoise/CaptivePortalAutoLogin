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
    val url: String?,
    val message: String?,
    val solver: String?,
    val stackTrace: String?,
    val harName: String?,
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
        harName = harName,
    )
    
    fun copy(
        id: Long = this.id,
        version: String = this.version,
        timestamp: Instant = this.timestamp,
        ssid: String = this.ssid,
        url: String? = this.url,
        message: String? = this.message,
        solver: String? = this.solver,
        stackTrace: String? = this.stackTrace,
        harName: String? = this.harName,
    ): ErrorEntity {
        return ErrorEntity(
            id = id,
            version = version,
            timestamp = timestamp,
            ssid = ssid,
            url = url,
            message = message,
            solver = solver,
            stackTrace = stackTrace,
            harName = harName,
        )
    }
}

@DataSchema
class ExtendedErrorEntity(
    id: Long = 0,
    version: String,
    timestamp: Instant,
    ssid: String,
    url: String?,
    message: String?,
    solver: String?,
    stackTrace: String?,
    harName: String?,
) : ErrorEntity(id, version, timestamp, ssid, url, message, solver, stackTrace, harName) {
    val domain: String? = url?.getUrlDomain()
    val majorVersion: Int = version.getMajorVersion()
    private val localDateTime = timestamp.toLocalDateTime(UTC)
    val year: Int = localDateTime.year
    val month: Int = localDateTime.month.number
    val day: Int = localDateTime.day
}
