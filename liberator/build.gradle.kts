plugins {
    kotlin("android")
    id("com.android.library")
}

android {
    namespace = "de.binarynoise.captiveportalautologin.liberator"
    
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation(project(":logger"))
    
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")
    
}
