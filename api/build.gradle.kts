plugins {
    alias(libs.plugins.buildlogic.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
