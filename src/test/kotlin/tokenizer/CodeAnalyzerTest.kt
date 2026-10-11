package org.jetbrains.edu.aads2026.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeAnalyzerTest {
    private fun Token.describe() = "$term $type $position $line:$column [$startOffset,$endOffset)"

    @Test
    fun `emits words, compounds and parts with positions and offsets`() {
        val text = "val parsedHTTPHeaders = 1\n  // connections"
        val tokens = CodeAnalyzer().analyze(text).toList()
        assertEquals(
            listOf(
                "val WORD 0 1:1 [0,3)",
                "parsedhttphead COMPOUND 1 1:5 [4,21)",
                "pars PART 1 1:5 [4,10)",
                "http PART 2 1:11 [10,14)",
                "header PART 3 1:15 [14,21)",
                "1 WORD 4 1:25 [24,25)",
                "connect WORD 5 2:6 [31,42)",
            ),
            tokens.map { it.describe() },
        )
        for (token in tokens.filter { it.type != TokenType.COMPOUND }) {
            assertEquals(token.term.take(3), text.substring(token.startOffset, token.endOffset).lowercase().take(3))
        }
    }

    @Test
    fun `different spellings of an identifier share the compound term`() {
        val analyzer = CodeAnalyzer()
        val compounds = listOf("HttpRequests", "http_request", "HTTP_REQUEST").map { spelling ->
            analyzer.analyze(spelling).single { it.type == TokenType.COMPOUND }.term
        }
        assertEquals(listOf("httprequest", "httprequest", "httprequest"), compounds)
    }

    @Test
    fun `stop words are dropped but keep their positions`() {
        val analyzer = CodeAnalyzer(stopWords = setOf("the"), emitCompounds = false)
        assertEquals(
            listOf("get" to 0, "valu" to 2),
            analyzer.analyze("getTheValue").map { it.term to it.position }.toList(),
        )
    }
}
