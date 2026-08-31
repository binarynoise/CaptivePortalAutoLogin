package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UsageStatsDao {
    @Insert
    suspend fun insert(entity: UsageStatsEntity)
    
    @Query("SELECT * FROM $USAGE_STATS_TABLE_NAME")
    suspend fun getAll(): List<UsageStatsEntity>
    
    @Query("SELECT * FROM $USAGE_STATS_TABLE_NAME WHERE manual = :manual")
    suspend fun getAll(manual: Boolean): List<UsageStatsEntity>
    
    data class ActiveInstall(
        @ColumnInfo(name = "bucket_start") val start: Instant,
        @ColumnInfo(name = "entry_count") val count: Int,
    )
    
    @Query(
        """
        WITH RECURSIVE
            $BUCKET_GENERATOR,
            -- pre filter the data
            filtered_data AS (
                SELECT * FROM $USAGE_STATS_TABLE_NAME
                WHERE (version NOT LIKE '%-dev' OR :includeDevVersions)
                AND (CAST(SUBSTR(version, 1, INSTR(version, '-') - 1) AS INTEGER) BETWEEN :minimumMajorVersion AND :maximumMajorVersion)
                AND ((:includeManual AND manual) OR (:includeAutomatic AND NOT manual))
            )
        
        -- Join the virtual buckets with usage stats data
        SELECT
            b.bucket_start,
            COUNT(u.timestamp) AS entry_count
        FROM buckets b
        LEFT JOIN filtered_data u ON
            u.timestamp >= b.bucket_start
            AND u.timestamp < b.bucket_end
        GROUP BY b.bucket_start
        HAVING entry_count >= :minimumEntryCount
        ORDER BY b.bucket_start ASC;
    """
    )
    /**
     * count active installations and slice them into time frame buckets
     * @param start start time stamp, e.g., 1 year ago
     * @param end end time stamp, e.g., now
     * @param interval interval, e.g., 1 week
     * @param includeAutomatic include automatic update queries
     * @param includeManual include manual update queries
     * @param includeDevVersions also include dev versions
     * @param minimumMajorVersion minimum major version as pre-filter
     * @param maximumMajorVersion maximum major version as pre-filter
     * @param minimumEntryCount defines minimum amount of count to be included
     */
    suspend fun countActiveInstalls(
        start: Instant,
        end: Instant,
        interval: Duration,
        includeAutomatic: Boolean = true,
        includeManual: Boolean = false,
        includeDevVersions: Boolean = false,
        minimumMajorVersion: Int = 0,
        maximumMajorVersion: Int = Int.MAX_VALUE,
        minimumEntryCount: Int = 0,
    ): List<ActiveInstall>
    
    data class ActiveVersionInstall(
        @ColumnInfo(name = "bucket_start") val start: Instant,
        @ColumnInfo(name = "version") val version: String,
        @ColumnInfo(name = "entry_count") val count: Int,
    )
    
    @Query(
        """
        WITH RECURSIVE
            $BUCKET_GENERATOR,
            -- Get a unique list of all versions present in the data
            versions AS (
                SELECT DISTINCT version
                FROM $USAGE_STATS_TABLE_NAME
                WHERE (version NOT LIKE '%-dev' OR :includeDevVersions)
                AND (CAST(SUBSTR(version, 1, INSTR(version, '-') - 1) AS INTEGER) BETWEEN :minimumMajorVersion AND :maximumMajorVersion)
            ),
            -- Create the "Grid" (Every version matched with every bucket)
            grid AS (
                SELECT *
                FROM versions
                CROSS JOIN buckets
            ),
            -- pre filter the data
            filtered_data AS (
                SELECT * FROM $USAGE_STATS_TABLE_NAME
                WHERE version IN versions
                AND ((:includeManual AND manual) OR (:includeAutomatic AND NOT manual))
            )
        
        -- Join the virtual buckets with usage stats data
        SELECT
            g.bucket_start,
            g.version,
            COUNT(u.timestamp) AS entry_count
        FROM grid g
        LEFT JOIN filtered_data u ON
            u.version = g.version
            AND u.timestamp >= g.bucket_start
            AND u.timestamp < g.bucket_end
        GROUP BY
            g.version,
            g.bucket_start
        HAVING entry_count >= :minimumEntryCount
        ORDER BY
            g.bucket_start ASC,
            g.version DESC
    """
    )
    /**
     * @see countActiveInstalls
     * an additional column is scoping count per version
     * but `minimumEntryCount` defaults to 1 instead
     */
    suspend fun countActiveInstallsPerVersion(
        start: Instant,
        end: Instant,
        interval: Duration,
        includeAutomatic: Boolean = true,
        includeManual: Boolean = false,
        includeDevVersions: Boolean = false,
        minimumMajorVersion: Int = 0,
        maximumMajorVersion: Int = Int.MAX_VALUE,
        minimumEntryCount: Int = 1,
    ): List<ActiveVersionInstall>
    
    suspend fun insert(version: String, manual: Boolean) {
        insert(UsageStatsEntity(timestamp = Clock.System.now(), version = version, manual = manual))
    }
}
