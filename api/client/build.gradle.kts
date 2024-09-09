plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(projects.api)
    implementation(projects.util.okhttpKts)
    
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    
    testImplementation(kotlin("test"))
    testImplementation(projects.api.server)
    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.server.core.jvm)
    testImplementation(libs.ktor.server.netty.jvm)
    testImplementation(libs.ktor.server.status.pages)
    testImplementation(libs.ktor.server.tests.jvm)
    
}

tasks.test {
    useJUnitPlatform()
}
