package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Entity
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@Entity(
    tableName = "successes",
    primaryKeys = ["version", "year", "month", "ssid", "url", "solver"],
)
@DataSchema
open class SuccessEntity(
    val version: String,
    val year: Int,
    val month: Int,
    val ssid: String,
    val url: String,
    val solver: String,
    val count: Int,
) {
    fun toExtendedSuccessEntity() = ExtendedSuccessEntity(
        version,
        year,
        month,
        ssid,
        url,
        solver,
        count,
        url.getUrlDomain(),
        version.getMajorVersion(),
    )
}

@DataSchema
class ExtendedSuccessEntity(
    version: String,
    year: Int,
    month: Int,
    ssid: String,
    url: String,
    solver: String,
    count: Int,
    val domain: String,
    val majorVersion: Int,
) : SuccessEntity(version, year, month, ssid, url, solver, count)
