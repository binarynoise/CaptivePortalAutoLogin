package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

@Entity(tableName = "usage_stats")
@DataSchema
class UsageStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val version: String,
    val manual: Boolean,
)
