plugins {
    alias(libs.plugins.buildlogic.android.library)
    alias(libs.plugins.buildlogic.kotlin.multiplatform)
}

kotlin {
    jvm()
    androidTarget()
    
    
    @Suppress("unused", "RedundantSuppression") //
    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly(libs.kotlinx.serialization.json)
            }
        }
        
        val androidMain by getting {
            dependencies {
                compileOnly(libs.androidx.collection.ktx)
                compileOnly(libs.androidx.core.ktx)
                
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
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
}
