package de.binarynoise.captiveportalautologin.preferences

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.core.content.getSystemService
import androidx.core.view.get
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.children
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.ItemInlineEditTextPreferenceBinding

//fun Preference.PreferenceIcon(icon: IIcon): Drawable {
//    return IconicsDrawable(context, icon).apply {
//        sizeDp = 36
//        colorInt = TypedValue().apply { context.theme.resolveAttribute(R.attr.colorOnSurface, this, true) }.data
//        alpha = 150
//    }
//}

fun Preference.setIconSpaceReservedRecursively(iconSpaceReserved: Boolean) {
    isIconSpaceReserved = iconSpaceReserved
    if (this is PreferenceGroup) {
        children.forEach { preference ->
            preference.setIconSpaceReservedRecursively(iconSpaceReserved)
        }
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

@OptIn(ExperimentalContracts::class)
inline fun <T : Preference> PreferenceGroup.addPreference(preference: T, setup: T.() -> Unit) {
    contract {
        callsInPlace(setup, InvocationKind.EXACTLY_ONCE)
    }
    
    val isPreferenceGroup = preference is PreferenceGroup
    
    if (isPreferenceGroup) {
        // PreferenceGroup needs to be added to the tree before other preferences can be added to it
        addPreference(preference)
    }
    
    preference.apply(setup)
    
    if (!isPreferenceGroup) {
        // normal preferences need the setup applied before being added to the tree
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

open class ViewHolderPreference(
    ctx: Context,
    @LayoutRes layout: Int? = null,
) : Preference(ctx) {
    init {
        if (layout != null) {
            layoutResource = layout
        }
    }
    
    var onBindViewHolder: (Preference.(View) -> Unit)? = null
    fun setOnBindViewHolderListener(setup: Preference.(View) -> Unit) {
        onBindViewHolder = setup
    }
    
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        onBindViewHolder?.invoke(this, holder.itemView.findViewById<LinearLayout>(android.R.id.widget_frame)[0])
    }
}

class WidgetPreference(
    ctx: Context,
    @LayoutRes layout: Int,
    setup: Preference.(View) -> Unit,
) : ViewHolderPreference(ctx) {
    init {
        widgetLayoutResource = layout
        setOnBindViewHolderListener(setup)
    }
}

fun EditTextPreference(
    ctx: Context,
    defaultValue: String,
    hint: String? = null,
    onTextUpdated: (EditText, String) -> Unit,
): WidgetPreference = WidgetPreference(ctx, R.layout.item_inline_edit_text_preference) {
    val binding = ItemInlineEditTextPreferenceBinding.bind(it)
    with(binding) {
        editText.setText(defaultValue)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                e ?: return
                val s = e.toString()
                onTextUpdated(editText, s)
            }
        })
        editText.setOnClickListener { }
        setOnPreferenceClickListener { _ ->
            editText.requestFocus()
            context.getSystemService<InputMethodManager>()?.showSoftInput(editText, 0)
            true
        }
        editText.setHint(hint)
    }
}.apply {
    layoutResource = R.layout.preference_horizontal
}

var Preference.titleRes: Int
    get() = 0
    set(value) = this.setTitle(value)
