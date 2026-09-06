plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.buildlogic.jvm.test)
}

dependencies {
    api(projects.util.logger)
    api(libs.rhino)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
