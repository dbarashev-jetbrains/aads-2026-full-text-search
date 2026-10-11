package org.jetbrains.edu.aads2026.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals

class CamelCaseSplitterTest {
    private fun split(identifier: String) = CamelCaseSplitter().split(identifier).map { identifier.substring(it) }

    @Test
    fun `splits identifiers`() {
        assertEquals(listOf("parse", "Http", "Request"), split("parseHttpRequest"))
        assertEquals(listOf("HTTP", "Server"), split("HTTPServer"))
        assertEquals(listOf("get", "URL"), split("getURL"))
        assertEquals(listOf("MAX", "BUFFER", "SIZE"), split("MAX_BUFFER_SIZE"))
        assertEquals(listOf("snake", "case"), split("__snake__case_"))
        assertEquals(listOf("int32", "Value"), split("int32Value"))
        assertEquals(listOf("utf8"), split("utf8"))
        assertEquals(listOf("scope"), split("\$scope"))
        assertEquals(listOf("Simple"), split("Simple"))
        assertEquals(emptyList(), split("___"))
    }
}
