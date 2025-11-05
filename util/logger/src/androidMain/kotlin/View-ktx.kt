package de.binarynoise.logger

import android.view.View
import android.view.ViewGroup
import androidx.core.view.children

fun View.dump(indent: Int = 0) {
    platform.println(" ".repeat(indent * 2) + this)
    if (this is ViewGroup) {
        children.forEach {
            it.dump(indent + 1)
        }
    }
}
