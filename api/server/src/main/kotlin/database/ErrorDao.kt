package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Duration
import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ErrorDao {
    @Insert
    suspend fun insert(error: ErrorEntity)
    
    @Update
    suspend fun update(error: ErrorEntity)
    
    @Query("SELECT * FROM errors ORDER BY timestamp DESC")
    suspend fun getAll(): List<ErrorEntity>
    
    @Query("SELECT * FROM errors WHERE message LIKE 'unknown portal' ORDER BY timestamp DESC")
    suspend fun getUnknownPortals(): List<ErrorEntity>
    
    @Query(
        """
        SELECT * FROM errors 
        WHERE message NOT LIKE 'unknown portal' 
        AND message NOT LIKE 'connection closed'
        AND message NOT LIKE 'Failed to connect to %'
        AND message NOT LIKE 'Unable to resolve host %'
        AND message NOT LIKE 'Software caused connection abort'
        AND message NOT LIKE 'Binding socket to network % failed: %'
        AND message NOT LIKE 'Chain validation failed'
        AND message NOT LIKE 'java.security.cert.CertPathValidatorException: %'
        AND message NOT LIKE 'Socket is closed'
        ORDER BY timestamp DESC
        """
    )
    suspend fun getNoNoise(): List<ErrorEntity>
    
    @Query(
        """
        SELECT * FROM errors
        WHERE version = :version 
          AND ssid = :ssid 
          AND url = :url
          AND message = :message 
          AND solver = :solver
          AND ABS(timestamp - :timestamp) <= :timestampDelta
        LIMIT 1
        """
    )
    /**
     * get existing error id by its data
     * @param timestampDelta defines how much delta around the given error timestamp is considered "the same"
     */
    suspend fun getSimilarError(
        version: String,
        ssid: String,
        url: String?,
        message: String?,
        solver: String?,
        timestamp: Instant,
        timestampDelta: Duration = Duration.ZERO,
    ): ErrorEntity?
    
    @Transaction
    /**
     * update or insert error based on timestamp similarity
     * @param timestampDelta defines how much delta around the given error timestamp is considered "the same"
     * @return the replaced error
     */
    suspend fun upsertSimilarError(
        error: ErrorEntity,
        timestampDelta: Duration = Duration.ZERO,
    ): ErrorEntity? {
        val similarError = getSimilarError(
            error.version,
            error.ssid,
            error.url,
            error.message,
            error.solver,
            error.timestamp,
            timestampDelta,
        )
        if (similarError != null) {
            update(error.copy(id = similarError.id))
            return similarError
        }
        insert(error)
        return null
    }
}
