package de.binarynoise.captiveportalautologin.server.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SuccessDao {
    @Insert
    suspend fun insert(success: SuccessEntity)
    
    @Query("SELECT * FROM successes")
    suspend fun getAll(): List<SuccessEntity>
}
