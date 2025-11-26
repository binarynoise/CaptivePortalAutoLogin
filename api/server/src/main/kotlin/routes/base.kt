package de.binarynoise.captiveportalautologin.server.routes

import de.binarynoise.captiveportalautologin.server.routes.api.api
import de.binarynoise.captiveportalautologin.server.routes.stats.stats
import de.binarynoise.logger.Logger.log
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    val routes = routing {
        staticResources("/static", "static")
        
        api()
        stats()
    }
    
    // Log all registered routes
    routes.getAllRoutes().forEach { route ->
        log(route.toLogString())
    }
}
