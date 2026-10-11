package org.jetbrains.edu.aads2026.ast

import org.jetbrains.edu.aads2026.tokenizer.Document
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlinStructureExtractorTest {
    private val extractor = KotlinStructureExtractor()

    @AfterTest
    fun tearDown() = extractor.close()

    private fun document(name: String) = object : Document {
        override val id = 0
        override val name = name
        override fun readText() = error("not used")
    }

    private fun extract(text: String) = extractor.extract(document("Test.kt"), text)!!

    @Test
    fun `extracts declarations and hoists associations to top-level declarations`() {
        val root = extract(
            """
            package test

            interface SearchEngine {
                fun index(corpus: CorpusTokenizer, structure: CorpusStructure)
                fun search(query: String): Sequence<SearchResult>
            }

            class SimpleSearchEngine(private val analyzer: Analyzer) : SearchEngine, AutoCloseable {
                private var cache: Map<String, Index>? = null
                override fun index(corpus: CorpusTokenizer, structure: CorpusStructure) {
                    fun local(x: Ignored) {}
                }
                override fun search(query: String) = emptySequence()
                fun <T : Bound> generic(value: T, path: java.nio.file.Path): (Analyzer) -> Result<T>? = TODO()
                companion object {}
                enum class Mode { FAST, SLOW; fun next(): Step = TODO() }
            }

            fun Analyzer.queryTerms(query: String): Set<String> = TODO()
            typealias Factory = (Analyzer) -> SearchEngine
            """.trimIndent()
        )

        val expected = AstNode(
            "Test.kt", NodeType.FILE, listOf(
                AstNode(
                    "SearchEngine", NodeType.INTERFACE, listOf(
                        AstNode("index", NodeType.FUNCTION),
                        AstNode("search", NodeType.FUNCTION),
                    ),
                    listOf("CorpusStructure", "CorpusTokenizer", "SearchResult"),
                ),
                AstNode(
                    "SimpleSearchEngine", NodeType.CLASS, listOf(
                        AstNode("analyzer", NodeType.PROPERTY),
                        AstNode("cache", NodeType.PROPERTY),
                        AstNode("index", NodeType.FUNCTION),
                        AstNode("search", NodeType.FUNCTION),
                        AstNode("generic", NodeType.FUNCTION),
                        AstNode("Companion", NodeType.OBJECT),
                        AstNode("Mode", NodeType.ENUM, listOf(AstNode("next", NodeType.FUNCTION))),
                    ),
                    listOf("Analyzer", "AutoCloseable", "CorpusStructure", "CorpusTokenizer", "Path", "SearchEngine", "Step"),
                ),
                AstNode("queryTerms", NodeType.FUNCTION, associations = listOf("Analyzer")),
                AstNode("Factory", NodeType.TYPEALIAS, associations = listOf("Analyzer", "SearchEngine")),
            )
        )
        assertEquals(expected, root)
    }

    @Test
    fun `serializes to JSON`() {
        val node = extract("interface SearchEngine { fun index(c: CorpusTokenizer); fun search(): Sequence<SearchResult> }")
            .children.single()
        assertEquals(
            """{"name":"SearchEngine","type":"interface","children":[""" +
                """{"name":"index","type":"function"},{"name":"search","type":"function"}],""" +
                """"associations":["CorpusTokenizer","SearchResult"]}""",
            node.toJson(),
        )
        assertEquals(
            """
            {
              "name": "f\"1",
              "type": "function"
            }
            """.trimIndent(),
            AstNode("f\"1", NodeType.FUNCTION).toJson(indent = 2),
        )
    }

    @Test
    fun `ignores other languages and tolerates syntax errors`() {
        assertNull(extractor.extract(document("Main.java"), "class Main {}"))
        assertEquals(
            listOf("ok", "Broken"),
            extract("fun ok(): Unit {}\nclass Broken {{{ fun").children.map { it.name },
        )
    }
}
