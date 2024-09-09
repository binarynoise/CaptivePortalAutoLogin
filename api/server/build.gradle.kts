plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("de.binarynoise.captiveportalautologin.server.ApplicationKt")
}

dependencies {
    api(projects.api)
    api(projects.fileDB)
    
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
}

tasks.test {
    useJUnitPlatform()
}
