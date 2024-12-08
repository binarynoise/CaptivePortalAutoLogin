plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
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
                implementation(libs.collection.ktx)
                implementation(libs.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                
                compileOnly(libs.xposed.api)
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
