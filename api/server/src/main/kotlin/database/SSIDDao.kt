package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SSIDDao {
    @Query(
        """
        SELECT ssid 
        FROM successes
        LIMIT :limit
        """
    )
    suspend fun getSSIDs(limit: Int = 1024): List<String>
}
