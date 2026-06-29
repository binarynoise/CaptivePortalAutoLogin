package de.binarynoise.captiveportalautologin.server.routes

import de.binarynoise.captiveportalautologin.server.routes.api.api
import de.binarynoise.captiveportalautologin.server.routes.stats.stats
import de.binarynoise.logger.Logger.log
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.routing


fun Application.configureRouting() {
    val routes = routing {
        staticResources("static", "static")
        
        api()
        stats()
        
        if (!developmentMode) {
            get("/") {
                call.response.header("Location", "https://github.com/binarynoise/CaptivePortalAutoLogin")
                call.respondStatus(HttpStatusCode.TemporaryRedirect)
            }
        } else {
            get("/favicon.ico") {
                call.respondStatus(HttpStatusCode.NoContent)
            }
        }
    }
    
    // Log all registered routes
    routes.getAllRoutes().forEach { route ->
        log(route.toLogString())
    }
}
