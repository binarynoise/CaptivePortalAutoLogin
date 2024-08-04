plugins {
    kotlin("jvm")
    
}

dependencies {
    api(project(":api"))
    implementation(project(":util:okhttp-kts"))
    
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    
    testImplementation(kotlin("test"))
    testImplementation(project(":api:server"))
//    testImplementation(project(":jsonDB"))
    testImplementation(platform("io.ktor:ktor-bom:2.3.12"))
    testImplementation("io.ktor:ktor-server-core-jvm")
    testImplementation("io.ktor:ktor-server-netty-jvm")
    testImplementation("io.ktor:ktor-server-status-pages")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    
}

tasks.test {
    useJUnitPlatform()
}
