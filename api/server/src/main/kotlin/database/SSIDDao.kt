package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Query

@Dao
interface SSIDDao {
    @Query(
        """
        SELECT ssid 
        FROM successes
        WHERE version NOT LIKE '%+%'
        AND CAST(SUBSTR(version, 1, INSTR(version, '-') - 1) AS INTEGER) <= :majorVersion
        AND timestamp >= :since
        GROUP BY ssid
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    suspend fun getSSIDs(
        limit: Int,
        majorVersion: Int,
        since: Instant,
    ): List<String>
}
