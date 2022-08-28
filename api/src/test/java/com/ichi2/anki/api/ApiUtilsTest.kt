//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.api

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.HashSet
import kotlin.test.assertNull

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
// TODO: @KotlinCleanup("replace Assert.assertEquals with kotlin.test.assertEquals")
@RunWith(RobolectricTestRunner::class)
class ApiUtilsTest {
    @Test
    fun joinFieldsShouldJoinWhenListIsValid() {
        val fieldList = arrayOf<String>("A", "B", "C")
        assertEquals("A" + delimiter + "B" + delimiter + "C", Utils.joinFields(fieldList))
    }

    @Test
    fun joinFieldsShouldReturnNullWhenListIsNull() {
        assertNull(Utils.joinFields(null))
    }

    @Test
    fun splitFieldsShouldSplitRightWhenStringIsValid() {
        val fieldList = "A" + delimiter + "B" + delimiter + "C"
        val output = Utils.splitFields(fieldList)
        assertEquals("A", output[0])
        assertEquals("B", output[1])
        assertEquals("C", output[2])
    }

    @Test
    // TODO: @KotlinCleanup("use mutableSetOf, use scope function")
    fun joinTagsShouldReturnEmptyStringWhenSetIsValid() {
        val set: MutableSet<String?> = HashSet()
        set.add("A")
        set.add("B")
        set.add("C")
        assertEquals("A B C", Utils.joinTags(set))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsNull() {
        assertEquals("", Utils.joinTags(null))
    }

    @Test
    fun joinTagsShouldReturnEmptyStringWhenSetIsEmpty() {
        assertEquals("", Utils.joinTags(HashSet()))
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

    companion object {
        private const val delimiter = "\u001F"
    }
}
