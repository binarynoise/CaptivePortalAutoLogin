package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Clock
import androidx.room.Dao
import androidx.room.Insert

@Dao
interface UsageStatsDao {
    @Insert
    suspend fun insert(entity: UsageStatsEntity)
    
    suspend fun insert(version: String, manual: Boolean) {
        insert(UsageStatsEntity(timestamp = Clock.System.now(), version = version, manual = manual))
    }
}
