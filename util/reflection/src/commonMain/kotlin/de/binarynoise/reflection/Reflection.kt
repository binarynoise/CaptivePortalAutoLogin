package de.binarynoise.reflection

import java.lang.reflect.Field

fun Field.makeAccessible(): Field = apply { isAccessible = true }
