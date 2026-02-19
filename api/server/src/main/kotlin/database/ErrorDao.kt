package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ErrorDao {
    @Insert
    suspend fun insert(error: ErrorEntity)
    
    @Query("SELECT * FROM errors ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllErrors(limit: Int): List<ErrorEntity>
    
    @Query("SELECT * FROM errors WHERE message LIKE 'unknown portal' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getUnknownPortalErrors(limit: Int): List<ErrorEntity>
    
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
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getNoNoiseErrors(limit: Int): List<ErrorEntity>
    
    @Query(
        """
        SELECT * FROM errors 
        WHERE timestamp BETWEEN :start AND :end
        AND version = :version
        AND message = :message
        AND url LIKE '%' || :domain || '%'
        ORDER BY timestamp DESC
        """
    )
    suspend fun getErrorDetails(
        start: Instant,
        end: Instant,
        version: String,
        message: String,
        domain: String,
    ): List<ErrorEntity>
}
