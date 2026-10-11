package org.jetbrains.edu.aads2026.search

import org.jetbrains.edu.aads2026.tokenizer.Analyzer
import org.jetbrains.edu.aads2026.tokenizer.Token

/**
 * A line of a document shown with a search result.
 *
 * @property line 1-based line number in the document
 * @property text the line without leading and trailing whitespace. A long line is cut around the
 *   first match, with `…` marking the cut ends.
 * @property highlights ranges of the matched words in [text], sorted and non-overlapping
 */
data class Snippet(val line: Int, val text: String, val highlights: List<IntRange>)

/**
 * Builds a snippet from the line with the most relevant matches: the line containing the largest
 * number of distinct query terms. Ties are broken by the total number of matches, then by
 * the line number (earlier wins).
 *
 * @param text full text of the document
 * @param matches tokens of the document whose terms match the query
 * @param maxLength maximum length of the snippet text, not counting the `…` marks
 * @return the snippet, or null if there are no matches
 */
fun buildSnippet(text: CharSequence, matches: Collection<Token>, maxLength: Int = 160): Snippet? {
    val (line, lineMatches) = matches.groupBy { it.line }.entries.minWithOrNull(
        compareByDescending<Map.Entry<Int, List<Token>>> { (_, tokens) -> tokens.distinctBy { it.term }.size }
            .thenByDescending { (_, tokens) -> tokens.size }
            .thenBy { (line, _) -> line }
    ) ?: return null

    // Find the boundaries of the line, without surrounding whitespace.
    val offset = lineMatches.first().startOffset
    var lineStart = text.lastIndexOf('\n', offset - 1) + 1
    var lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
    while (lineStart < lineEnd && text[lineStart].isWhitespace()) lineStart++
    while (lineEnd > lineStart && text[lineEnd - 1].isWhitespace()) lineEnd--

    // Matches may overlap: a compound identifier contains its parts.
    val ranges = mutableListOf<IntRange>()
    for (range in lineMatches.map { it.startOffset until it.endOffset }.sortedBy { it.first }) {
        val last = ranges.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            ranges[ranges.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            ranges += range
        }
    }

    // Cut a long line so that the first match is close to the beginning.
    var start = lineStart
    var end = lineEnd
    if (end - start > maxLength) {
        start = (ranges.first().first - maxLength / 4).coerceIn(lineStart, lineEnd - maxLength)
        end = start + maxLength
    }
    val prefix = if (start > lineStart) "…" else ""
    val suffix = if (end < lineEnd) "…" else ""
    val shift = prefix.length - start
    val highlights = ranges
        .map { maxOf(it.first, start)..minOf(it.last, end - 1) }
        .filter { !it.isEmpty() }
        .map { (it.first + shift)..(it.last + shift) }
    return Snippet(line, prefix + text.substring(start, end) + suffix, highlights)
}

/**
 * Builds a snippet by re-analyzing the document [text] and matching its tokens against the query
 * [terms]. Convenient when the index does not store token offsets.
 */
fun buildSnippet(text: CharSequence, analyzer: Analyzer, terms: Set<String>, maxLength: Int = 160): Snippet? =
    buildSnippet(text, analyzer.analyze(text).filter { it.term in terms }.toList(), maxLength)
