package de.binarynoise.captiveportalautologin.preferences

import android.content.pm.LauncherApps
import android.content.pm.ShortcutManager
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.preference.Preference
import de.binarynoise.captiveportalautologin.util.getColorFromAttr


class DebugShortcutsFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        
        val shortcutManager = context.getSystemService<ShortcutManager>()!!
        val shortcuts = shortcutManager.manifestShortcuts
        
        val launcherApps = context.getSystemService<LauncherApps>()!!
        
        preferenceScreen = preferenceManager.createPreferenceScreen(context).apply {
            shortcuts.forEach { shortcut ->
                addPreference(Preference(context)) {
                    title = shortcut.shortLabel
                    summary = shortcut.longLabel
                    val shortcutIconDrawable = launcherApps.getShortcutIconDrawable(shortcut, 0)
                    val tintColor = context.getColorFromAttr(android.R.attr.textColorPrimary)
                    shortcutIconDrawable.setTint(tintColor)
                    icon = shortcutIconDrawable
                    setOnPreferenceClickListener {
                        startActivity(shortcut.intent!!)
                        true
                    }
                }
            }
        }
    }
}
