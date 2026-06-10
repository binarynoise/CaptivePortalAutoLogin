package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ErrorDao {
    @Insert
    suspend fun insert(error: ErrorEntity)
    
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
}
