package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "errors")
data class ErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: String,
    val timestamp: Instant,
    val ssid: String,
    val url: String,
    val message: String,
)
