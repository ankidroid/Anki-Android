//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.api

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by rodrigobresan on 19/10/17.
 *
 *
 * In case of any questions, feel free to ask me
 *
 *
 * E-mail: rcbresan@gmail.com
 * Slack: bresan
 */
@RunWith(RobolectricTestRunner::class)
internal class ApiUtilsTest {
    @Test
    fun joinFieldsShouldJoinWhenListIsValid() {
        val fieldList = arrayOf("A", "B", "C")
        assertEquals("A" + DELIMITER + "B" + DELIMITER + "C", Utils.joinFields(fieldList))
    }

    @Test
    fun joinFieldsShouldReturnNullWhenListIsNull() {
        assertNull(Utils.joinFields(null))
    }

    @Test
    fun splitFieldsShouldSplitRightWhenStringIsValid() {
        val fieldList = "A" + DELIMITER + "B" + DELIMITER + "C"
        val output = Utils.splitFields(fieldList)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsValid() {
        val tags = setOf("A", "B", "C")
        assertEquals("A B C", Utils.joinTags(tags))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsNull() {
        assertEquals("", Utils.joinTags(null))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsEmpty() {
        assertEquals("", Utils.joinTags(emptySet()))
    }

    @Test
    fun joinTagsShouldReplaceSpacesWithUnderscores() {
        assertEquals("multi_word", Utils.joinTags(setOf("multi word")))
    }

    @Test
    fun joinTagsShouldReplaceSpacesInAllTags() {
        // Tags containing spaces must have spaces replaced by underscores because
        // Anki uses spaces as the tag separator in the joined string.
        val result = Utils.joinTags(setOf("hello world", "foo bar"))
        val resultTags = result.split(" ")
        assertEquals(2, resultTags.size)
        assert(resultTags.all { "_" in it || " " !in it }) {
            "Tags with spaces were not underscore-escaped: $result"
        }
    }

    @Test
    fun joinTagsShouldReturnSingleTagWithoutTrailingSpace() {
        assertEquals("only_one", Utils.joinTags(setOf("only one")))
    }

    @Test
    fun splitTagsShouldReturnNullWhenStringIsValid() {
        val tags = "A B C"
        val output = Utils.splitTags(tags)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    fun shouldGenerateProperCheckSum() {
        assertEquals(3533307532L, Utils.fieldChecksum("AnkiDroid"))
    }

    @Test
    fun fieldChecksumShouldHandleEmptyString() {
        // Should not throw and should be deterministic
        val checksum = Utils.fieldChecksum("")
        assertTrue(checksum >= 0, "Checksum of empty string should be non-negative")
        assertEquals(checksum, Utils.fieldChecksum(""), "Checksum should be deterministic")
    }

    @Test
    fun fieldChecksumShouldStripHtmlTags() {
        // Checksum is computed on stripped text, so "<b>AnkiDroid</b>" strips to "AnkiDroid"
        assertEquals(Utils.fieldChecksum("AnkiDroid"), Utils.fieldChecksum("<b>AnkiDroid</b>"))
    }

    @Test
    fun fieldChecksumShouldDecodeHtmlEntities() {
        // "&amp;" decodes to "&", so checksum of "&amp;amp" strips to "&amp" after entity decode
        // The key property: two strings that differ only in HTML encoding must produce the same checksum
        assertEquals(Utils.fieldChecksum("A&B"), Utils.fieldChecksum("A&amp;B"))
    }

    @Test
    fun splitTagsShouldHandleMultipleConsecutiveSpaces() {
        val tags = "A  B   C"
        val output = Utils.splitTags(tags)
        assertEquals(3, output.size)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    fun splitTagsShouldTrimLeadingAndTrailingWhitespace() {
        val tags = "  A B C  "
        val output = Utils.splitTags(tags)
        assertEquals(3, output.size)
        assertEquals("A", output[0])
    }

    @Test
    fun splitFieldsShouldHandleSingleField() {
        val output = Utils.splitFields("OnlyField")
        assertEquals(1, output.size)
        assertEquals("OnlyField", output[0])
    }

    @Test
    fun splitFieldsShouldPreserveEmptyFieldBetweenDelimiters() {
        val output = Utils.splitFields("A" + DELIMITER + "" + DELIMITER + "C")
        // dropLastWhile removes trailing empty strings but not middle ones
        assertEquals("A", output[0])
        assertEquals("", output[1])
        assertEquals("C", output[2])
    }

    companion object {
        // We need to keep a copy because a change to Utils.FIELD_SEPARATOR should break the tests
        private const val DELIMITER = "\u001F"
    }
}
