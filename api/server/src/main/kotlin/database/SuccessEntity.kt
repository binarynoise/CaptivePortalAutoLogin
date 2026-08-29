package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@Entity(tableName = "successes")
@DataSchema
open class SuccessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: String,
    val timestamp: Instant,
    val ssid: String,
    val url: String,
    val solver: String,
) {
    fun toExtendedSuccessEntity() = ExtendedSuccessEntity(
        id = id,
        version = version,
        timestamp = timestamp,
        ssid = ssid,
        url = url,
        solver = solver,
    )
    
    fun copy(
        id: Long = this.id,
        version: String = this.version,
        timestamp: Instant = this.timestamp,
        ssid: String = this.ssid,
        url: String = this.url,
        solver: String = this.solver,
    ): SuccessEntity {
        return SuccessEntity(
            id = id,
            version = version,
            timestamp = timestamp,
            ssid = ssid,
            url = url,
            solver = solver,
        )
    }
}

@DataSchema
class ExtendedSuccessEntity(
    id: Long,
    version: String,
    timestamp: Instant,
    ssid: String,
    url: String,
    solver: String,
) : SuccessEntity(id, version, timestamp, ssid, url, solver) {
    val domain: String = url.getUrlDomain()
    val majorVersion: Int = version.getMajorVersion()
    private val localDateTime = timestamp.toLocalDateTime(UTC)
    val year: Int = localDateTime.year
    val month: Int = localDateTime.month.number
    val day: Int = localDateTime.day
}
