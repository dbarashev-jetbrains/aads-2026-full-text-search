package org.jetbrains.edu.aads2026.tokenizer

/**
 * A unit of indexing and retrieval: typically, one source file.
 */
interface Document {
    /**
     * Dense zero-based identifier, unique within one [DocumentSource]. Use it as a compact key
     * in posting lists instead of the name.
     */
    val id: Int

    /** Human-readable name, e.g. the file path relative to the corpus root. */
    val name: String

    /** Reads the full text of the document. Called once per tokenization pass. */
    fun readText(): String
}

/**
 * A collection of documents to be tokenized.
 */
interface DocumentSource {
    /**
     * Returns the documents of the corpus in a stable order, with ids assigned in that order.
     */
    fun documents(): Sequence<Document>
}
