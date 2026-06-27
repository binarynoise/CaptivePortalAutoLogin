package de.binarynoise.captiveportalautologin.server.database

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
        GROUP BY ssid
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    suspend fun getSSIDs(
        limit: Int = 1024,
        majorVersion: Int = Int.MAX_VALUE,
    ): List<String>
}
