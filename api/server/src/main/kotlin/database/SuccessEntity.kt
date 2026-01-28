package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Entity

@Entity(
    tableName = "successes",
    primaryKeys = ["version", "year", "month", "ssid", "url", "solver"],
)
data class SuccessEntity(
    val version: String,
    val year: Int,
    val month: Int,
    val ssid: String,
    val url: String,
    val solver: String,
    val count: Int,
)
