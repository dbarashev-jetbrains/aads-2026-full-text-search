package org.jetbrains.edu.aads2026.search

import org.jetbrains.edu.aads2026.ast.CorpusStructure
import org.jetbrains.edu.aads2026.tokenizer.CodeAnalyzer
import org.jetbrains.edu.aads2026.tokenizer.DefaultCorpusTokenizer
import org.jetbrains.edu.aads2026.tokenizer.Document
import org.jetbrains.edu.aads2026.tokenizer.DocumentSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleSearchEngineTest {
    private class TextDocument(override val id: Int, override val name: String, val text: String) : Document {
        override fun readText() = text
    }

    private val source = object : DocumentSource {
        override fun documents() = sequenceOf(
            TextDocument(0, "a.kt", "fun parseRequest()\n  return headers"),
            TextDocument(1, "b.kt", "val request = 1"),
            TextDocument(2, "c.kt", "// parsing http requests"),
        )
    }

    private fun engine() = CodeAnalyzer().let { analyzer ->
        SimpleSearchEngine(analyzer).apply {
            index(DefaultCorpusTokenizer(source, analyzer), CorpusStructure { emptySequence() })
        }
    }

    @Test
    fun `finds documents containing all query terms`() {
        val engine = engine()
        assertEquals(listOf("a.kt", "b.kt", "c.kt"), engine.search("requests").map { it.document.name }.toList())
        assertEquals(listOf("a.kt", "c.kt"), engine.search("parse request").map { it.document.name }.toList())
        assertEquals(listOf("a.kt"), engine.search("parsed headers").map { it.document.name }.toList())
        assertEquals(emptyList(), engine.search("missing").toList())
        assertEquals(emptyList(), engine.search("  ").toList())
    }

    @Test
    fun `builds snippets from the most relevant lines`() {
        val engine = engine()
        assertEquals(Snippet(2, "return headers", listOf(0..5, 7..13)), engine.search("headers return").single().snippet)
        assertEquals(
            listOf(Snippet(1, "fun parseRequest()", listOf(4..15)), Snippet(1, "// parsing http requests", listOf(3..9, 16..23))),
            engine.search("parse request").map { it.snippet }.toList(),
        )
    }
}
