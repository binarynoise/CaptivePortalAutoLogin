package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            // Recreate errors table with v1 schema, converting timestamp from TEXT to INTEGER
            connection.execSQL(
                """
                CREATE TABLE errors_new (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    version TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    ssid TEXT NOT NULL,
                    url TEXT NOT NULL,
                    message TEXT NOT NULL
                )
            """.trimIndent()
            )
            
            // Copy data, converting timestamp from ISO TEXT to epoch milliseconds INTEGER
            connection.execSQL(
                """
                INSERT INTO errors_new (id, version, timestamp, ssid, url, message)
                SELECT id, version, strftime('%s', timestamp) * 1000, ssid, url, error FROM errors
            """.trimIndent()
            )
            
            // Drop old table
            connection.execSQL("DROP TABLE errors")
            
            // Rename new table
            connection.execSQL("ALTER TABLE errors_new RENAME TO errors")
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
