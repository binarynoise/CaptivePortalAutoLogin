@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.captiveportalautologin.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "::",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(json = Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        })
    }
    install(StatusPages) {
    
    }
    
    configureRouting()
}
