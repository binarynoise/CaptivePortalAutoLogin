package de.binarynoise.captiveportalautologin.integration

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class UiNode(
    val packageName: String,
    val className: String,
    val text: String,
    val resourceId: String,
    val contentDescription: String,
    val checked: Boolean,
    val clickable: Boolean,
    val bounds: List<Int>,
    val children: List<UiNode>,
) {
    var parent: UiNode? = null
        internal set
    
    val centerX: Int get() = (bounds[0] + bounds[2]) / 2
    val centerY: Int get() = (bounds[1] + bounds[3]) / 2
    
    fun findAll(predicate: (UiNode) -> Boolean): List<UiNode> = buildList {
        if (predicate(this@UiNode)) add(this@UiNode)
        children.forEach { child -> addAll(child.findAll(predicate)) }
    }
    
    fun find(predicate: (UiNode) -> Boolean): UiNode? = findAll(predicate).firstOrNull()
    
    /**
     * The row of a preference, which contains the title and the widget showing its state.
     *
     * The nearest ancestor is preferred whose direct children hold the widget, so that a screen-level
     * container holding several switches is never mistaken for the row of this node.
     */
    fun enclosingRow(): UiNode? {
        val ancestors = generateSequence(parent) { it.parent }.toList()
        return ancestors.firstOrNull { candidate -> candidate.children.any { it.resourceId in widgetResourceIds } }
            ?: ancestors.firstOrNull { candidate -> candidate.find { node -> node.resourceId in widgetResourceIds } != null }
    }
    
    fun widget(): UiNode? = find { node -> node.resourceId in widgetResourceIds } ?: enclosingRow()?.find { node ->
        node.resourceId in widgetResourceIds
    }
    
    private companion object {
        val widgetResourceIds = listOf("android:id/checkbox", "android:id/switch_widget")
    }
}

/**
 * A snapshot of the screens on the device, as reported by `uiautomator dump`.
 */
class DeviceUi(private val roots: List<UiNode>) {
    
    fun findAll(predicate: (UiNode) -> Boolean): List<UiNode> = roots.flatMap { root -> root.findAll(predicate) }
    
    fun find(predicate: (UiNode) -> Boolean): UiNode? = findAll(predicate).firstOrNull()
    
    fun findByResourceIdSuffix(suffix: String): UiNode? = find { node -> node.resourceId.endsWith(suffix) }
    
    fun findByText(text: String): UiNode? = find { node -> node.text == text }
    
    /**
     * The packages owning the dumped windows, which tells the caller which app is on the screen.
     * Only the windows that are on the screen are part of the dump, so a covered app is not reported anymore.
     */
    fun packages(): List<String> = findAll { node -> node.packageName.isNotEmpty() }.map { it.packageName }.distinct()
    
    /**
     * Whether the device shows its lockscreen instead of the app.
     *
     * Only the system ui is on screen then, and it marks its lock screen elements with a keyguard class.
     */
    fun isShowingKeyguard(): Boolean = find { node ->
        node.packageName == "com.android.systemui" && node.className.substringAfterLast('.').contains("Keyguard")
    } != null
    
    fun hasUngrantedPermissionRow(title: String): Boolean = find { node ->
        node.text == title && node.resourceId.endsWith("android:id/title")
    }?.enclosingRow()?.widget()?.checked == false
    
    fun describe(maxTextLength: Int = 80): String = buildString {
        fun appendNode(node: UiNode, indent: String) {
            append(indent).append(node.className.substringAfterLast('.'))
            if (node.text.isNotEmpty()) append(" text=\"").append(node.text.take(maxTextLength)).append("\"")
            if (node.resourceId.isNotEmpty()) append(" id=").append(node.resourceId.substringAfterLast('/'))
            if (node.contentDescription.isNotEmpty()) append(" desc=").append(node.contentDescription)
            if (node.checked) append(" checked")
            appendLine()
            node.children.forEach { child -> appendNode(child, "$indent  ") }
        }
        roots.forEach { appendNode(it, "") }
    }
    
    companion object {
        private val boundsPattern = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
        
        fun parse(xml: String): DeviceUi {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray()))
            return DeviceUi(document.documentElement.childElements().mapNotNull { child -> parseNode(child) })
        }
        
        private fun Element.childElements(): List<Element> =
            (0 until childNodes.length).mapNotNull { index -> childNodes.item(index) as? Element }
        
        private fun parseNode(element: Element): UiNode? {
            val attributes = element.attributes
            fun attribute(name: String): String = attributes?.getNamedItem(name)?.nodeValue ?: ""
            val bounds =
                boundsPattern.find(attribute("bounds"))?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
            val children = element.childElements().mapNotNull { child -> parseNode(child) }
            val node = UiNode(
                packageName = attribute("package"),
                className = attribute("class"),
                text = attribute("text"),
                resourceId = attribute("resource-id"),
                contentDescription = attribute("content-desc"),
                checked = attribute("checked") == "true",
                clickable = attribute("clickable") == "true",
                bounds = bounds,
                children = children,
            )
            children.forEach { child -> child.parent = node }
            return node
        }
    }
}
