plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
}

dependencies {
    api(projects.util.logger)
    api(libs.rhino)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
