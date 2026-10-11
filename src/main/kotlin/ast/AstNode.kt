package org.jetbrains.edu.aads2026.ast

/**
 * Kind of a declaration in the high-level syntax tree.
 *
 * @property jsonName the value of the `type` field in JSON
 */
enum class NodeType(val jsonName: String) {
    FILE("file"),
    CLASS("class"),
    INTERFACE("interface"),
    OBJECT("object"),
    ENUM("enum"),
    FUNCTION("function"),
    PROPERTY("property"),
    TYPEALIAS("typealias"),
}

/**
 * A declaration in the high-level syntax tree of a source file. The root node of every file
 * has the type [NodeType.FILE] and the file name as its name.
 *
 * @property children nested declarations: members of a class, top-level declarations of a file
 * @property associations names of other types this declaration and its descendants refer to:
 *   supertypes, types of function parameters and results. Present only on top-level declarations
 *   (children of the [NodeType.FILE] node); nested declarations have no associations of their own.
 *   Sorted and without duplicates.
 */
data class AstNode(
    val name: String,
    val type: NodeType,
    val children: List<AstNode> = emptyList(),
    val associations: List<String> = emptyList(),
) {
    /** Visits this node and all its descendants, parents before children. */
    fun walk(): Sequence<AstNode> = sequence {
        yield(this@AstNode)
        for (child in children) yieldAll(child.walk())
    }

    /**
     * Serializes the subtree to JSON, e.g.
     * ```
     * {"name": "SearchEngine", "type": "interface",
     *  "children": [{"name": "index", "type": "function"}, {"name": "search", "type": "function"}],
     *  "associations": ["CorpusTokenizer", "SearchResult"]}
     * ```
     * Empty `children` and `associations` are omitted.
     *
     * @param indent number of spaces per nesting level; 0 produces a single line.
     */
    fun toJson(indent: Int = 0): String = StringBuilder().also { writeJson(it, toJsonValue(), indent, 0) }.toString()

    private fun toJsonValue(): Map<String, Any> = buildMap {
        put("name", name)
        put("type", type.jsonName)
        if (children.isNotEmpty()) put("children", children.map { it.toJsonValue() })
        if (associations.isNotEmpty()) put("associations", associations)
    }
}

/** Writes a JSON value built of maps, lists and strings. */
private fun writeJson(out: StringBuilder, value: Any, indent: Int, level: Int) {
    fun newLine(level: Int) {
        if (indent > 0) out.append('\n').append(" ".repeat(indent * level))
    }

    fun <T> writeItems(open: Char, close: Char, items: Collection<T>, writeItem: (T) -> Unit) {
        out.append(open)
        items.forEachIndexed { i, item ->
            out.append(if (i > 0) "," else "")
            newLine(level + 1)
            writeItem(item)
        }
        newLine(level)
        out.append(close)
    }

    when (value) {
        is String -> out.appendJsonString(value)
        is List<*> -> writeItems('[', ']', value) { writeJson(out, it!!, indent, level + 1) }
        is Map<*, *> -> writeItems('{', '}', value.entries) { (key, item) ->
            out.appendJsonString(key as String)
            out.append(if (indent > 0) ": " else ":")
            writeJson(out, item!!, indent, level + 1)
        }
        else -> error("Unsupported JSON value: $value")
    }
}

private fun StringBuilder.appendJsonString(s: String) {
    append('"')
    for (c in s) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
