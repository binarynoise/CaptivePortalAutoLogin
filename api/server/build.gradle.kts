import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.shadow)
}


dependencies {
    implementation(projects.api)
    implementation(projects.util.fileDB)
    implementation(projects.util.logger)
    
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.mustache)
    implementation(libs.slf4j.simple)
    
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.sqlite.bundled)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}


room {
    schemaDirectory("$projectDir/schemas")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val mainClass = "de.binarynoise.captiveportalautologin.server.MainKt"
application.mainClass = mainClass
tasks.withType<Jar> {
    manifest {
        attributes(mapOf("Main-Class" to mainClass))
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("shadow")
    mergeServiceFiles()
    minimize {
        exclude(dependency(libs.ktor.serialization.kotlinx.json.get()))
        exclude(dependency(libs.slf4j.simple.get()))
    }
    exclude(
        "**/*.kotlin_*",
        "**/*.pro",
        "/*/default/linkdata/",
        "/*/default/manifest",
        "/DebugProbesKt.bin",
        "/META-INF/native-image/",
        "/META-INF/maven/"
    )
}
