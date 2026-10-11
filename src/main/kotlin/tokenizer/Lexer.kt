package org.jetbrains.edu.aads2026.tokenizer

/**
 * A raw word as it appears in the text, before any normalization.
 *
 * @property startOffset offset of the first character in the text (inclusive)
 * @property endOffset offset after the last character in the text (exclusive)
 * @property line 1-based line number of the first character
 * @property column 1-based column number of the first character
 */
data class Lexeme(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
    val column: Int,
)

/**
 * Splits text into raw words, dropping whitespace, punctuation and operators.
 */
interface Lexer {
    fun lex(text: CharSequence): Sequence<Lexeme>
}

/**
 * A language-agnostic lexer for source code: a word is a maximal run of letters, digits,
 * underscores and dollar signs, so that identifiers like `parse_http_request`, `$scope`
 * or `int32` are kept intact. Everything else is a separator.
 *
 * Comments and string literals are not treated specially: words inside them are emitted too.
 */
class CodeLexer : Lexer {
    override fun lex(text: CharSequence): Sequence<Lexeme> = sequence {
        var line = 1
        var column = 1
        var start = -1
        var startLine = 0
        var startColumn = 0
        for (i in 0..text.length) {
            val c = if (i < text.length) text[i] else ' '
            if (isWordChar(c)) {
                if (start < 0) {
                    start = i
                    startLine = line
                    startColumn = column
                }
            } else if (start >= 0) {
                yield(Lexeme(text.substring(start, i), start, i, startLine, startColumn))
                start = -1
            }
            if (c == '\n') {
                line++
                column = 1
            } else {
                column++
            }
        }
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '$'
}
