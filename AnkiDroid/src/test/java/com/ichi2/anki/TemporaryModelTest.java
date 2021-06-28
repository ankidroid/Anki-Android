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

package com.ichi2.anki;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.os.Bundle;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ichi2.libanki.Model;
import com.ichi2.utils.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import timber.log.Timber;

import static com.ichi2.anki.TemporaryModel.ChangeType.ADD;
import static com.ichi2.anki.TemporaryModel.ChangeType.DELETE;

@RunWith(AndroidJUnit4.class)
public class TemporaryModelTest extends RobolectricTest {

    @Test
    public void testTempModelStorage() throws Exception {

        // Start off with clean state in the cache dir
        TemporaryModel.clearTempModelFiles();

        // Make sure save / retrieve works
        String tempModelPath = TemporaryModel.saveTempModel(getTargetContext(), new JSONObject("{\"foo\": \"bar\"}"));
        Assert.assertNotNull("Saving temp model unsuccessful", tempModelPath);
        JSONObject tempModel = TemporaryModel.getTempModel(tempModelPath);
        Assert.assertNotNull("Temp model not read successfully", tempModel);
        Assert.assertEquals(new JSONObject("{\"foo\": \"bar\"}").toString(), tempModel.toString());

        // Make sure clearing works
        Assert.assertEquals(1, TemporaryModel.clearTempModelFiles());
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.");
        try {
            TemporaryModel.getTempModel(tempModelPath);
            Assert.fail("Should have caught an exception here because the file is missing");
        } catch (IOException e) {
            // this is expected
        }
    }


    @Test
    public void testAddDeleteTracking() {

        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first
        TemporaryModel tempModel = new TemporaryModel(new Model("{ \"foo\": \"bar\" }"));

        tempModel.addTemplateChange(ADD, 3);
        Object[][] expected1 = {{3, ADD}};
        // 3 templates and one change now
        assertTemplateChangesEqual(expected1, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(expected1, tempModel.getAdjustedTemplateChanges());
        Assert.assertArrayEquals(new int[]{3}, tempModel.getDeleteDbOrds(3));

        tempModel.addTemplateChange(DELETE, 2);
        // 2 templates and two changes now
        Object[][] expected2 = {{3, ADD}, {2, DELETE}};
        Object[][] adjExpected2 = {{2, ADD}, {2, DELETE}};
        assertTemplateChangesEqual(expected2, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected2, tempModel.getAdjustedTemplateChanges());
        Assert.assertArrayEquals(new int[]{2, 4}, tempModel.getDeleteDbOrds(3));

        tempModel.addTemplateChange(DELETE, 1);
        // 1 template and three changes now
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected3 = {{3, ADD}, {2, DELETE}, {1, DELETE}};
        Object[][] adjExpected3 = {{1, ADD}, {2, DELETE}, {1, DELETE}};
        assertTemplateChangesEqual(expected3, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected3, tempModel.getAdjustedTemplateChanges());

        tempModel.addTemplateChange(ADD, 2);
        // 2 templates and 4 changes now
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected4 = {{3, ADD}, {2, DELETE}, {1, DELETE}, {2, ADD}};
        Object[][] adjExpected4 = {{1, ADD}, {2, DELETE}, {1, DELETE}, {2, ADD}};
        assertTemplateChangesEqual(expected4, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected4, tempModel.getAdjustedTemplateChanges());

        // Make sure we can resurrect these changes across lifecycle
        Bundle outBundle = tempModel.toBundle();
        assertTemplateChangesEqual(expected4, outBundle.getSerializable("mTemplateChanges"));

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        tempModel.addTemplateChange(DELETE, 1);
        // 1 template and 3 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected5 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        Object[][] adjExpected5 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected5, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected5, tempModel.getAdjustedTemplateChanges());

        tempModel.addTemplateChange(ADD, 2);
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected6 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        Object[][] adjExpected6 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        assertTemplateChangesEqual(expected6, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected6, tempModel.getAdjustedTemplateChanges());

        tempModel.addTemplateChange(ADD, 3);
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected7 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}, {3, ADD}};
        Object[][] adjExpected7 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}, {3, ADD}};
        assertTemplateChangesEqual(expected7, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected7, tempModel.getAdjustedTemplateChanges());

        tempModel.addTemplateChange(DELETE, 3);
        // 1 template and 3 changes now (two deletes cancelled out adds)
        Assert.assertArrayEquals(new int[]{2, 1, 5}, tempModel.getDeleteDbOrds(3));
        Object[][] expected8 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        Object[][] adjExpected8 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        assertTemplateChangesEqual(expected8, tempModel.getTemplateChanges());
        assertTemplateChangesEqual(adjExpected8, tempModel.getAdjustedTemplateChanges());
    }


    private void assertTemplateChangesEqual(Object[][] expected, Serializable actual) {
        if (!(actual instanceof ArrayList)) {
            Assert.fail("actual array null or not the correct type");
        }
        Assert.assertEquals("arrays didn't have the same length?", expected.length, ((ArrayList<Object[]>) actual).size());
        for (int i = 0; i < expected.length; i++) {
            if (!(((ArrayList<Object[]>) actual).get(i) instanceof Object[])) {
                Assert.fail("actual array does not contain Object[] entries");
            }
            Object[] actualChange = ((ArrayList<Object[]>) actual).get(i);
            Assert.assertEquals("ordinal at " + i + " not correct?", expected[i][0], actualChange[0]);
            Assert.assertEquals("changeType at " + i + " not correct?", expected[i][1], actualChange[1]);
        }
    }
}