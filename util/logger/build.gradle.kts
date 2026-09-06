plugins {
    alias(libs.plugins.buildlogic.android.kotlin.multiplatform.library)
    alias(libs.plugins.buildlogic.kotlin.multiplatform)
}

kotlin {
    jvm()
    android {
        namespace = "de.binarynoise.logger"
        enableCoreLibraryDesugaring = true
    }
    
    sourceSets {
        commonMain {
            dependencies {
                compileOnly(libs.kotlinx.serialization.json)
            }
        }
        
        androidMain {
            dependencies {
                compileOnly(libs.androidx.collection.ktx)
                compileOnly(libs.androidx.core.ktx)
                
                compileOnly(libs.xposed.api)
            }
        }
        
        jvmMain {
            dependencies {}
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
}
