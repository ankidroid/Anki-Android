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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class HanUnificationDetectorTest {
    @Test
    fun `test isCJKCharacter with Japanese Kanji`() {
        // Test with common Japanese Kanji characters that are affected by Han Unification
        assertThat(HanUnificationDetector.isCJKCharacter('骨'), equalTo(true)) // U+9AA8
        assertThat(HanUnificationDetector.isCJKCharacter('直'), equalTo(true))
        assertThat(HanUnificationDetector.isCJKCharacter('私'), equalTo(true))
        assertThat(HanUnificationDetector.isCJKCharacter('食'), equalTo(true))
    }

    @Test
    fun `test isCJKCharacter with non-CJK characters`() {
        // Test with ASCII and other non-CJK characters
        assertThat(HanUnificationDetector.isCJKCharacter('a'), equalTo(false))
        assertThat(HanUnificationDetector.isCJKCharacter('Z'), equalTo(false))
        assertThat(HanUnificationDetector.isCJKCharacter('1'), equalTo(false))
        assertThat(HanUnificationDetector.isCJKCharacter(' '), equalTo(false))
        assertThat(HanUnificationDetector.isCJKCharacter('!'), equalTo(false))
    }

    @Test
    fun `test isCJKCharacter with Hiragana and Katakana`() {
        // Hiragana and Katakana are not part of CJK Unified Ideographs
        assertThat(HanUnificationDetector.isCJKCharacter('あ'), equalTo(false)) // Hiragana
        assertThat(HanUnificationDetector.isCJKCharacter('カ'), equalTo(false)) // Katakana
    }

    @Test
    fun `test detectIssues with CJK characters and no lang attribute`() {
        val html = "<div>骨</div>"
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `test detectIssues with CJK characters and Japanese lang attribute`() {
        val html = """<div lang="ja">骨</div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with CJK characters and Chinese lang attribute`() {
        val html = """<div lang="zh">骨</div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with CJK characters and Korean lang attribute`() {
        val html = """<div lang="ko">骨</div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with no CJK characters`() {
        val html = "<div>Hello World</div>"
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with mixed content`() {
        val html = "<div>Hello 骨 World</div>"
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `test detectIssues with multiple CJK characters and no lang`() {
        val html = "<div>骨直私食</div>"
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `test analyze with CJK characters and no lang attribute`() {
        val html = "<div>骨直</div>"
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.hasCJKCharacters, equalTo(true))
        assertThat(result.hasLangAttribute, equalTo(false))
        assertThat(result.hasIssue, equalTo(true))
        assertThat(result.cjkCharacterCount, equalTo(2))
    }

    @Test
    fun `test analyze with CJK characters and lang attribute`() {
        val html = """<div lang="ja">骨直</div>"""
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.hasCJKCharacters, equalTo(true))
        assertThat(result.hasLangAttribute, equalTo(true))
        assertThat(result.hasIssue, equalTo(false))
        assertThat(result.cjkCharacterCount, equalTo(2))
    }

    @Test
    fun `test analyze with no CJK characters`() {
        val html = "<div>English text only</div>"
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.hasCJKCharacters, equalTo(false))
        assertThat(result.hasLangAttribute, equalTo(false))
        assertThat(result.hasIssue, equalTo(false))
        assertThat(result.cjkCharacterCount, equalTo(0))
    }

    @Test
    fun `test analyze sample characters extraction`() {
        val html = "<div>骨直私食</div>"
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.sampleCharacters.size, equalTo(4))
        assertThat(result.sampleCharacters.contains('骨'), equalTo(true))
        assertThat(result.sampleCharacters.contains('直'), equalTo(true))
        assertThat(result.sampleCharacters.contains('私'), equalTo(true))
        assertThat(result.sampleCharacters.contains('食'), equalTo(true))
    }

    @Test
    fun `test analyze with duplicate CJK characters`() {
        val html = "<div>骨骨骨</div>"
        val result = HanUnificationDetector.analyze(html)
        
        // Should count unique characters
        assertThat(result.cjkCharacterCount, equalTo(1))
        assertThat(result.sampleCharacters.size, equalTo(1))
    }

    @Test
    fun `test detectIssues with lang attribute in nested element`() {
        val html = """<div><span lang="ja">骨</span></div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with complex HTML structure`() {
        val html = """
            <div>
                <p>This is some text</p>
                <div>骨</div>
                <p>More text</p>
            </div>
        """.trimIndent()
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `test detectIssues with HTML entities and CJK`() {
        val html = """<div>&lt;骨&gt;</div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `test isCJKCharacter with edge case characters`() {
        // Test characters at the boundaries of CJK ranges
        assertThat(HanUnificationDetector.isCJKCharacter('\u4E00'), equalTo(true)) // First char in range
        assertThat(HanUnificationDetector.isCJKCharacter('\u9FFF'), equalTo(true)) // Last char in main range
        assertThat(HanUnificationDetector.isCJKCharacter('\u4DFF'), equalTo(false)) // Just outside range
    }

    @Test
    fun `test analyze with real-world example from issue`() {
        // Example from the GitHub issue
        val html = """<td>骨</td>"""
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.hasCJKCharacters, equalTo(true))
        assertThat(result.hasIssue, equalTo(true))
    }

    @Test
    fun `test analyze with disambiguated example from issue`() {
        // Example from the GitHub issue with proper lang attribute
        val html = """<td lang="ja">骨</td>"""
        val result = HanUnificationDetector.analyze(html)
        
        assertThat(result.hasCJKCharacters, equalTo(true))
        assertThat(result.hasIssue, equalTo(false))
    }

    @Test
    fun `test detectIssues with single quotes in lang attribute`() {
        val html = """<div lang='ja'>骨</div>"""
        val result = HanUnificationDetector.detectIssues(html, 12345L)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `test detectIssues with lang attribute variants`() {
        // Test with language variants
        val htmlZhHans = """<div lang="zh-Hans">骨</div>"""
        assertThat(HanUnificationDetector.detectIssues(htmlZhHans, 12345L), equalTo(false))
        
        val htmlZhHant = """<div lang="zh-Hant">骨</div>"""
        assertThat(HanUnificationDetector.detectIssues(htmlZhHant, 12345L), equalTo(false))
        
        val htmlZhHK = """<div lang="zh-Hant-HK">骨</div>"""
        assertThat(HanUnificationDetector.detectIssues(htmlZhHK, 12345L), equalTo(false))
    }
}
