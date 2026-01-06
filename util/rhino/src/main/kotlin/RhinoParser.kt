package de.binarynoise.rhino

import org.mozilla.javascript.Node
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AbstractObjectProperty
import org.mozilla.javascript.ast.Assignment
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.ElementGet
import org.mozilla.javascript.ast.FunctionCall
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NumberLiteral
import org.mozilla.javascript.ast.ObjectLiteral
import org.mozilla.javascript.ast.ObjectProperty
import org.mozilla.javascript.ast.PropertyGet
import org.mozilla.javascript.ast.StringLiteral
import org.mozilla.javascript.ast.VariableInitializer

class RhinoParser(private val debug: Boolean = false) {
    
    fun parseAssignments(js: String): Map<String, String> {
        val parser = Parser()
        val ast = parser.parse(js, null, 0) ?: return emptyMap()
        
        if (debug) {
            println("AST Structure:")
            val debugPrintVisitor = DebugPrintVisitor()
            ast.visit(debugPrintVisitor)
            println(debugPrintVisitor)
            println("\nAssignments found:")
        }
        
        val assignments = mutableMapOf<String, String>()
        
        ast.visit { node ->
            when (node) {
                is VariableInitializer -> {
                    val name = (node.target as? Name)?.identifier ?: return@visit true
                    val value = getValueString(node.initializer, js)
                    if (debug) println("Found variable: $name = $value")
                    assignments[name] = value
                    
                    // Process object literals to extract their properties
                    if (node.initializer is ObjectLiteral) {
                        processObjectLiteral(node.initializer as ObjectLiteral, name, assignments, js)
                    }
                }
                
                is Assignment -> {
                    val pathSegments = buildPropertyPath(node.left)
                    if (pathSegments.isNotEmpty()) {
                        val value = getValueString(node.right, js)
                        val fullPath = pathSegments.joinToString(".")
                        if (debug) println("$fullPath = $value")
                        assignments[fullPath] = value
                        
                        // Process object literals in assignments
                        if (node.right is ObjectLiteral) {
                            processObjectLiteral(node.right as ObjectLiteral, fullPath, assignments, js)
                        }
                    }
                }
                
                is FunctionCall -> {
                    val name = getValueString(node.target, js)
                    val args = node.arguments.map { getValueString(it, js) }
                    if (debug) println("$name(${args.joinToString(", ")})")
                    args.forEachIndexed { i, arg ->
                        assignments["$name.$i"] = arg
                    }
                }
            }
            true
        }
        
        return assignments
    }
    
    private fun processObjectLiteral(
        obj: ObjectLiteral,
        parentPath: String,
        assignments: MutableMap<String, String>,
        js: String,
    ) {
        obj.elements.forEach { prop: AbstractObjectProperty ->
            prop as ObjectProperty
            val propName = getValueString(prop.key, js)
            val fullPath = "$parentPath.$propName"
            val value = getValueString(prop.value, js)
            if (debug) println("  $fullPath = $value")
            assignments[fullPath] = value
            
            // Recursively process nested object literals
            if (prop.value is ObjectLiteral) {
                processObjectLiteral(prop.value as ObjectLiteral, fullPath, assignments, js)
            }
        }
    }
    
    private fun buildPropertyPath(node: AstNode): List<String> {
        return when (node) {
            is Name -> listOf(node.identifier)
            is PropertyGet -> buildPropertyPath(node.target) + node.property.identifier
            is ElementGet -> {
                val base = buildPropertyPath(node.target)
                val key = when (val keyNode = node.element) {
                    is StringLiteral -> keyNode.value
                    is Name -> keyNode.identifier
                    is NumberLiteral -> keyNode.value
                    else -> {
                        if (debug) println("Unsupported dynamic key: ${keyNode::class.simpleName}")
                        return emptyList()
                    }
                }
                base + key
            }
            
            else -> emptyList()
        }
    }
    
    private fun getValueString(node: Node?, js: String): String = when (node) {
        null -> "null"
        is StringLiteral -> node.value
        is Name -> node.identifier
        is NumberLiteral -> node.value
        is ElementGet -> "${getValueString(node.target, js)}[${getValueString(node.element, js)}]"
        is AstNode -> {
            // Fallback: return the raw source code
            js.substring(node.absolutePosition, node.absolutePosition + node.length)
        }
        else -> "?"
    }
}
