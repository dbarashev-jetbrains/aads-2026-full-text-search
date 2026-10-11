package org.jetbrains.edu.aads2026.tokenizer

/**
 * A token together with the document it was found in. This is what the indexer consumes.
 */
data class DocumentToken(val document: Document, val token: Token) {
    val term: String get() = token.term
    val position: Int get() = token.position
}

/**
 * Produces the token stream of a whole corpus.
 */
interface CorpusTokenizer {
    /**
     * Returns a lazy stream of tokens of all documents. Guarantees:
     *
     * - documents come in the order of [DocumentSource.documents];
     * - tokens of one document are contiguous, so a change of [DocumentToken.document] marks
     *   the end of the previous document;
     * - within a document, tokens come in non-decreasing [Token.position] order.
     *
     * Documents without any tokens do not appear in the stream. Each call starts a new pass
     * over the corpus and re-reads the files.
     */
    fun tokens(): Sequence<DocumentToken>
}

/**
 * Reads every document of the [source] and runs it through the [analyzer].
 *
 * @param onError called when a document cannot be read; the document is then skipped.
 */
class DefaultCorpusTokenizer(
    private val source: DocumentSource,
    private val analyzer: Analyzer = CodeAnalyzer(),
    private val onError: (Document, Exception) -> Unit = { doc, e ->
        System.err.println("Skipping ${doc.name}: ${e.message}")
    },
) : CorpusTokenizer {

    override fun tokens(): Sequence<DocumentToken> =
        source.documents().flatMap { document ->
            val text = try {
                document.readText()
            } catch (e: Exception) {
                onError(document, e)
                return@flatMap emptySequence()
            }
            analyzer.analyze(text).map { DocumentToken(document, it) }
        }
}
