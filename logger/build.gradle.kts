plugins {
    alias(libs.plugins.buildlogic.android.library)
    alias(libs.plugins.buildlogic.kotlin.multiplatform)
}

kotlin {
    jvm()
    androidTarget()
    
    
    @Suppress("unused") //
    sourceSets {
        val commonMain by getting {
            dependencies {}
        }
        
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.collection.ktx)
                implementation(libs.androidx.core.ktx)
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
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
}
