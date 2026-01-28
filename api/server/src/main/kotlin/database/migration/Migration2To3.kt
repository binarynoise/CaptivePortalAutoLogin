package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            // Add solver and stackTrace columns to errors table
            connection.execSQL("ALTER TABLE errors ADD COLUMN solver TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE errors ADD COLUMN stackTrace TEXT NOT NULL DEFAULT ''")
            
            // Add solver column to successes table
            connection.execSQL("ALTER TABLE successes ADD COLUMN solver TEXT NOT NULL DEFAULT ''")
            
            // Since we can't directly modify primary keys in SQLite, we need to recreate the successes table
            // 1. Create temporary table with new schema
            connection.execSQL(
                """
                CREATE TABLE successes_new (
                    version TEXT NOT NULL, 
                    year INTEGER NOT NULL, 
                    month INTEGER NOT NULL, 
                    ssid TEXT NOT NULL, 
                    url TEXT NOT NULL, 
                    solver TEXT NOT NULL, 
                    count INTEGER NOT NULL, 
                    PRIMARY KEY(version, year, month, ssid, url, solver)
                )
            """.trimIndent()
            )
            
            // 2. Copy data from old table to new table
            connection.execSQL(
                """
                INSERT INTO successes_new (version, year, month, ssid, url, solver, count)
                SELECT version, year, month, ssid, url, solver, count FROM successes
            """.trimIndent()
            )
            
            // 3. Drop old table
            connection.execSQL("DROP TABLE successes")
            
            // 4. Rename new table to original name
            connection.execSQL("ALTER TABLE successes_new RENAME TO successes")
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
