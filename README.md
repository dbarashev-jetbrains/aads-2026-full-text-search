# Code Search Framework

A starter framework for the assignment on full-text search and code search. It takes care of the
boring parts — reading source files, tokenizing and stemming them, extracting their structure — so
that you can concentrate on indexing, query processing and ranking.

## 1. Overview

```
                    ┌──────────────── Analyzer (CodeAnalyzer) ────────────────┐
DocumentSource ──►  │ Lexer ──► IdentifierSplitter ──► lower-case ──► Stemmer │ ──► CorpusTokenizer ──┐
  (source files)    └─────────────────────────────────────────────────────────┘    Sequence<DocumentToken>
       │                                                                                              ├──► SearchEngine ──► SearchResult
       └──────────────────────────► StructureExtractor ──────────────────────────► CorpusStructure ───┘     index()          document, score,
                                    (Kotlin compiler PSI)                          Sequence<FileStructure>  search()         snippet
```

The code lives in three packages under `src/main/kotlin`:

### `tokenizer` — from files to a stream of tokens

| Component | Implementation | What it does |
|---|---|---|
| `DocumentSource`, `Document` | `FileSystemDocumentSource` | Recursively collects source files under a directory. Skips hidden and build directories (`build`, `out`, `node_modules`, …), non-code extensions and files over 1 MB. Each document has a dense `id` and a `name` (path relative to the root). |
| `Lexer` | `CodeLexer` | Splits text into words: runs of letters, digits, `_` and `$`. Punctuation and operators are dropped. |
| `IdentifierSplitter` | `CamelCaseSplitter` | Splits identifiers: `parseHTTPRequest` → `parse`, `HTTP`, `Request`; `MAX_SIZE` → `MAX`, `SIZE`. |
| `Stemmer` | `PorterStemmer` | The classic Porter stemmer: `parsing`, `parsed`, `parses` → `pars`. |
| `Analyzer` | `CodeAnalyzer` | Combines the above. Produces `Token`s with the normalized `term`, `position`, character offsets, line and column. |
| `CorpusTokenizer` | `DefaultCorpusTokenizer` | Runs the analyzer over all documents and emits a lazy `Sequence<DocumentToken>` — the input for your indexer. |

How identifiers are tokenized: a compound identifier produces a `PART` token for every part and,
additionally, a `COMPOUND` token for the whole identifier. For example, `val parsedHTTPHeaders = 1`
yields:

| term | type | position |
|---|---|---|
| `val` | `WORD` | 0 |
| `parsedhttphead` | `COMPOUND` | 1 |
| `pars` | `PART` | 1 |
| `http` | `PART` | 2 |
| `header` | `PART` | 3 |
| `1` | `WORD` | 4 |

Positions are ordinal numbers of words, so phrase and proximity queries can be answered with position
arithmetic: `http header` is a phrase inside the identifier above. The compound term is the parts
concatenated, so `HttpRequest`, `http_request` and `HTTP_REQUEST` all produce `httprequest`.

Guarantees of the token stream: documents come in a stable order; tokens of one document are
contiguous (a change of `DocumentToken.document` means that the previous document has ended);
within a document, positions do not decrease.

**Always analyze queries with the same analyzer as documents**, otherwise query terms will not match
the index terms. `Analyzer.queryTerms(query)` returns the distinct terms of a query.

### `ast` — high-level structure of the code

`KotlinStructureExtractor` parses Kotlin files (`.kt`, `.kts`) with the Kotlin compiler's parser
and produces, for every file, a tree of declarations. `CorpusStructure.files()` emits a
`FileStructure` per file, with the tree available both as Kotlin objects (`root: AstNode`) and as
JSON (`json`). Files in other languages are tokenized, but have no structure.

```json
{
  "name": "SearchEngine",
  "type": "interface",
  "children": [{"name": "index", "type": "function"}, {"name": "search", "type": "function"}],
  "associations": ["CorpusStructure", "CorpusTokenizer", "SearchResult"]
}
```

- The root of each file is a node of type `file`; its children are the top-level declarations.
- Node types: `file`, `class`, `interface`, `object`, `enum`, `function`, `property`, `typealias`.
- `associations` lists the types a declaration refers to: supertypes, types of function parameters
  and results. Associations are reported only on top-level declarations and include those of all
  their members. Built-in and common standard library types (`String`, `List`, …) are omitted.
- The extractor does not resolve symbols: type names are simple names as written in the code,
  and result types that are not written explicitly are unknown.

### `search` — search engines

- `SearchEngine` is the interface you implement: `index(corpus, structure)` is called once,
  then `search(query)` is called for every query and returns `SearchResult`s, most relevant first.
- `SearchResult` holds the `document`, a relevance `score` and a `snippet`.
- `buildSnippet(...)` builds a `Snippet` — the line with the most relevant match and the ranges of
  the matched words — either from the matching tokens or by re-analyzing the document text.
- `SimpleSearchEngine` is a baseline without any index: every query re-tokenizes the whole corpus
  and returns, unranked, the documents that contain all query terms.

### Running the search client

```shell
./gradlew run --args="[--engine <name>] <directory>"
```

The client indexes the directory with the selected engine (`simple` by default), then reads
queries from the console, one per line; an empty line exits:

```
> search engine
Query terms: search engin
  1.000  main/kotlin/search/SimpleSearchEngine.kt:14
         class Simple«SearchEngine»(private val analyzer: Analyzer) : «SearchEngine» {
```

To see the structure extracted from the corpus, one JSON per file and per line:

```shell
./gradlew run --args="--dump-ast <directory>"
```

## 2. What you need to implement

Your main task is your own implementation of `SearchEngine` (`src/main/kotlin/search/SearchEngine.kt`):

```kotlin
class StudentSearchEngine(private val analyzer: Analyzer) : SearchEngine {
    override fun index(corpus: CorpusTokenizer, structure: CorpusStructure) {
        // Build your index from corpus.tokens() and, optionally, structure.files().
    }

    override fun search(query: String): Sequence<SearchResult> {
        // Analyze the query with the same analyzer, look the terms up in the index,
        // rank the documents and build snippets.
    }
}
```

The constructor must accept the `Analyzer` used for the corpus: that's how your engine is created
(see `SearchEngineFactory`). Typical parts of an engine:

1. **Indexer** — consumes `corpus.tokens()` in `index()` and builds an inverted index: for every
   term, the documents (and possibly positions and offsets) where it occurs. Collect the statistics
   that your ranking needs, such as document lengths and term frequencies.
2. **Query processing** — turns the query into index terms with `analyzer.queryTerms(query)` or
   `analyzer.analyze(query)` and finds the matching documents. Think about which `TokenType`s to
   use, whether all terms must match, phrase queries via positions, etc.
3. **Ranking** — assigns `SearchResult.score` (e.g. TF-IDF or BM25) and returns the results sorted by
   decreasing score.
4. **Code-specific relevance** — uses `structure.files()` to boost documents where the queried
   name is declared (a class or function named like the query) or associated with the found
   declarations.
5. **Snippets** — fills `SearchResult.snippet`, e.g. with `buildSnippet(text, matches)` if you store
   token offsets, or `buildSnippet(text, analyzer, terms)` otherwise.

You may also replace or tune the analysis pipeline, e.g. add stop words via
`CodeAnalyzer(stopWords = ...)`, disable compound tokens or write your own `Analyzer`.

### Plugging in your engine

The places to change are marked with `TODO` comments in `src/main/kotlin/Main.kt`:

1. Register your engine in `SEARCH_ENGINES`:
   ```kotlin
   val SEARCH_ENGINES: Map<String, SearchEngineFactory> = mapOf(
       "simple" to ::SimpleSearchEngine,
       "student" to ::StudentSearchEngine,
   )
   ```
2. Run the client with your engine:
   ```shell
   ./gradlew run --args="--engine student <directory>"
   ```
3. Optionally, make it the default by changing `DEFAULT_ENGINE`.

If you change the analyzer, change it in `main()` in `Main.kt` as well, since the same analyzer
instance is passed both to the corpus tokenizer and to your engine.

## 3. Running unit tests

Requirements: JDK 21 (Gradle downloads it automatically if missing). Run all tests:

```shell
./gradlew test
```

Run a single test class or a package:

```shell
./gradlew test --tests "org.jetbrains.edu.aads2026.search.SimpleSearchEngineTest"
./gradlew test --tests "org.jetbrains.edu.aads2026.tokenizer.*"
```

The HTML report is written to `build/reports/tests/test/index.html`.

The existing tests in `src/test/kotlin` cover the framework and are good examples of how to use it:

| Test | Covers |
|---|---|
| `tokenizer/PorterStemmerTest` | the stemmer against the reference vocabulary |
| `tokenizer/CamelCaseSplitterTest` | identifier splitting |
| `tokenizer/CodeAnalyzerTest` | terms, token types, positions, offsets, stop words |
| `tokenizer/CorpusTokenizerTest` | file collection and the corpus token stream |
| `ast/KotlinStructureExtractorTest` | the extracted declarations, associations and JSON |
| `search/SnippetTest` | snippet line selection, highlighting and cutting |
| `search/SimpleSearchEngineTest` | the baseline engine on an in-memory corpus |

Put the tests for your engine next to them, in `src/test/kotlin/search`.
`SimpleSearchEngineTest` shows how to build a small in-memory corpus without files on disk.
