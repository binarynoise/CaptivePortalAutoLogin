import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(projects.util.logger)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.html)
    implementation(libs.slf4j.simple)
    implementation(libs.vertx.lang.kotlin)
    implementation(libs.vertx.lang.kotlin.coroutines)
    implementation(libs.vertx.web)
    runtimeOnly(libs.blockhound)
}

val mainClass = "de.binarynoise.captiveportalautologin.portalproxy.MainKt"
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
