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
                implementation("androidx.collection:collection-ktx:1.4.2")
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
//    compileOptions {
//        isCoreLibraryDesugaringEnabled = true
//    }
}

//dependencies {
//    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
//}
