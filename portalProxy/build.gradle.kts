plugins {
    application
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.buildlogic.shadow)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.util.logger)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.html)
    implementation(libs.slf4j.simple)
    implementation(platform(libs.vertx.bom))
    implementation(platform(libs.netty.bom))
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
