import java.util.Properties

plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
}

dependencies {
    testImplementation(projects.api)
    testImplementation(projects.api.server)
    testImplementation(projects.portalProxy)
    testImplementation(projects.util.logger)
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(platform(libs.ktor.bom))
    testImplementation(libs.ktor.server.core.jvm)
    testImplementation(libs.androidx.room.runtime)
    // SuccessEntity is annotated for the dataframe library
    testImplementation(libs.kotlinx.dataframe)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val apiServerPort = 18080
val deviceApiServerPort = 8080

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { load(it) }
}
val androidSdkDirectory =
    localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("no Android SDK found, set sdk.dir in local.properties or the ANDROID_HOME environment variable")

val appBuildDirectory = project(":app").layout.buildDirectory.get().asFile

val testSourceSet = sourceSets["test"]

// the integration tests need emulators and take a long time, the unit tests below run in `test`
tasks.named<Test>("test") {
    useJUnitPlatform()
    filter {
        excludeTestsMatching("*IntegrationTest")
    }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Installs the app on every available emulator and checks that it liberates a captive portal"
    
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    filter {
        includeTestsMatching("*IntegrationTest")
    }
    
    dependsOn(":app:assembleUniversalDebug")
    dependsOn(":app:assembleUniversalRelease")
    
    systemProperty("captivePortalAutoLogin.projectDirectory", rootProject.projectDir.absolutePath)
    systemProperty("captivePortalAutoLogin.androidSdkDirectory", androidSdkDirectory)
    systemProperty("captivePortalAutoLogin.appBuildDirectory", appBuildDirectory.absolutePath)
    systemProperty("captivePortalAutoLogin.deviceApiServerPort", deviceApiServerPort.toString())
    systemProperty("captivePortalAutoLogin.apiServerPort", apiServerPort.toString())
    
    fun forwardProperty(gradleName: String, systemName: String) {
        providers.gradleProperty(gradleName).orNull?.let { systemProperty(systemName, it) }
    }
    // run a subset of the emulators with -Pavds=, leave one out with -PskipAvds=,
    forwardProperty("avds", "captivePortalAutoLogin.avds")
    forwardProperty("skipAvds", "captivePortalAutoLogin.skipAvds")
    // run a subset of the build types with -PbuildTypes=debug
    forwardProperty("buildTypes", "captivePortalAutoLogin.buildTypes")
    
    useJUnitPlatform()
    maxHeapSize = "1g"
    
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}
