package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import de.binarynoise.logger.Logger.log

/**
 * Drives the user interface of the app, mostly the onboarding and the advanced settings.
 *
 * The test does not depend on the layout details, it looks for the resource ids and the strings the app
 * uses in its own resources, see `app/src/main/res/values/strings.xml`.
 */
class AppUiFlow(private val adb: Adb, private val logDirectory: Path) {
    
    fun dumpUi(): DeviceUi = retryValue(30.seconds, description = "uiautomator to report the screen") {
        // `uiautomator dump` writes the XML into this file, its own output is not usable as XML
        adb.execOut("uiautomator dump $remoteDumpFile", logDirectory.resolve("uiautomator-stdout.txt"))
        val uiFile = adb.execOut("cat $remoteDumpFile", logDirectory.resolve("window_dump.xml"))
        DeviceUi.parse(uiFile.toFile().readText())
    }
    
    fun tap(node: UiNode) {
        adb.shell("input tap ${node.centerX} ${node.centerY}")
        Thread.sleep(settleDelay.inWholeMilliseconds)
    }
    
    fun pressBack() {
        adb.shell("input keyevent KEYCODE_BACK")
        Thread.sleep(settleDelay.inWholeMilliseconds)
    }
    
    /**
     * Walks through the onboarding, granting every permission it asks for.
     *
     * The last screen starts the foreground service, which then liberates the portal on its own.
     */
    fun completeOnboarding() {
        val deadline = System.currentTimeMillis() + onboardingTimeout.inWholeMilliseconds
        var lastScreen = ""
        while (System.currentTimeMillis() < deadline) {
            // an app that crashes on startup never shows a screen again, waiting for the timeout would only hide
            // the crash that caused it
            failIfAppDied()
            val ui = dumpUi()
            lastScreen = ui.describe()
            if (isShowingMainScreen(ui)) return
            // the device locks itself during a long onboarding, which would make the test tap a keyguard
            if (ui.isShowingKeyguard()) {
                lastScreen = ui.describe()
                adb.wakeUpAndDismissKeyguard()
                continue
            }
            if (!handleSystemPermissionScreen(ui) && !handleAppScreen(ui)) {
                lastScreen = ui.describe()
                Thread.sleep(pollInterval.inWholeMilliseconds)
            }
        }
        error("the onboarding did not finish within $onboardingTimeout:\n$lastScreen")
    }
    
    private fun failIfAppDied() {
        if (isAppRunning()) return
        val crashLog = adb.crashLogDump()
        error(
            "the app is not running any more, the last crash was:\n" + crashLog.takeLast(crashLogDetailLength)
                .ifBlank { "nothing in the crash buffer" },
        )
    }
    
    private fun isAppRunning(): Boolean {
        val packageName = TestEnvironment.appPackageName
        if (adb.shellOrNull("pidof $packageName")?.isNotBlank() == true) return true
        return adb.shellOrNull("ps -A | grep $packageName | grep -v grep").orEmpty().isNotBlank()
    }
    
    fun openAdvancedSettings() {
        retry(60.seconds, description = "the advanced settings to open") {
            val ui = dumpUi()
            val entry = ui.find { node -> node.text == advancedSettingsTitle } ?: return@retry false
            tap(entry)
            // the main screen is gone once the preferences are open
            !isShowingMainScreen(dumpUi())
        }
    }
    
    /**
     * Release builds keep the experimental portal liberators off by default, and the portal of this test
     * can only be solved by one of them.
     */
    fun enableExperimentalPortalLiberators() {
        val preference = findPreference(experimentalLiberatorsTitle)
        val widget =
            preference.enclosingRow()?.widget() ?: error("the $experimentalLiberatorsTitle preference has no switch")
        if (!widget.checked) tap(preference)
    }
    
    fun liberateNow() {
        tap(findPreference(liberateNowTitle))
    }
    
    private fun findPreference(title: String): UiNode =
        scrollUntilFound("the $title preference") { node -> node.text == title }
            ?: error("the $title preference is not on the advanced settings screen")
    
    /**
     * Scrolls down until a node matching the predicate shows up, `uiautomator` only reports what is visible.
     *
     * The search starts from the top, because a caller can have scrolled past what it is looking for.
     */
    private fun scrollUntilFound(description: String, predicate: (UiNode) -> Boolean): UiNode? {
        val (width, height) = screenSize()
        dumpUi().find(predicate)?.let { return it }
        scrollToTop(width, height)
        var lastScreen = ""
        repeat(maxScrolls) {
            val ui = dumpUi()
            ui.find(predicate)?.let { return it }
            lastScreen = ui.describe()
            swipe(width, height, upward = true)
        }
        log("could not find $description by scrolling, last screen:\n$lastScreen")
        return null
    }
    
    private fun scrollToTop(width: Int, height: Int) {
        var previous: String? = null
        repeat(maxScrolls) {
            // the full text is compared, the truncated log form could report different screens as equal
            val ui = dumpUi().describe(maxTextLength = Int.MAX_VALUE)
            if (ui == previous) return
            previous = ui
            swipe(width, height, upward = false)
        }
    }
    
    private fun swipe(width: Int, height: Int, upward: Boolean) {
        val (fromY, toY) = if (upward) height * 3 / 4 to height / 4 else height / 4 to height * 3 / 4
        adb.shell("input swipe ${width / 2} $fromY ${width / 2} $toY 200")
        Thread.sleep(settleDelay.inWholeMilliseconds)
    }
    
    private fun screenSize(): Pair<Int, Int> {
        val size = adb.shellOrNull("wm size")
            ?.lineSequence()
            ?.firstNotNullOfOrNull { line -> Regex("""(\d+)x(\d+)""").find(line)?.groupValues }
            ?: error("could not read the screen size")
        return size[1].toInt() to size[2].toInt()
    }
    
    /**
     * Taps the schedule button of `QueuedWorkActivity`, which uploads the statistics the app has queued
     * instead of waiting for its hourly job. Only a debug build ships that activity.
     */
    fun uploadQueuedStatistics() {
        retry(60.seconds, description = "the schedule button of the queued work screen") {
            val button = dumpUi().findByResourceIdSuffix("id/schedule_button") ?: return@retry false
            tap(button)
            true
        }
    }
    
    private fun isShowingMainScreen(ui: DeviceUi): Boolean =
        ui.findByText(mainScreenTitle) != null && ui.findByText(advancedSettingsTitle) != null
    
    /**
     * Answers a runtime permission dialog, returns false when there is none.
     */
    private fun handleSystemPermissionScreen(ui: DeviceUi): Boolean {
        ui.findByResourceIdSuffix("permission_allow_button")?.let {
            tap(it)
            return true
        }
        ui.findByResourceIdSuffix("allow_always_radio_button")?.let {
            tap(it)
            pressBack()
            return true
        }
        ui.findByResourceIdSuffix("permission_allow_foreground_only_button")?.let {
            tap(it)
            return true
        }
        return false
    }
    
    /**
     * Presses the next button of the onboarding screens and grants the permissions it lists.
     */
    private fun handleAppScreen(ui: DeviceUi): Boolean {
        val nextButton = ui.findByResourceIdSuffix("id/fab")
        if (nextButton != null) {
            tap(nextButton)
            return true
        }
        
        val title = locationPermissionTitles.firstOrNull { ui.hasUngrantedPermissionRow(it) }
        // the screen can change between the check and this lookup, in which case there is nothing to tap
        val row = title?.let { text -> ui.find { node -> node.text == text } } ?: return false
        tap(row)
        return true
    }
    
    private companion object {
        val onboardingTimeout = 5.minutes
        val settleDelay = 500.milliseconds
        val pollInterval = 500.milliseconds
        const val maxScrolls = 12
        const val crashLogDetailLength = 4000
        
        const val remoteDumpFile = "/sdcard/window_dump.xml"
        
        const val mainScreenTitle = "Status"
        const val advancedSettingsTitle = "Advanced Settings"
        const val liberateNowTitle = "Liberate me now"
        const val experimentalLiberatorsTitle = "Enable Experimental PortalLiberators"
        
        val locationPermissionTitles = listOf("Fine Location", "Background Location")
    }
}
