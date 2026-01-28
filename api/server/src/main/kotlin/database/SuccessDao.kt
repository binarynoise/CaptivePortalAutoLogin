package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SuccessDao {
    
    @Query("SELECT count FROM successes WHERE version = :version AND year = :year AND month = :month AND ssid = :ssid AND url = :url AND solver = :solver")
    suspend fun getCount(version: String, year: Int, month: Int, ssid: String, url: String, solver: String): Int?
    
    @Query(
        """
        INSERT INTO successes (version, year, month, ssid, url, solver, count)
        VALUES (:version, :year, :month, :ssid, :url, :solver, 1)
        ON CONFLICT(version, year, month, ssid, url, solver) 
        DO UPDATE SET count = count + 1
        """
    )
    suspend fun insertOrIncrement(version: String, year: Int, month: Int, ssid: String, url: String, solver: String)
    
    @Query("SELECT * FROM successes")
    suspend fun getAllSuccesses(): List<SuccessEntity>
    
    @Query(
        """
        SELECT * FROM successes 
        WHERE year = :year 
        AND month = :month 
        AND version LIKE '%' || :version || '%'
        AND url LIKE '%' || :domain || '%'
        ORDER BY count DESC, ssid ASC, url ASC
    """
    )
    suspend fun getSuccessDetails(year: Int, month: Int, version: String, domain: String): List<SuccessEntity>
}
