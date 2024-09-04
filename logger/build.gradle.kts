plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

kotlin {
    jvm()
    androidTarget()
    
    
    sourceSets {
        val commonMain by getting {
            dependencies {}
        }
        
        val androidMain by getting {
            dependencies {
                //add Android-specific dependencies here
                implementation("androidx.collection:collection-ktx:1.4.3")
                implementation("androidx.core:core-ktx:1.13.1")
            }
        }
        
        val jvmMain by getting {
            dependencies {}
        }
    }
}


android {
    namespace = "de.binarynoise.logger"
    
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
