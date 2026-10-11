package org.jetbrains.edu.aads2026.search

import org.jetbrains.edu.aads2026.tokenizer.CodeAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnippetTest {
    private val analyzer = CodeAnalyzer()

    private fun snippet(text: String, query: String, maxLength: Int = 160) =
        buildSnippet(text, analyzer, analyzer.queryTerms(query), maxLength)

    private fun Snippet.marked() = buildString {
        var last = 0
        for (range in highlights) {
            append(text, last, range.first).append('[').append(text, range.first, range.last + 1).append(']')
            last = range.last + 1
        }
        append(text, last, text.length)
    }

    @Test
    fun `picks the line with the most distinct terms`() {
        val text = """
            |fun search() {
            |    // search, search, search
            |  val engine = SearchEngine()   
            |}
        """.trimMargin()
        val snippet = snippet(text, "search engine")!!
        assertEquals(3, snippet.line)
        assertEquals("val [engine] = [SearchEngine]()", snippet.marked())
    }

    @Test
    fun `prefers more matches, then earlier lines`() {
        assertEquals(2, snippet("a search\nsearch search\nsearch search", "search")!!.line)
    }

    @Test
    fun `cuts long lines around the first match`() {
        val text = "x".repeat(100) + " needle " + "y".repeat(100)
        assertEquals("…" + "x".repeat(4) + " [needle] " + "y".repeat(8) + "…", snippet(text, "needle", maxLength = 20)!!.marked())
    }

    @Test
    fun `returns null without matches`() {
        assertNull(snippet("nothing here", "needle"))
    }
}
