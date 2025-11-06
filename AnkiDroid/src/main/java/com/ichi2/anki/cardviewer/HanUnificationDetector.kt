/*
 *  Copyright (c) 2025 Xenonnn4w <xenonnn4w@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer

import timber.log.Timber

/**
 * Detects potential Han Unification rendering issues in card content.
 *
 * Han Unification is a Unicode concept where characters from different CJK languages
 * (Chinese, Japanese, Korean) that have similar meanings are encoded using the same
 * code points. This can cause rendering issues when characters are displayed without
 * proper language attributes (e.g., Japanese Kanji rendering as Chinese Hanzi).
 *
 * This detector identifies CJK characters in HTML content that lack proper `lang`
 * attributes, which could lead to incorrect rendering.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Han_unification">Han Unification</a>
 */
object HanUnificationDetector {
    /**
     * Unicode ranges for CJK Unified Ideographs that are affected by Han Unification.
     * These ranges contain characters that may render differently depending on the
     * language context (zh, ja, ko).
     */
    private val CJK_UNIFIED_IDEOGRAPHS_RANGES =
        listOf(
            0x4E00..0x9FFF, // CJK Unified Ideographs
            0x3400..0x4DBF, // CJK Unified Ideographs Extension A
            0x20000..0x2A6DF, // CJK Unified Ideographs Extension B
            0x2A700..0x2B73F, // CJK Unified Ideographs Extension C
            0x2B740..0x2B81F, // CJK Unified Ideographs Extension D
            0x2B820..0x2CEAF, // CJK Unified Ideographs Extension E
            0x2CEB0..0x2EBEF, // CJK Unified Ideographs Extension F
            0x30000..0x3134F, // CJK Unified Ideographs Extension G
        )

    /**
     * Checks if a character is a CJK Unified Ideograph that could be affected by Han Unification.
     *
     * @param char The character to check
     * @return true if the character is in the CJK Unified Ideographs ranges
     */
    fun isCJKCharacter(char: Char): Boolean {
        val codePoint = char.code
        return CJK_UNIFIED_IDEOGRAPHS_RANGES.any { codePoint in it }
    }

    /**
     * Detects potential Han Unification issues in HTML content.
     *
     * This method scans the HTML content for CJK characters that are not properly
     * disambiguated with language attributes. It checks if CJK characters appear
     * in text nodes without appropriate `lang` attributes on their parent elements.
     *
     * @param html The HTML content to analyze
     * @param cardId The ID of the card being rendered (for logging purposes)
     * @return true if potential Han Unification issues were detected
     */
    fun detectIssues(
        html: String,
        cardId: Long,
    ): Boolean {
        var hasIssues = false
        val cjkCharacters = mutableListOf<Char>()

        // Extract text content from HTML (simple approach - outside of tags)
        // This is a basic implementation that looks for CJK characters in the content
        var inTag = false
        val textContent = StringBuilder()

        for (char in html) {
            when (char) {
                '<' -> inTag = true
                '>' -> inTag = false
                else -> if (!inTag) textContent.append(char)
            }
        }

        // Check for CJK characters in the text content
        for (char in textContent) {
            if (isCJKCharacter(char)) {
                cjkCharacters.add(char)
            }
        }

        if (cjkCharacters.isNotEmpty()) {
            // Check if the HTML has language attributes to disambiguate
            val hasLangAttribute =
                html.contains(Regex("""lang=["'](zh|ja|ko)[^"']*["']""", RegexOption.IGNORE_CASE))

            if (!hasLangAttribute) {
                hasIssues = true
                Timber.w(
                    "Han Unification issue detected in card %d: Found %d CJK characters without lang attribute. " +
                        "Characters: %s. Consider adding lang=\"ja\", lang=\"zh\", or lang=\"ko\" to disambiguate.",
                    cardId,
                    cjkCharacters.size,
                    cjkCharacters.distinct().take(10).joinToString(""),
                )
            }
        }

        return hasIssues
    }

    /**
     * Analyzes the HTML content and returns detailed information about potential issues.
     *
     * @param html The HTML content to analyze
     * @return A [DetectionResult] containing information about detected issues
     */
    fun analyze(html: String): DetectionResult {
        val cjkCharacters = mutableSetOf<Char>()

        // Extract text content from HTML
        var inTag = false
        val textContent = StringBuilder()

        for (char in html) {
            when (char) {
                '<' -> inTag = true
                '>' -> inTag = false
                else -> if (!inTag) textContent.append(char)
            }
        }

        // Collect CJK characters
        for (char in textContent) {
            if (isCJKCharacter(char)) {
                cjkCharacters.add(char)
            }
        }

        val hasLangAttribute =
            html.contains(Regex("""lang=["'](zh|ja|ko)[^"']*["']""", RegexOption.IGNORE_CASE))

        return DetectionResult(
            hasCJKCharacters = cjkCharacters.isNotEmpty(),
            hasLangAttribute = hasLangAttribute,
            cjkCharacterCount = cjkCharacters.size,
            sampleCharacters = cjkCharacters.take(10).toList(),
        )
    }

    /**
     * Result of Han Unification detection analysis.
     *
     * @property hasCJKCharacters Whether CJK characters were found in the content
     * @property hasLangAttribute Whether the content has language disambiguation attributes
     * @property cjkCharacterCount The number of unique CJK characters found
     * @property sampleCharacters A sample of the CJK characters found (up to 10)
     */
    data class DetectionResult(
        val hasCJKCharacters: Boolean,
        val hasLangAttribute: Boolean,
        val cjkCharacterCount: Int,
        val sampleCharacters: List<Char>,
    ) {
        /**
         * Whether this content has potential Han Unification rendering issues.
         */
        val hasIssue: Boolean
            get() = hasCJKCharacters && !hasLangAttribute
    }
}
