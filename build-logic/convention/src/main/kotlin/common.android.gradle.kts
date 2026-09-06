import buildlogic.androidCompileSdk
import buildlogic.applyCommonLint
import buildlogic.applyJavaCompatibility
import buildlogic.libs
import com.android.build.api.dsl.CommonExtension

extensions.configure<CommonExtension> {
    compileSdk = androidCompileSdk
    
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    
    lint.applyCommonLint()
    
    compileOptions.applyJavaCompatibility()
}

dependencies {
    add("implementation", libs.androidx.annotation)
}
