package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.TypeConverter

class DatabaseTypeConverters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()
    
    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}
