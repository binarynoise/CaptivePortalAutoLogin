plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    implementation(projects.logger)
    implementation(projects.util.okhttpKts)
    
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.json)
    
    // KSP processor for generating PortalLiberator list
    ksp(projects.liberator.processor)
}
