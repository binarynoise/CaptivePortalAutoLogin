package de.binarynoise.captiveportalautologin.server.routes.stats

import io.ktor.http.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.stats() {
    get("/stats") {
        call.response.header("Location", "/stats/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("/stats/") {
        get {
            call.respond(
                MustacheContent(
                    "home.mustache",
                    mapOf("title" to "Stats"),
                )
            )
        }
        
        successRoutes()
        errorRoutes()
        harRoutes()
    }
}
