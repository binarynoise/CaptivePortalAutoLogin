plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("de.binarynoise.captiveportalautologin.server.ApplicationKt")
}

dependencies {
    // api to pass dependencies through to client tests
    api(projects.api)
    api(projects.fileDB)
    
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    
    api(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.sqlite.jdbc)
}

tasks.test {
    useJUnitPlatform()
}
