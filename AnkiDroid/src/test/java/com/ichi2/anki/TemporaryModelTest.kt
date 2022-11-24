/****************************************************************************************
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.TemporaryModel.ChangeType.*
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.libanki.Model
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.IOException
import java.io.Serializable
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(AndroidJUnit4::class)
class TemporaryModelTest : RobolectricTest() {
    @Test
    @Throws(Exception::class)
    fun testTempModelStorage() {

        // Start off with clean state in the cache dir
        TemporaryModel.clearTempModelFiles()

        // Make sure save / retrieve works
        val tempModelPath = TemporaryModel.saveTempModel(targetContext, JSONObject("{\"foo\": \"bar\"}"))
        assertNotNull("Saving temp model unsuccessful", tempModelPath)
        val tempModel: JSONObject = TemporaryModel.getTempModel(tempModelPath!!)
        assertNotNull("Temp model not read successfully", tempModel)
        Assert.assertEquals(JSONObject("{\"foo\": \"bar\"}").toString(), tempModel.toString())

        // Make sure clearing works
        Assert.assertEquals(1, TemporaryModel.clearTempModelFiles().toLong())
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.")
        try {
            TemporaryModel.getTempModel(tempModelPath)
            Assert.fail("Should have caught an exception here because the file is missing")
        } catch (e: IOException) {
            // this is expected
        }
    }

    @Test
    fun testAddDeleteTracking() {

        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first
        val tempModel = TemporaryModel(Model("{ \"foo\": \"bar\" }"))

        tempModel.addTemplateChange(ADD, 3)
        val expected1 = arrayOf(arrayOf<Any>(3, ADD))
        // 3 templates and one change now
        assertTemplateChangesEqual(expected1, tempModel.templateChanges)
        assertTemplateChangesEqual(expected1, tempModel.adjustedTemplateChanges)
        Assert.assertArrayEquals(intArrayOf(3), tempModel.getDeleteDbOrds(3))

        tempModel.addTemplateChange(DELETE, 2)
        // 2 templates and two changes now
        val expected2 = arrayOf(arrayOf<Any>(3, ADD), arrayOf<Any>(2, DELETE))
        val adjExpected2 = arrayOf(arrayOf<Any>(2, ADD), arrayOf<Any>(2, DELETE))
        assertTemplateChangesEqual(expected2, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected2, tempModel.adjustedTemplateChanges)
        Assert.assertArrayEquals(intArrayOf(2, 4), tempModel.getDeleteDbOrds(3))

        tempModel.addTemplateChange(DELETE, 1)
        // 1 template and three changes now
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected3 = arrayOf(arrayOf<Any>(3, ADD), arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE))
        val adjExpected3 = arrayOf(arrayOf<Any>(1, ADD), arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE))
        assertTemplateChangesEqual(expected3, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected3, tempModel.adjustedTemplateChanges)

        tempModel.addTemplateChange(ADD, 2)
        // 2 templates and 4 changes now
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected4 = arrayOf(arrayOf<Any>(3, ADD), arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(2, ADD))
        val adjExpected4 = arrayOf(arrayOf<Any>(1, ADD), arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(2, ADD))
        assertTemplateChangesEqual(expected4, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected4, tempModel.adjustedTemplateChanges)

        // Make sure we can resurrect these changes across lifecycle
        val outBundle = tempModel.toBundle()
        assertTemplateChangesEqual(expected4, outBundle.getSerializableCompat("mTemplateChanges"))

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        tempModel.addTemplateChange(DELETE, 1)
        // 1 template and 3 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected5 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD))
        val adjExpected5 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD))
        assertTemplateChangesEqual(expected5, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected5, tempModel.adjustedTemplateChanges)

        tempModel.addTemplateChange(ADD, 2)
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected6 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD))
        val adjExpected6 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD))
        assertTemplateChangesEqual(expected6, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected6, tempModel.adjustedTemplateChanges)

        tempModel.addTemplateChange(ADD, 3)
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected7 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD), arrayOf<Any>(3, ADD))
        val adjExpected7 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD), arrayOf<Any>(3, ADD))
        assertTemplateChangesEqual(expected7, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected7, tempModel.adjustedTemplateChanges)

        tempModel.addTemplateChange(DELETE, 3)
        // 1 template and 3 changes now (two deletes cancelled out adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempModel.getDeleteDbOrds(3))
        val expected8 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD))
        val adjExpected8 = arrayOf(arrayOf<Any>(2, DELETE), arrayOf<Any>(1, DELETE), arrayOf<Any>(1, ADD), arrayOf<Any>(2, ADD))
        assertTemplateChangesEqual(expected8, tempModel.templateChanges)
        assertTemplateChangesEqual(adjExpected8, tempModel.adjustedTemplateChanges)
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertTemplateChangesEqual(expected: Array<Array<Any>>, actual: Serializable?) {
        if (actual !is ArrayList<*>) {
            Assert.fail("actual array null or not the correct type")
        }
        Assert.assertEquals("arrays didn't have the same length?", expected.size.toLong(), (actual as ArrayList<Array<Any?>?>).size.toLong())
        for (i in expected.indices) {
            if (actual[i] !is Array<Any?>) {
                Assert.fail("actual array does not contain Object[] entries")
            }
            val actualChange = (actual as ArrayList<Array<Any?>>)[i]
            Assert.assertEquals("ordinal at $i not correct?", expected[i][0], actualChange[0])
            Assert.assertEquals("changeType at $i not correct?", expected[i][1], actualChange[1])
        }
    }
}
