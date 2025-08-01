plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.logger)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.datetime)
}

tasks.test {
    useJUnitPlatform()
}
