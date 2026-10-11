package org.jetbrains.edu.aads2026.tokenizer

/**
 * Splits a compound identifier into its constituent words.
 */
interface IdentifierSplitter {
    /**
     * Returns the character ranges of the parts of [identifier], in order. Separators are not
     * included in any range. An identifier consisting only of separators yields an empty list.
     */
    fun split(identifier: String): List<IntRange>
}

/**
 * Splits identifiers on `_` and `$` separators and on case changes:
 *
 * - `parseHttpRequest` → `parse`, `Http`, `Request`
 * - `HTTPServer` → `HTTP`, `Server`
 * - `MAX_BUFFER_SIZE` → `MAX`, `BUFFER`, `SIZE`
 * - `int32Value` → `int32`, `Value` (digits stick to the preceding part, so `utf8`, `md5`, `base64` stay whole)
 */
class CamelCaseSplitter : IdentifierSplitter {
    override fun split(identifier: String): List<IntRange> {
        val parts = mutableListOf<IntRange>()
        var start = -1
        for (i in identifier.indices) {
            val c = identifier[i]
            if (c == '_' || c == '$') {
                if (start >= 0) parts += start until i
                start = -1
                continue
            }
            if (start >= 0 && c.isUpperCase()) {
                val prev = identifier[i - 1]
                val next = identifier.getOrNull(i + 1)
                // fooBar, foo1Bar: an upper-case letter after a lower-case letter or a digit starts a new part.
                // HTTPServer: the last capital of an acronym starts a new part if followed by a lower-case letter.
                if (prev.isLowerCase() || prev.isDigit() ||
                    (prev.isUpperCase() && next != null && next.isLowerCase())
                ) {
                    parts += start until i
                    start = i
                }
            }
            if (start < 0) start = i
        }
        if (start >= 0) parts += start until identifier.length
        return parts
    }
}
