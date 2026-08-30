package de.binarynoise.captiveportalautologin.server.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.binarynoise.logger.Logger.log

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        try {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `usage_stats` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `version` TEXT NOT NULL,
                    `manual` INTEGER NOT NULL
                )
            """.trimIndent()
            )
            
            log("ran $startVersion->$endVersion migration")
        } catch (e: Exception) {
            log("failed to run $startVersion->$endVersion migration", e)
            throw e
        }
    }
}
