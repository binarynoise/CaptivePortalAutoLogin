plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    
    testImplementation(kotlin("test"))
}
