plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.util.logger)
    implementation(projects.util.okhttpKts)
    implementation(projects.util.rhino)
    
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.json)
    implementation(libs.kotlinx.serialization.json)

    // KSP processor for generating PortalLiberator list
    ksp(projects.liberator.processor)
}
