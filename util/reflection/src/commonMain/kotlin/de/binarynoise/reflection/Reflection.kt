package de.binarynoise.reflection

import java.lang.reflect.AccessibleObject

fun <T : AccessibleObject> T.makeAccessible(): T = apply { isAccessible = true }
