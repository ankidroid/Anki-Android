/*
 *  Copyright (c) 2024 Snowiee <xenonnn4w@gmail.com>
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

package com.ichi2.anki.utils

/**
 * Detects Han Unification issues in note field content.
 *
 * Han Unification is a Unicode feature where visually distinct Chinese, Japanese, and Korean
 * characters are assigned the same code point. This can cause Japanese kanji to render as
 * Chinese hanzi (or vice versa) depending on the system font, unless the HTML explicitly
 * specifies the language via the `lang` attribute.
 *
 * Example: U+9AA8 (骨) renders differently in Japanese vs Chinese without lang="ja" or lang="zh"
 *
 * This detector is used during note creation/editing to warn users about potential rendering
 * issues before they encounter them during review.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Han_unification">Han Unification on Wikipedia</a>
 */

object HanUnificationDetector {
    data class DetectionResult(
        val hasIssue: Boolean,
        val problematicCharacters: Set<Char> = emptySet(),
        val count: Int = 0,
    )

    /**
     * Common CJK characters known to have significant visual differences between
     * Japanese and Chinese rendering. This is a curated list for detection for now.
     */
    private val KNOWN_PROBLEMATIC_CHARS =
        setOf(
            '\u9AA8', // 骨 - bone
            '\u76F4', // 直 - straight
            '\u76F8', // 相 - mutual
            '\u79C1', // 私 - private
            '\u8FBC', // 込 - crowded
            '\u904E', // 過 - pass/exceed
            '\u98DF', // 食 - food/eat
            '\u6700', // 最 - most
            '\u5408', // 合 - combine
            '\u5009', // 倉 - warehouse
            '\u5C64', // 層 - layer
            '\u66FE', // 曾 - formerly
            '\u671D', // 朝 - morning/dynasty
            '\u6CBB', // 治 - govern
            '\u7384', // 玄 - mysterious
            '\u7DF4', // 練 - practice
            '\u7F72', // 署 - signature
            '\u89D2', // 角 - corner
            '\u8AAA', // 說 - speak/explain
            '\u8D08', // 贈 - give
            '\u9038', // 逸 - escape
            '\u9244', // 鉄 - iron
            '\u96C6', // 集 - gather
        )

    /**
     * Detects potential Han Unification issues in text content.
     *
     * Checks if the text contains known CJK characters that render differently
     * in Japanese vs Chinese contexts without proper language specification.
     *
     * @param text The text content to analyze (plain text, not HTML)
     * @return DetectionResult with issue status and details
     */
    fun detectIssue(text: String): DetectionResult {
        if (text.isEmpty()) {
            return DetectionResult(hasIssue = false)
        }

        val problematicChars =
            text
                .filter { char ->
                    KNOWN_PROBLEMATIC_CHARS.contains(char)
                }.toSet()

        return DetectionResult(
            hasIssue = problematicChars.isNotEmpty(),
            problematicCharacters = problematicChars,
            count = problematicChars.size,
        )
    }
}
