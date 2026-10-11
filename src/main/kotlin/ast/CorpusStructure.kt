package org.jetbrains.edu.aads2026.ast

import org.jetbrains.edu.aads2026.tokenizer.Document
import org.jetbrains.edu.aads2026.tokenizer.DocumentSource

/**
 * High-level syntax tree of one document.
 *
 * @property root the node of type [NodeType.FILE]
 */
data class FileStructure(val document: Document, val root: AstNode) {
    /** The tree serialized to a single-line JSON. */
    val json: String get() = root.toJson()
}

/**
 * Builds the high-level syntax tree of a single source file.
 */
interface StructureExtractor {
    /** Returns the tree of [document] with the given [text], or null if its language is not supported. */
    fun extract(document: Document, text: String): AstNode?
}

/**
 * Produces the syntax trees of all documents of a corpus.
 */
fun interface CorpusStructure {
    /**
     * Returns a lazy stream of syntax trees, in the order of [DocumentSource.documents].
     * Documents whose language is not supported are absent from the stream.
     */
    fun files(): Sequence<FileStructure>
}

/**
 * Reads every document of the [source] and runs it through the [extractor].
 *
 * @param onError called when a document cannot be read or parsed; the document is then skipped.
 */
class DefaultCorpusStructure(
    private val source: DocumentSource,
    private val extractor: StructureExtractor,
    private val onError: (Document, Exception) -> Unit = { doc, e ->
        System.err.println("Skipping ${doc.name}: ${e.message}")
    },
) : CorpusStructure {

    override fun files(): Sequence<FileStructure> =
        source.documents().mapNotNull { document ->
            try {
                extractor.extract(document, document.readText())?.let { FileStructure(document, it) }
            } catch (e: Exception) {
                onError(document, e)
                null
            }
        }
}
