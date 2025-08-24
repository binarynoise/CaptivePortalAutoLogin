import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.shadow)
}


dependencies {
    implementation(projects.api)
    implementation(projects.fileDB)
    implementation(projects.logger)
    
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.mustache)
    implementation(libs.slf4j.simple)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.kotlinx.datetime)
    implementation(libs.sqlite.jdbc)
}

tasks.test {
    useJUnitPlatform()
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
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.ktor.serialization.kotlinx.json.get()))
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
