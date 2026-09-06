import buildlogic.androidCompileSdk
import buildlogic.applyCommonLint
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("common.kotlin")
}

extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    finalizeDsl { dsl ->
        dsl.compileSdk = androidCompileSdk
        
        dsl.lint.applyCommonLint()
    }
}
