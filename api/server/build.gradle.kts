plugins {
    application
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.12"
//    id("com.google.devtools.ksp") version "2.0.0-1.0.23"
}

application {
    mainClass.set("de.binarynoise.captiveportalautologin.server.ApplicationKt")
}

dependencies {
    api(project(":api"))
    api(project(":fileDB"))
    
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")

//    ksp("me.tatarka.inject:kotlin-inject-compiler-ksp:0.7.1")
//    implementation("me.tatarka.inject:kotlin-inject-runtime:0.7.1")

}

tasks.test {
    useJUnitPlatform()
}
