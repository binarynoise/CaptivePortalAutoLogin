plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
}

dependencies {
    api(projects.api)
    api(projects.util.okhttpKts)
    
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    
    testImplementation(projects.api.server)
    testImplementation(projects.util.fileDB)
    testImplementation(projects.util.logger)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.server.core.jvm)
    testImplementation(libs.ktor.server.netty.jvm)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
}

tasks.test {
    useJUnitPlatform()
}
