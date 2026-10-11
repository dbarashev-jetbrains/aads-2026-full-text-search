package org.jetbrains.edu.aads2026.search

import org.jetbrains.edu.aads2026.ast.CorpusStructure
import org.jetbrains.edu.aads2026.tokenizer.Analyzer
import org.jetbrains.edu.aads2026.tokenizer.CorpusTokenizer
import org.jetbrains.edu.aads2026.tokenizer.Document
import org.jetbrains.edu.aads2026.tokenizer.Token

/**
 * A baseline engine without an index: every query re-tokenizes the whole corpus and returns,
 * unranked, the documents that contain all query terms. To build the snippet, the text of every
 * found document is read once more.
 */
class SimpleSearchEngine(private val analyzer: Analyzer) : SearchEngine {
    private var corpus: CorpusTokenizer? = null

    /** Builds nothing: just remembers the corpus to scan it on every query. The structure is not used. */
    override fun index(corpus: CorpusTokenizer, structure: CorpusStructure) {
        this.corpus = corpus
    }

    /**
     * Scans the token stream and yields every document that contains all query terms, in corpus order.
     * Relies on the tokens of one document being contiguous in the stream.
     */
    override fun search(query: String): Sequence<SearchResult> = sequence {
        val corpus = checkNotNull(corpus) { "index() must be called before search()" }
        val terms = analyzer.queryTerms(query)
        if (terms.isEmpty()) return@sequence

        var currentDocument: Document? = null
        var foundTerms = HashSet<String>()
        var matches = ArrayList<Token>()

        fun completedResult(): SearchResult? {
            val document = currentDocument ?: return null
            if (foundTerms.size < terms.size) return null
            // The document has already been read once, so a failure here is unlikely; the snippet is optional anyway.
            val snippet = runCatching { buildSnippet(document.readText(), matches) }.getOrNull()
            return SearchResult(document, snippet = snippet)
        }

        for ((document, token) in corpus.tokens()) {
            if (document != currentDocument) {
                completedResult()?.let { yield(it) }
                currentDocument = document
                foundTerms = HashSet()
                matches = ArrayList()
            }
            if (token.term in terms) {
                foundTerms += token.term
                matches += token
            }
        }
        completedResult()?.let { yield(it) }
    }
}
