package buildlogic

import com.android.build.api.dsl.Lint

internal fun Lint.applyCommonLint() {
    disable += "DiscouragedApi"
    disable += "ExpiredTargetSdkVersion"
    disable += "OldTargetApi"
    disable += "MissingApplicationIcon"
    disable += "UnusedAttribute"
}
