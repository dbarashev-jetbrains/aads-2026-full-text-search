package org.jetbrains.edu.aads2026.tokenizer

/**
 * Reduces a lower-case word to its stem, so that inflected forms share one index term:
 * `parsing`, `parsed`, `parses` → `pars`.
 */
fun interface Stemmer {
    fun stem(word: String): String

    companion object {
        /** A stemmer that leaves words as they are. */
        val NONE = Stemmer { it }
    }
}

/**
 * The classic English stemmer by Martin Porter (1980), https://tartarus.org/martin/PorterStemmer/
 *
 * Only words consisting of ASCII lower-case letters are stemmed; anything else (words with digits,
 * non-Latin scripts, etc.) is returned unchanged.
 */
class PorterStemmer : Stemmer {
    override fun stem(word: String): String {
        if (word.length <= 2 || !word.all { it in 'a'..'z' }) return word
        return Run(word).stem()
    }

    /**
     * Mutable state of stemming one word. Follows the reference C implementation: [b] holds the word,
     * `b[0..k]` is the current stem, and [j] marks the end of the stem before a suffix found by [ends].
     */
    private class Run(word: String) {
        private val b = word.toCharArray()
        private var k = b.size - 1
        private var j = 0

        fun stem(): String {
            step1ab()
            if (k > 0) {
                step1c()
                step2()
                step3()
                step4()
                step5()
            }
            return String(b, 0, k + 1)
        }

        /** Whether `b[i]` is a consonant. `y` is a consonant at the start or after a vowel. */
        private fun cons(i: Int): Boolean = when (b[i]) {
            'a', 'e', 'i', 'o', 'u' -> false
            'y' -> i == 0 || !cons(i - 1)
            else -> true
        }

        /** The number of vowel-consonant sequences in `b[0..j]`: `[C](VC){m}[V]`. */
        private fun m(): Int {
            var n = 0
            var i = 0
            while (i <= j && cons(i)) i++
            while (true) {
                while (i <= j && !cons(i)) i++
                if (i > j) return n
                while (i <= j && cons(i)) i++
                n++
            }
        }

        private fun vowelInStem(): Boolean = (0..j).any { !cons(it) }

        private fun doubleConsonant(i: Int): Boolean = i >= 1 && b[i] == b[i - 1] && cons(i)

        /** Whether `b[i-2..i]` is consonant-vowel-consonant and the last consonant is not `w`, `x` or `y`. */
        private fun cvc(i: Int): Boolean =
            i >= 2 && cons(i) && !cons(i - 1) && cons(i - 2) && b[i] !in "wxy"

        private fun ends(s: String): Boolean {
            val start = k - s.length + 1
            if (start < 0) return false
            for (i in s.indices) if (b[start + i] != s[i]) return false
            j = start - 1
            return true
        }

        /** Replaces `b[j+1..k]` with [s]. Suffixes are never longer than what they replace. */
        private fun setTo(s: String) {
            for (i in s.indices) b[j + 1 + i] = s[i]
            k = j + s.length
        }

        private fun replaceIfMeasurePositive(s: String) {
            if (m() > 0) setTo(s)
        }

        /** Plurals and -ed/-ing: `caresses` → `caress`, `ponies` → `poni`, `motoring` → `motor`, `hopping` → `hop`. */
        private fun step1ab() {
            if (b[k] == 's') {
                when {
                    ends("sses") -> k -= 2
                    ends("ies") -> setTo("i")
                    b[k - 1] != 's' -> k--
                }
            }
            if (ends("eed")) {
                if (m() > 0) k--
            } else if ((ends("ed") || ends("ing")) && vowelInStem()) {
                k = j
                when {
                    ends("at") -> setTo("ate")
                    ends("bl") -> setTo("ble")
                    ends("iz") -> setTo("ize")
                    doubleConsonant(k) -> {
                        if (b[k] !in "lsz") k--
                    }
                    else -> {
                        j = k
                        if (m() == 1 && cvc(k)) {
                            setTo("e")
                        }
                    }
                }
            }
        }

        /** Terminal `y` to `i` when there is another vowel in the stem: `happy` → `happi`. */
        private fun step1c() {
            if (ends("y") && vowelInStem()) b[k] = 'i'
        }

        /** Double suffixes to single ones: `relational` → `relate`, `digitizer` → `digitize`. */
        private fun step2() {
            if (k < 1) return
            val rules = when (b[k - 1]) {
                'a' -> listOf("ational" to "ate", "tional" to "tion")
                'c' -> listOf("enci" to "ence", "anci" to "ance")
                'e' -> listOf("izer" to "ize")
                'l' -> listOf("bli" to "ble", "alli" to "al", "entli" to "ent", "eli" to "e", "ousli" to "ous")
                'o' -> listOf("ization" to "ize", "ation" to "ate", "ator" to "ate")
                's' -> listOf("alism" to "al", "iveness" to "ive", "fulness" to "ful", "ousness" to "ous")
                't' -> listOf("aliti" to "al", "iviti" to "ive", "biliti" to "ble")
                'g' -> listOf("logi" to "log")
                else -> return
            }
            applyFirstMatching(rules)
        }

        /** `-ic-`, `-full`, `-ness` etc.: `electrical` → `electric`, `hopeful` → `hope`. */
        private fun step3() {
            val rules = when (b[k]) {
                'e' -> listOf("icate" to "ic", "ative" to "", "alize" to "al")
                'i' -> listOf("iciti" to "ic")
                'l' -> listOf("ical" to "ic", "ful" to "")
                's' -> listOf("ness" to "")
                else -> return
            }
            applyFirstMatching(rules)
        }

        private fun applyFirstMatching(rules: List<Pair<String, String>>) {
            for ((suffix, replacement) in rules) {
                if (ends(suffix)) {
                    replaceIfMeasurePositive(replacement)
                    return
                }
            }
        }

        /** Removes `-ant`, `-ence` etc. in context `<c>vcvc<v>`: `adjustment` → `adjust`. */
        private fun step4() {
            if (k < 1) return
            val suffixes = when (b[k - 1]) {
                'a' -> listOf("al")
                'c' -> listOf("ance", "ence")
                'e' -> listOf("er")
                'i' -> listOf("ic")
                'l' -> listOf("able", "ible")
                'n' -> listOf("ant", "ement", "ment", "ent")
                'o' -> {
                    val found = (ends("ion") && j >= 0 && b[j] in "st") || ends("ou")
                    if (found && m() > 1) k = j
                    return
                }
                's' -> listOf("ism")
                't' -> listOf("ate", "iti")
                'u' -> listOf("ous")
                'v' -> listOf("ive")
                'z' -> listOf("ize")
                else -> return
            }
            if (suffixes.any { ends(it) } && m() > 1) k = j
        }

        /** Removes a final `-e` and reduces `-ll` to `-l` when the measure allows: `probate` → `probat`, `controll` → `control`. */
        private fun step5() {
            j = k
            if (b[k] == 'e') {
                val a = m()
                if (a > 1 || (a == 1 && !cvc(k - 1))) k--
            }
            if (b[k] == 'l' && doubleConsonant(k) && m() > 1) k--
        }
    }
}
