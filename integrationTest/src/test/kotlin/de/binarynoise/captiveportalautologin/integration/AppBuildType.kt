package de.binarynoise.captiveportalautologin.integration

enum class AppBuildType(val gradleName: String, val displayName: String, val isDebuggable: Boolean) {
    DEBUG("debug", "debug build", true), RELEASE("release", "release build", false)
}
