package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Duration
import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SuccessDao {
    @Insert
    suspend fun insert(success: SuccessEntity)
    
    @Update
    suspend fun update(success: SuccessEntity)
    
    @Query("SELECT * FROM successes")
    suspend fun getAll(): List<SuccessEntity>
    
    
    @Query(
        """
        SELECT * FROM successes
        WHERE version = :version 
          AND ssid = :ssid 
          AND url = :url
          AND solver = :solver
          AND ABS(timestamp - :timestamp) <= :timestampDelta
        LIMIT 1
        """
    )
    /**
     * get existing error id by its data
     * @param timestampDelta defines how much delta around the given error timestamp is considered "the same"
     */
    suspend fun getSimilarSuccess(
        version: String,
        ssid: String,
        url: String,
        solver: String,
        timestamp: Instant,
        timestampDelta: Duration = Duration.ZERO,
    ): SuccessEntity?
    
    @Transaction
    /**
     * update or insert success based on timestamp similarity
     * @param timestampDelta defines how much delta around the given success timestamp is considered "the same"
     * @return the replaced success
     */
    suspend fun upsertSimilarSuccess(
        success: SuccessEntity,
        timestampDelta: Duration = Duration.ZERO,
    ): SuccessEntity? {
        val similarSuccess = getSimilarSuccess(
            success.version,
            success.ssid,
            success.url,
            success.solver,
            success.timestamp,
            timestampDelta,
        )
        if (similarSuccess != null) {
            update(success.copy(id = similarSuccess.id))
            return similarSuccess
        }
        insert(success)
        return null
    }
}
