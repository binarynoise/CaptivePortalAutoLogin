package de.binarynoise.captiveportalautologin.server.routes.stats

import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Routing.stats() {
    get("stats") {
        call.response.header("Location", "stats/")
        call.respondStatus(HttpStatusCode.MovedPermanently)
    }
    
    route("stats/") {
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
        logRoutes()
    }
}
