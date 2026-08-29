package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration
import androidx.room.TypeConverter

class DatabaseTypeConverters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()
    
    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)
    
    @TypeConverter
    fun fromDuration(value: Duration): Long = value.inWholeMilliseconds
    
    @TypeConverter
    fun toDuration(value: Long): Duration = value.toDuration(DurationUnit.MILLISECONDS)
}
