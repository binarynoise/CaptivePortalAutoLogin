import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(projects.logger)
    implementation(projects.liberator)
    implementation(projects.api.client)
    compileOnly(libs.okhttp)
}

val mainClass = "de.binarynoise.captiveportalautologin.MainKt"
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
