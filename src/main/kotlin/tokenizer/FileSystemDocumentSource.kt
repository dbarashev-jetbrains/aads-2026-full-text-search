package org.jetbrains.edu.aads2026.tokenizer

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

/**
 * A document backed by a file on disk. Its [name] is the path relative to the corpus root,
 * with `/` as the separator.
 */
data class FileDocument(
    override val id: Int,
    override val name: String,
    val path: Path,
) : Document {
    // Malformed UTF-8 sequences are replaced with U+FFFD, so this never fails on encoding issues.
    override fun readText(): String = String(Files.readAllBytes(path), Charsets.UTF_8)
}

/**
 * Recursively collects source files under [root].
 *
 * @param fileFilter decides which regular files are included; by default, files with
 *   a well-known source code extension (see [CODE_EXTENSIONS]).
 * @param excludedDirectories names of directories that are not descended into. Hidden directories
 *   (whose names start with a dot) are always skipped.
 * @param maxFileSize files larger than this many bytes are skipped (generated code, minified bundles, etc.)
 */
class FileSystemDocumentSource(
    private val root: Path,
    private val fileFilter: (Path) -> Boolean = ::hasCodeExtension,
    private val excludedDirectories: Set<String> = DEFAULT_EXCLUDED_DIRECTORIES,
    private val maxFileSize: Long = 1L shl 20,
) : DocumentSource {

    init {
        require(Files.isDirectory(root)) { "Not a directory: $root" }
    }

    override fun documents(): Sequence<Document> =
        collectFiles()
            .map { root.relativize(it).invariantSeparatorsPathString to it }
            .sortedBy { it.first }
            .asSequence()
            .mapIndexed { id, (name, path) -> FileDocument(id, name, path) }

    private fun collectFiles(): List<Path> {
        val result = mutableListOf<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir == root) return FileVisitResult.CONTINUE
                val name = dir.name
                return if (name.startsWith(".") || name in excludedDirectories) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && attrs.size() <= maxFileSize && fileFilter(file)) {
                    result.add(file)
                }
                return FileVisitResult.CONTINUE
            }

            // Unreadable files and directories are skipped rather than failing the whole walk.
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return result
    }

    companion object {
        val DEFAULT_EXCLUDED_DIRECTORIES = setOf("build", "out", "target", "bin", "node_modules", "dist", "venv")

        val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "scala", "groovy",
            "c", "h", "cc", "cpp", "cxx", "hpp", "cs", "m", "mm", "swift",
            "go", "rs", "zig",
            "py", "rb", "php", "pl", "lua", "r",
            "js", "jsx", "ts", "tsx", "mjs", "cjs",
            "sh", "bash", "sql",
        )

        fun hasCodeExtension(path: Path): Boolean = path.extension.lowercase() in CODE_EXTENSIONS
    }
}
