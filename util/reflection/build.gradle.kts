plugins {
    alias(libs.plugins.buildlogic.android.library)
    alias(libs.plugins.buildlogic.kotlin.multiplatform)
}

kotlin {
    jvm()
    androidTarget()
    
    sourceSets {
        commonMain {
            dependencies {}
        }
        
        androidMain {
            dependencies {
                implementation(libs.hiddenapibypass)
            }
        }
        
        jvmMain {
            dependencies {}
        }
    }
}

android {
    namespace = "de.binarynoise.reflection"
}
