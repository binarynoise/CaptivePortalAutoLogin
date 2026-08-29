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

const val redirectorPackage = "$liberatorPackage.redirectors"
private const val PortalRedirectorFqn = "$liberatorPackage.PortalRedirector"

class PortalRedirectorProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    
    private val logger = environment.logger
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    
    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val candidates = resolver.getDeclarationsFromPackage(portalPackage)
            .plus(resolver.getDeclarationsFromPackage(redirectorPackage))
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.OBJECT }
            .filter { !it.modifiers.contains(Modifier.PRIVATE) }
            .filter { it.implements(PortalRedirectorFqn) }
            .toList()
        
        generateList(candidates)
        
        return emptyList()
    }
    
    private fun generateList(objects: List<KSClassDeclaration>) {
        val fileName = "GeneratedPortalRedirectors"
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
            writer.appendLine("import $liberatorPackage.PortalRedirector")
            writer.appendLine()
            writer.appendLine("val allPortalRedirectors: List<PortalRedirector> = listOf(")
            
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
