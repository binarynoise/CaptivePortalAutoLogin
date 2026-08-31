package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema

const val USAGE_STATS_TABLE_NAME = "usage_stats"

@Entity(tableName = USAGE_STATS_TABLE_NAME)
@DataSchema
class UsageStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val version: String,
    val manual: Boolean,
)
