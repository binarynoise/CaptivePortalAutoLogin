plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
}

dependencies {
    api(projects.util.logger)
    api(libs.rhino)
    
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
