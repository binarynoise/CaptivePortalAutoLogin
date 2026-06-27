package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            connection.execSQL("ALTER TABLE errors ADD COLUMN harName TEXT")
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
