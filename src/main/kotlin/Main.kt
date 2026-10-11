package org.jetbrains.edu.aads2026

import org.jetbrains.edu.aads2026.ast.DefaultCorpusStructure
import org.jetbrains.edu.aads2026.ast.KotlinStructureExtractor
import org.jetbrains.edu.aads2026.search.SearchEngineFactory
import org.jetbrains.edu.aads2026.search.SearchEngine
import org.jetbrains.edu.aads2026.search.SimpleSearchEngine
import org.jetbrains.edu.aads2026.search.Snippet
import org.jetbrains.edu.aads2026.search.queryTerms
import org.jetbrains.edu.aads2026.tokenizer.Analyzer
import org.jetbrains.edu.aads2026.tokenizer.CodeAnalyzer
import org.jetbrains.edu.aads2026.tokenizer.DefaultCorpusTokenizer
import org.jetbrains.edu.aads2026.tokenizer.FileSystemDocumentSource
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.system.exitProcess
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Search engines selectable with the `--engine` command line flag.
 */
val SEARCH_ENGINES: Map<String, SearchEngineFactory> = mapOf(
    "simple" to ::SimpleSearchEngine,
    // TODO: register your implementation of SearchEngine here, e.g.
    //   "student" to ::StudentSearchEngine,
    // and run the client with `--engine student`.
)

// TODO: once your engine works, you may make it the default one.
const val DEFAULT_ENGINE = "simple"

/**
 * A console search client: indexes a directory with the selected search engine and executes
 * the queries typed by the user, one per line.
 *
 * With `--dump-ast`, prints the syntax trees of the supported files instead, one JSON per line.
 *
 * Usage: `./gradlew run --args="[--engine <name>] [--dump-ast] <directory>"`
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val engineFactory = SEARCH_ENGINES[options.engine] ?: usageError("Unknown search engine: ${options.engine}")
    val root = Path(options.directory)
    if (!Files.isDirectory(root)) usageError("Not a directory: $root")

    val documents = FileSystemDocumentSource(root)
    KotlinStructureExtractor().use { extractor ->
        val structure = DefaultCorpusStructure(documents, extractor)
        if (options.dumpAst) {
            structure.files().forEach { println(it.json) }
            return
        }

        // The same analyzer must process both documents and queries.
        val analyzer = CodeAnalyzer()
        val corpus = DefaultCorpusTokenizer(documents, analyzer)
        val engine = engineFactory(analyzer)

        println("Indexing ${root.toAbsolutePath().normalize()} with the '${options.engine}' engine")
        val indexingTime = measureTime { engine.index(corpus, structure) }
        println("Indexed in $indexingTime")
        runQueries(analyzer, engine)
    }
}

private fun runQueries(analyzer: Analyzer, engine: SearchEngine) {
    println("Enter space-separated query words, or an empty line to exit.")
    while (true) {
        print("> ")
        val query = readlnOrNull()?.trim()
        if (query.isNullOrEmpty()) break

        val terms = analyzer.queryTerms(query)
        if (terms.isEmpty()) {
            println("The query has no searchable words.")
            continue
        }
        println("Query terms: ${terms.joinToString(" ")}")

        val (count, duration) = measureTimedValue {
            var count = 0
            for (result in engine.search(query)) {
                count++
                val snippet = result.snippet
                println("  %.3f  %s%s".format(result.score, result.document.name, snippet?.let { ":${it.line}" } ?: ""))
                if (snippet != null) println("         ${snippet.highlighted()}")
            }
            count
        }
        println("$count document(s) found in $duration")
    }
}

/** Snippet text with the matches enclosed in `«»`. */
private fun Snippet.highlighted(): String = buildString {
    var last = 0
    for (range in highlights) {
        append(text, last, range.first).append('«').append(text, range.first, range.last + 1).append('»')
        last = range.last + 1
    }
    append(text, last, text.length)
}

private class Options(val engine: String, val dumpAst: Boolean, val directory: String)

private fun parseArgs(args: Array<String>): Options {
    var engine = DEFAULT_ENGINE
    var dumpAst = false
    var directory: String? = null
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--engine" -> engine = args.getOrNull(++i) ?: usageError("Missing value of --engine")
            arg.startsWith("--engine=") -> engine = arg.removePrefix("--engine=")
            arg == "--dump-ast" -> dumpAst = true
            arg.startsWith("-") -> usageError("Unknown option: $arg")
            directory == null -> directory = arg
            else -> usageError("Unexpected argument: $arg")
        }
        i++
    }
    return Options(engine, dumpAst, directory ?: usageError("Missing directory"))
}

private fun usageError(message: String): Nothing {
    System.err.println(message)
    System.err.println("Usage: [--engine <name>] [--dump-ast] <directory with source code>")
    System.err.println("Available engines: ${SEARCH_ENGINES.keys.joinToString()} (default: $DEFAULT_ENGINE)")
    exitProcess(1)
}
