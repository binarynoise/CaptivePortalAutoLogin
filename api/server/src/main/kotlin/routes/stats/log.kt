package de.binarynoise.captiveportalautologin.server.routes.stats

import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Instant
import de.binarynoise.captiveportalautologin.api.parseLogFileName
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.FileSize
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.respondPathWithContentDisposition
import de.binarynoise.logger.Logger.log
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

@DataSchema
data class LogEntry(
    val name: String,
    val timestamp: Instant,
    val version: String,
    val checksum: String,
    val fileSize: FileSize,
    val archived: Boolean,
)

fun parseLogPath(path: Path, archived: Boolean): LogEntry {
    val fileSize = FileSize(path.fileSize())
    val name = path.nameWithoutExtension
    val (timestamp, version, checksum) = parseLogFileName(name)
    return LogEntry(name, timestamp, version, checksum, fileSize, archived)
}

val logDB = ApiServer.api.logDB
val logDBArchived = ApiServer.api.logDBArchived

internal fun Route.logRoutes() {
    get("log") {
        call.response.header("Location", "log/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("log/") {
        get {
            val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
                ColumnDefinition("name", "Name", Comparators.RegularComparator),
                ColumnDefinition("timestamp", "Timestamp", Comparators.RegularComparator),
                ColumnDefinition("version", "Version", Comparators.VersionComparator),
                ColumnDefinition("checksum", "Checksum", Comparators.RegularComparator),
                ColumnDefinition("fileSize", "File Size", Comparators.RegularComparator),
                ColumnDefinition("archived", "Archived", Comparators.RegularComparator),
            )
            val actionColumnDefinitions: DataFrame<ActionColumnDefinition> = dataFrameOf(
                ActionColumnDefinition("view", "View", listOf("name")),
                ActionColumnDefinition("download", "Download", listOf("name")),
                ActionColumnDefinition("archive", "Archive", listOf("name")),
                ActionColumnDefinition("delete", "Delete", listOf("name")),
            )
            val defaultGroups: Set<String> = setOf("timestamp", "version")
            val defaultSort = "timestamp-desc"
            
            val preFilterDefinitions: List<PreFilterDefinition> = listOf(
                PreFilterDefinition("all", "All") {
                    loadLogEntries(includeRegular = true, includeArchived = true)
                },
                PreFilterDefinition("regular", "Regular") {
                    loadLogEntries(includeRegular = true)
                },
                PreFilterDefinition("archived", "Archived") {
                    loadLogEntries(includeArchived = true)
                },
            )
            
            val tableData = generateTableData(
                call,
                columnDefinitions,
                preFilterDefinitions,
                defaultGroups,
                defaultSort = defaultSort,
                actionColumnDefinitions = actionColumnDefinitions,
                defaultPreFilter = "regular"
            )
            
            call.respond(
                MustacheContent(
                    "logs.mustache", mapOf(
                        "title" to "Log files",
                        "backLink" to "../",
                    ) + tableData.toMap()
                )
            )
        }
        
        fun downloadRoutingHandler(inline: Boolean = false): RoutingHandler = get@{
            val id = call.parameters["id"] ?: error("id not set")
            
            if (logDB.exists(id)) {
                call.respondPathWithContentDisposition(logDB.file(id), inline)
                return@get
            }
            
            if (logDBArchived.exists(id)) {
                call.respondPathWithContentDisposition(logDBArchived.file(id), inline)
                return@get
            }
            
            log("log file not found: id=$id")
            call.respond(HttpStatusCode.NotFound)
        }
        get("download/{id}", downloadRoutingHandler())
        get("view/{id}", downloadRoutingHandler(true))
        
        post("archive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            
            if (!logDB.exists(id)) {
                log("log archive: file not found: $id")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            
            if (logDBArchived.exists(id)) {
                log("log archive: file already exists: $id")
                call.respond(HttpStatusCode.Conflict, "Archived file already exists")
                return@post
            }
            
            logDB.moveTo(logDBArchived, id)
            log("log archive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respond(HttpStatusCode.SeeOther)
        }
        
        post("unarchive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            
            if (!logDBArchived.exists(id)) {
                log("log unarchive: file not found: $id")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            
            if (logDB.exists(id)) {
                log("log unarchive: file already exists: $id")
                call.respond(HttpStatusCode.Conflict, "Unarchived file already exists")
                return@post
            }
            
            logDBArchived.moveTo(logDB, id)
            log("log unarchive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respond(HttpStatusCode.SeeOther)
        }
        
        post("delete/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            
            var deleted = false
            if (logDB.exists(id)) {
                log("log delete $id")
                logDB.delete(id)
                deleted = true
            }
            if (logDBArchived.exists(id)) {
                log("log delete archived $id")
                logDBArchived.delete(id)
                deleted = true
            }
            
            if (deleted) {
                call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
                call.respond(HttpStatusCode.SeeOther)
            } else {
                log("log delete: file not found: $id")
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private fun loadLogEntries(
    includeRegular: Boolean = false,
    includeArchived: Boolean = false,
): DataFrame<*> {
    val entries = mutableListOf<LogEntry>()
    
    if (includeRegular) {
        val regular = logDB.listAllFiles()
        entries.addAll(regular.map { parseLogPath(it, false) })
    }
    
    if (includeArchived) {
        val archived = logDBArchived.listAllFiles()
        entries.addAll(archived.map { parseLogPath(it, true) })
    }
    
    val dataFrame = entries.toDataFrame() //
        .add("view") {
            ActionColumnAction(
                "View",
                "view/${it.name}",
                "get",
            )
        }.add("download") {
            ActionColumnAction(
                "Download",
                "download/${it.name}",
                "get",
            )
        }.add("archive") {
            when (it.archived) {
                false -> ActionColumnAction("Archive", "archive/${it.name}", "post")
                true -> ActionColumnAction("Unarchive", "unarchive/${it.name}", "post")
            }
        }.add("delete") {
            ActionColumnAction("Delete", "delete/${it.name}", "post")
        }
    
    return dataFrame
}
