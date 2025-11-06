package de.binarynoise.rhino

import org.mozilla.javascript.Token
import org.mozilla.javascript.ast.AstNode
import org.mozilla.javascript.ast.Name
import org.mozilla.javascript.ast.NodeVisitor
import org.mozilla.javascript.ast.NumberLiteral
import org.mozilla.javascript.ast.StringLiteral

class DebugPrintVisitor : NodeVisitor {
    private val buffer = StringBuilder()
    
    override fun visit(node: AstNode): Boolean = with(buffer) {
        val type = node.getType()
        val name = Token.typeToName(type)
        append(makeIndent(node.depth()))
        append(name).append(" ")
        append(node::class.simpleName).append(" ")
        when (node) {
            is Name -> append(node.identifier)
            is StringLiteral -> {
                append("\"")
                append(node.value)
                append("\"")
            }
            
            is NumberLiteral -> append(node.value)
        }
        append("\n")
        
        true // process children
    }
    
    override fun toString(): String = buffer.toString()
    
    private fun makeIndent(depth: Int): String = " ".repeat(2 * depth)
}
