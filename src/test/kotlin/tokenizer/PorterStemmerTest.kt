package org.jetbrains.edu.aads2026.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals

class PorterStemmerTest {
    private val stemmer = PorterStemmer()

    // Examples from Porter's paper and the reference vocabulary at https://tartarus.org/martin/PorterStemmer/
    private val expected = mapOf(
        "caresses" to "caress", "ponies" to "poni", "ties" to "ti", "caress" to "caress", "cats" to "cat",
        "feed" to "feed", "agreed" to "agre", "plastered" to "plaster", "bled" to "bled",
        "motoring" to "motor", "sing" to "sing", "conflated" to "conflat", "troubled" to "troubl",
        "sized" to "size", "hopping" to "hop", "tanned" to "tan", "falling" to "fall", "hissing" to "hiss",
        "fizzed" to "fizz", "failing" to "fail", "filing" to "file", "happy" to "happi", "sky" to "sky",
        "relational" to "relat", "conditional" to "condit", "rational" to "ration", "valenci" to "valenc",
        "hesitanci" to "hesit", "digitizer" to "digit", "conformabli" to "conform", "radicalli" to "radic",
        "differentli" to "differ", "vileli" to "vile", "analogousli" to "analog", "vietnamization" to "vietnam",
        "predication" to "predic", "operator" to "oper", "feudalism" to "feudal", "decisiveness" to "decis",
        "hopefulness" to "hope", "callousness" to "callous", "formaliti" to "formal", "sensitiviti" to "sensit",
        "sensibiliti" to "sensibl", "triplicate" to "triplic", "formative" to "form", "formalize" to "formal",
        "electriciti" to "electr", "electrical" to "electr", "hopeful" to "hope", "goodness" to "good",
        "revival" to "reviv", "allowance" to "allow", "inference" to "infer", "airliner" to "airlin",
        "gyroscopic" to "gyroscop", "adjustable" to "adjust", "defensible" to "defens", "irritant" to "irrit",
        "replacement" to "replac", "adjustment" to "adjust", "dependent" to "depend", "adoption" to "adopt",
        "homologou" to "homolog", "communism" to "commun", "activate" to "activ", "angulariti" to "angular",
        "homologous" to "homolog", "effective" to "effect", "bowdlerize" to "bowdler", "probate" to "probat",
        "rate" to "rate", "cease" to "ceas", "controll" to "control", "roll" to "roll",
        "generalizations" to "gener", "connections" to "connect", "parsing" to "pars", "parser" to "parser",
    )

    @Test
    fun `stems reference vocabulary`() {
        val actual = expected.keys.associateWith { stemmer.stem(it) }
        assertEquals(expected, actual)
    }

    @Test
    fun `leaves short and non-alphabetic words unchanged`() {
        for (word in listOf("is", "a", "utf8", "int32", "größe", "Parsing")) {
            assertEquals(word, stemmer.stem(word))
        }
    }
}
