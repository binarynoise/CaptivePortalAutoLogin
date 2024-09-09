plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.logger)
    implementation(projects.util.okhttpKts)
    
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.json)
    
}
