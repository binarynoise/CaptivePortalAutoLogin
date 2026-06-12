package de.binarynoise.liberator.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration

fun KSClassDeclaration.implements(interfaceName: String): Boolean {
    return superTypes.any { superType ->
        val resolved = superType.resolve()
        val declaration = resolved.declaration
        val qName = declaration.qualifiedName?.asString()
        if (qName == interfaceName) return@any true
        if (declaration is KSClassDeclaration) {
            return@any declaration.implements(interfaceName)
        }
        return@any false
    }
}
