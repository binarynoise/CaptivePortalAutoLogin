plugins {
    alias(libs.plugins.buildlogic.android.kotlin.multiplatform.library)
    alias(libs.plugins.buildlogic.kotlin.multiplatform)
}

kotlin {
    jvm()
    android {
        namespace = "de.binarynoise.reflection"
    }
    
    @Suppress("unused", "RedundantSuppression") sourceSets {
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
