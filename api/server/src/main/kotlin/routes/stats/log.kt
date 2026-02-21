package de.binarynoise.captiveportalautologin.server.routes.stats

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import de.binarynoise.captiveportalautologin.api.json.LOG
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.logger.Logger
import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.logRoutes() {
    get("log") {
        call.response.header("Location", "log/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("log/") {
        get {
            val names = ApiServer.api.jsonDb.listAll<LOG>("log")
            val entries = names.map { name ->
                val log = ApiServer.api.jsonDb.load<LOG>(name, "log")
                val timestamp = log.timestamp
                val version = log.version
                mapOf(
                    "name" to name,
                    "timestamp" to timestamp,
                    "version" to version,
                    "view" to "view/$name.log",
                    "download" to "download/$name.log",
                    "archiveAction" to "archive/$name.log",
                )
            }.sortedByDescending { it["timestamp"] as String }
            
            call.respond(
                MustacheContent(
                    "logs.mustache", mapOf(
                        "title" to "Log Files",
                        "backLink" to "../",
                        "entries" to entries,
                    )
                )
            )
        }
        
        fun downloadRoutingHandler(inline: Boolean = false): RoutingHandler = get@{
            val id = call.parameters["id"] ?: error("id not set")
            
            val path = ApiServer.api.jsonDb.base<LOG>().resolve(id)
            if (!path.exists()) {
                Logger.log("file not found: $path")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val log = ApiServer.api.jsonDb.load<LOG>(id.removeSuffix(".log"), "log")
            val contentDispositionBase = if (inline) ContentDisposition.Inline else ContentDisposition.Attachment
            call.response.header(
                HttpHeaders.ContentDisposition,
                contentDispositionBase.withParameter(ContentDisposition.Parameters.FileName, log.name).toString(),
            )
            call.respondText(log.content)
        }
        get("download/{id}", downloadRoutingHandler())
        get("view/{id}", downloadRoutingHandler(true))
        
        post("archive/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            val base = ApiServer.api.jsonDb.base<LOG>()
            val src = base.resolve(id)
            if (!src.exists()) {
                Logger.log("file not found: $src")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val archiveDir = base.resolve("archived").apply { createDirectories() }
            var dest = archiveDir.resolve(id)
            if (dest.exists()) {
                val alt = "$id-archived-${System.currentTimeMillis()}"
                dest = archiveDir.resolve(alt)
            }
            src.moveTo(dest)
            call.response.header("Location", "../")
            call.respond(HttpStatusCode.SeeOther)
        }
    }
}
