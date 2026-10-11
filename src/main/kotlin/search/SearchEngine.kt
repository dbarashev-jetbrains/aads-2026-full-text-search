package org.jetbrains.edu.aads2026.search

import org.jetbrains.edu.aads2026.ast.CorpusStructure
import org.jetbrains.edu.aads2026.tokenizer.Analyzer
import org.jetbrains.edu.aads2026.tokenizer.CorpusTokenizer
import org.jetbrains.edu.aads2026.tokenizer.Document

/**
 * One document found by a query.
 *
 * @property score relevance of the document to the query; higher is better. Engines that do not
 *   rank results may use the same score for all documents.
 * @property snippet the line with the most relevant match, to be shown to the user; see [buildSnippet]
 */
data class SearchResult(
    val document: Document,
    val score: Double = 1.0,
    val snippet: Snippet? = null,
)

/**
 * A full-text search engine over a code corpus. The lifecycle is:
 *
 * 1. [index] is called exactly once with the corpus: its tokens and the syntax trees of its files;
 * 2. [search] is called any number of times afterwards.
 */
interface SearchEngine {
    /**
     * Processes the corpus before any query is executed, e.g. builds an inverted index.
     *
     * @param corpus token stream of all documents
     * @param structure high-level syntax trees (declarations and their associations) of the documents
     *   in supported languages. Use it for code-specific search, e.g. to boost documents that declare
     *   a class with the queried name.
     */
    fun index(corpus: CorpusTokenizer, structure: CorpusStructure)

    /**
     * Executes a query typed by the user and returns the found documents, most relevant first.
     * The query must be processed with the same [Analyzer] as the corpus.
     */
    fun search(query: String): Sequence<SearchResult>
}

/**
 * Creates a search engine that uses the given analyzer for queries. The same analyzer is used
 * to produce the corpus tokens passed to [SearchEngine.index].
 */
typealias SearchEngineFactory = (Analyzer) -> SearchEngine

/** Runs the query through the analyzer and returns the distinct terms, in order of appearance. */
fun Analyzer.queryTerms(query: String): Set<String> =
    analyze(query).map { it.term }.toCollection(LinkedHashSet())
