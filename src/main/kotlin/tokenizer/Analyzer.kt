package org.jetbrains.edu.aads2026.tokenizer

import java.util.Locale

enum class TokenType {
    /** A word that is not split further, e.g. `request` or `utf8`. */
    WORD,

    /** A whole compound identifier, e.g. `parseHttpRequest`, emitted in addition to its [PART]s. */
    COMPOUND,

    /** One part of a compound identifier, e.g. `http` in `parseHttpRequest`. */
    PART,
}

/**
 * A normalized token: the unit that goes to the index.
 *
 * @property term normalized form used as the index key (lower-cased and stemmed)
 * @property position ordinal position of the token in its document, starting from 0.
 *   Consecutive words have consecutive positions, so phrase and proximity queries can be answered
 *   with position arithmetic. The parts of a compound identifier occupy consecutive positions too,
 *   and the [TokenType.COMPOUND] token shares its position with the first part.
 * @property startOffset offset of the first character in the document text (inclusive)
 * @property endOffset offset after the last character in the document text (exclusive)
 * @property line 1-based line number in the document
 * @property column 1-based column number in the document
 */
data class Token(
    val term: String,
    val type: TokenType,
    val position: Int,
    val startOffset: Int,
    val endOffset: Int,
    val line: Int,
    val column: Int,
)

/**
 * Turns text into a stream of normalized tokens.
 *
 * The same analyzer must be used for documents and queries, otherwise query terms will not match
 * index terms.
 */
interface Analyzer {
    fun analyze(text: CharSequence): Sequence<Token>
}

/**
 * Analyzer for source code. For each word produced by the [lexer]:
 *
 * 1. the word is split into parts by the [splitter];
 * 2. each part is lower-cased and stemmed by the [stemmer];
 * 3. a single-part word yields one [TokenType.WORD] token; a multi-part identifier yields
 *    one [TokenType.PART] token per part plus, if [emitCompounds] is on, a [TokenType.COMPOUND]
 *    token whose term is the parts concatenated and then stemmed, so that `HttpRequests`,
 *    `http_request` and `HTTP_REQUEST` all produce the compound term `httprequest`.
 *
 * Parts whose lower-cased form is in [stopWords] are dropped, but they still occupy a position.
 *
 * Example: `val parsedHTTPHeaders = 1` yields
 * ```
 * term                type      position
 * val                 WORD      0
 * parsedhttphead      COMPOUND  1
 * pars                PART      1
 * http                PART      2
 * header              PART      3
 * 1                   WORD      4
 * ```
 */
class CodeAnalyzer(
    private val lexer: Lexer = CodeLexer(),
    private val splitter: IdentifierSplitter = CamelCaseSplitter(),
    private val stemmer: Stemmer = PorterStemmer(),
    private val emitCompounds: Boolean = true,
    private val stopWords: Set<String> = emptySet(),
) : Analyzer {

    override fun analyze(text: CharSequence): Sequence<Token> = sequence {
        var position = 0
        for (lexeme in lexer.lex(text)) {
            val parts = splitter.split(lexeme.text)
            if (parts.isEmpty()) continue

            val words = parts.map { lexeme.text.substring(it).lowercase(Locale.ROOT) }
            if (parts.size > 1 && emitCompounds) {
                val compound = words.joinToString("")
                if (compound !in stopWords) {
                    yield(lexeme.toToken(stemmer.stem(compound), TokenType.COMPOUND, position, parts.first().first, parts.last().last + 1))
                }
            }
            val type = if (parts.size == 1) TokenType.WORD else TokenType.PART
            for ((i, range) in parts.withIndex()) {
                if (words[i] !in stopWords) {
                    yield(lexeme.toToken(stemmer.stem(words[i]), type, position, range.first, range.last + 1))
                }
                position++
            }
        }
    }

    /** Creates a token for the [lexeme] characters in `[start, end)`. */
    private fun Lexeme.toToken(term: String, type: TokenType, position: Int, start: Int, end: Int) = Token(
        term = term,
        type = type,
        position = position,
        startOffset = startOffset + start,
        endOffset = startOffset + end,
        line = line,
        column = column + start,
    )
}
