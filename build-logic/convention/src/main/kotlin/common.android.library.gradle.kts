import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("common.android")
}

extensions.configure<LibraryExtension> {
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
    
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
