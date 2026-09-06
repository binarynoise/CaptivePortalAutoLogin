plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.buildlogic.jvm.test)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.util.logger)
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.datetime)
}
