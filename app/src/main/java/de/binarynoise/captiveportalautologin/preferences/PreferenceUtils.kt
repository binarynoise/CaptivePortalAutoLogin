package de.binarynoise.captiveportalautologin.preferences

import android.content.Context
import android.view.View
import androidx.annotation.LayoutRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder

//fun Preference.PreferenceIcon(icon: IIcon): Drawable {
//    return IconicsDrawable(context, icon).apply {
//        sizeDp = 36
//        colorInt = TypedValue().apply { context.theme.resolveAttribute(R.attr.colorOnSurface, this, true) }.data
//        alpha = 150
//    }
//}

fun PreferenceGroup.setIconSpaceReservedRecursively(iconSpaceReserved: Boolean) {
    isIconSpaceReserved = iconSpaceReserved
    for (i in 0 until preferenceCount) {
        val preference = getPreference(i)
        preference.isIconSpaceReserved = iconSpaceReserved
        if (preference is PreferenceGroup) preference.setIconSpaceReservedRecursively(iconSpaceReserved)
    }
}

fun PreferenceGroup.removeOnClickListenersRecursively() {
    onPreferenceClickListener = null
    for (i in 0 until preferenceCount) {
        val preference = getPreference(i)
        preference.onPreferenceClickListener = null
        if (preference is PreferenceGroup) preference.removeOnClickListenersRecursively()
    }
}

inline fun <T : Preference> PreferenceGroup.addPreference(preference: T, setup: T.() -> Unit) {
    if (preference is PreferenceGroup) {
        // PreferenceGroup needs to be added to the tree before other prefrences can be added to it
        addPreference(preference)
        preference.apply(setup)
    } else {
        // normal preferences need the setup applied before being added to the tree
        preference.apply(setup)
        addPreference(preference)
    }
}

abstract class AutoCleanupPreferenceFragment : PreferenceFragmentCompat() {
    override fun onDestroy() {
        preferenceScreen.removeOnClickListenersRecursively()
        preferenceScreen.removeAll()
        super.onDestroy()
    }
}

open class ViewHolderPreference(ctx: Context, @LayoutRes layout: Int? = null) : Preference(ctx) {
    init {
        if (layout != null) {
            layoutResource = layout
        }
    }
    
    var onBindViewHolder: ((View) -> Unit)? = null
    fun setOnBindViewHolderListener(setup: (View) -> Unit) {
        onBindViewHolder = setup
    }
    
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        onBindViewHolder?.invoke(holder.itemView)
    }
}

class WidgetPreference(ctx: Context, @LayoutRes layout: Int, val setup: (View) -> Unit) : ViewHolderPreference(ctx) {
    init {
        widgetLayoutResource = layout
        setOnBindViewHolderListener(setup)
    }
}

var Preference.titleRes: Int
    get() = 0
    set(value) = this.setTitle(value)
