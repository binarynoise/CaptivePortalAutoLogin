package de.binarynoise.liberator.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

class PortalLiberatorProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    
    private val logger = environment.logger
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    
    private val liberatorPackage = "de.binarynoise.liberator"
    private val portalPackage = "$liberatorPackage.portals"
    private val PortalLiberatorFqn = "$liberatorPackage.PortalLiberator"
    
    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getDeclarationsFromPackage(portalPackage)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.OBJECT }
            .filter { !it.modifiers.contains(Modifier.PRIVATE) }
            .filter { it.implements(PortalLiberatorFqn) }
            .toList()
        
        generateList(candidates)
        
        return emptyList()
    }
    
    private fun KSClassDeclaration.implements(interfaceName: String): Boolean {
        return superTypes.any { superType ->
            val resolved = superType.resolve()
            val qName = resolved.declaration.qualifiedName?.asString()
            qName == interfaceName
        }
    }
    
    private fun generateList(objects: List<KSClassDeclaration>) {
        val fileName = "GeneratedPortalLiberators"
        val pkg = portalPackage
        
        val dependencies =
            Dependencies(aggregating = true, sources = objects.mapNotNull { it.containingFile }.toTypedArray())
        val output = try {
            codeGenerator.createNewFile(dependencies, pkg, fileName)
        } catch (_: FileAlreadyExistsException) {
            // Another round already created it; skip
            return
        }
        
        output.writer().use { writer ->
            writer.appendLine("package $pkg")
            writer.appendLine()
            writer.appendLine("import $liberatorPackage.PortalLiberator")
            writer.appendLine()
            writer.appendLine("val allPortalLiberators: List<PortalLiberator> = listOf(")
            
            val map = objects.associateWith { it.qualifiedName?.asString() }
            map.filterValues { v -> v != null }.values.forEach { v ->
                writer.appendLine("    $v,")
            }
            map.filterValues { v -> v == null }.keys.forEach { classDeclaration ->
                logger.warn("object without qualifiedName", classDeclaration)
            }
            
            writer.appendLine(")")
        }
    }
}
