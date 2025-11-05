plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
}

dependencies {
    implementation(projects.util.logger)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    
    testImplementation(kotlin("test"))
}
