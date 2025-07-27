import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.shadow)
}


dependencies {
    // api to pass dependencies through to client tests
    api(projects.api)
    api(projects.fileDB)
    
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.slf4j.simple)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
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
    minimize()
    exclude("**/*.kotlin_*")
}
