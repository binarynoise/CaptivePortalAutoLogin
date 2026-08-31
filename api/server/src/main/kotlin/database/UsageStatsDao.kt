package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UsageStatsDao {
    @Insert
    suspend fun insert(entity: UsageStatsEntity)
    
    @Query("SELECT * FROM usage_stats")
    suspend fun getAll(): List<UsageStatsEntity>
    
    @Query("SELECT * FROM usage_stats WHERE manual = :manual")
    suspend fun getAll(manual: Boolean): List<UsageStatsEntity>
    
    @Query("")
    /**
     * @param start start time stamp, e.g., 1 year ago
     * @param end end time stamp, e.g., now
     * @param interval interval, e.g., 1 week
     * @param includeAutomatic include automatic update queries
     * @param includeManual include manual update queries
     */
    suspend fun countActiveInstalls(
        start: Instant,
        end: Instant,
        interval: Duration,
        includeAutomatic: Boolean,
        includeManual: Boolean,
    ): List<UsageStatsEntity>
    
    suspend fun insert(version: String, manual: Boolean) {
        insert(UsageStatsEntity(timestamp = Clock.System.now(), version = version, manual = manual))
    }
}
