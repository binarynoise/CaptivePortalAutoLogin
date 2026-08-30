package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            // Recreate errors table with v6 schema:
            //   - url, message, solver, stackTrace are now nullable
            connection.execSQL(
                """
                CREATE TABLE errors_new (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    version TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    ssid TEXT NOT NULL,
                    url TEXT,
                    message TEXT,
                    solver TEXT,
                    stackTrace TEXT,
                    harName TEXT
                )
            """.trimIndent()
            )
            
            connection.execSQL(
                """
                INSERT INTO errors_new (id, version, timestamp, ssid, url, message, solver, stackTrace, harName)
                SELECT id, version, timestamp, ssid,
                       CASE WHEN url = '' OR url = 'null' THEN NULL ELSE url END,
                       CASE WHEN message = '' OR message = 'null' THEN NULL ELSE message END,
                       CASE WHEN solver = '' OR solver = 'null' THEN NULL ELSE solver END,
                       CASE WHEN stackTrace = '' OR stackTrace = 'null' THEN NULL ELSE stackTrace END,
                       harName
                FROM errors
            """.trimIndent()
            )
            
            connection.execSQL("DROP TABLE errors")
            connection.execSQL("ALTER TABLE errors_new RENAME TO errors")
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
