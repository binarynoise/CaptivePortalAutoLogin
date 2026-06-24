package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            // Recreate successes table with v4 schema:
            //   - auto-increment id primary key instead of composite PK
            //   - year/month replaced by timestamp (epoch milliseconds)
            //   - count column removed (each success is its own row)
            connection.execSQL(
                """
                CREATE TABLE successes_new (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    version TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    ssid TEXT NOT NULL,
                    url TEXT NOT NULL,
                    solver TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent()
            )
            
            // Expand aggregated rows into individual rows (one per count),
            // converting year/month to epoch milliseconds (first of month UTC)
            connection.execSQL(
                """
                WITH RECURSIVE expand AS (
                    SELECT version, year, month, ssid, url, solver, count, 1 AS n
                    FROM successes
                    UNION ALL
                    SELECT version, year, month, ssid, url, solver, count, n + 1
                    FROM expand
                    WHERE n < count
                )
                INSERT INTO successes_new (version, timestamp, ssid, url, solver)
                SELECT version,
                       strftime('%s', printf('%04d-%02d-01', year, month)) * 1000,
                       ssid, url, solver
                FROM expand
            """.trimIndent()
            )
            
            connection.execSQL("DROP TABLE successes")
            connection.execSQL("ALTER TABLE successes_new RENAME TO successes")
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
