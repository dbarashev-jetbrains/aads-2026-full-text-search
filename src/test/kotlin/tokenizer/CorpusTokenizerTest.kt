package org.jetbrains.edu.aads2026.tokenizer

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir

class CorpusTokenizerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `tokenizes source files in stable order and skips excluded ones`() {
        root.resolve("src/b").createDirectories()
        root.resolve("src/b/Second.kt").writeText("fun second()")
        root.resolve("src/First.java").writeText("class First {}")
        root.resolve("README.txt").writeText("not code")
        root.resolve("build").createDirectories().resolve("Gen.kt").writeText("generated")
        root.resolve(".git").createDirectories().resolve("config.kt").writeText("hidden")

        val tokens = DefaultCorpusTokenizer(FileSystemDocumentSource(root)).tokens().toList()

        assertEquals(
            listOf(
                Triple(0, "src/First.java", "class"),
                Triple(0, "src/First.java", "first"),
                Triple(1, "src/b/Second.kt", "fun"),
                Triple(1, "src/b/Second.kt", "second"),
            ),
            tokens.map { Triple(it.document.id, it.document.name, it.term) },
        )
    }
}
