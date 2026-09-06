package buildlogic

import com.android.build.api.dsl.Lint

internal fun Lint.applyCommonLint() {
    disable += "DiscouragedApi"
    disable += "DiscouragedPrivateApi"
    disable += "ExpiredTargetSdkVersion"
    disable += "MissingApplicationIcon"
    disable += "OldTargetApi"
    disable += "UnusedAttribute"
}
