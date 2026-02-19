package de.binarynoise.captiveportalautologin.server.database

import java.io.File
import java.nio.file.Path
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.binarynoise.captiveportalautologin.server.database.migration.MIGRATION_1_2
import de.binarynoise.logger.Logger.log

@Database(
    entities = [ErrorEntity::class, SuccessEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun errorDao(): ErrorDao
    abstract fun successDao(): SuccessDao
    
    companion object {
        fun createDatabase(root: Path): AppDatabase {
            val dbFile = File(root.toFile(), "db.sqlite")
            
            // Prepare pre-Room DB by adding room_master_table if missing
            if (dbFile.exists()) {
                val driver = BundledSQLiteDriver()
                driver.open(dbFile.absolutePath).use { connection ->
                    val hasRoomMaster = try {
                        connection.prepare("SELECT 1 FROM room_master_table LIMIT 1").use { it.step() }
                    } catch (_: Exception) {
                        false
                    }
                    if (!hasRoomMaster) {
                        connection.execSQL("PRAGMA user_version = 1")
                        log("created user_version")
                    }
                }
            }
            
            return Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(connection: SQLiteConnection) {
                        log("AppDatabase created")
                    }
                    
                    override fun onOpen(connection: SQLiteConnection) {
                        log("AppDatabase opened")
                    }
                    
                    override fun onDestructiveMigration(connection: SQLiteConnection) {
                        log("AppDatabase migrated destructively")
                    }
                })
                .build()
        }
    }
}
