package com.ichi2.anki;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ichi2.utils.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

import timber.log.Timber;

import static com.ichi2.anki.TemporaryModel.ChangeType.ADD;
import static com.ichi2.anki.TemporaryModel.ChangeType.DELETE;

@RunWith(AndroidJUnit4.class)
public class TemporaryModelTest extends RobolectricTest {

    @Test
    public void testTempModelStorage() throws Exception {
        Context context = getTargetContext();

        // Start off with clean state in the cache dir
        TemporaryModel.clearTempModelFiles();

        // Make sure save / retrieve works
        String tempModelPath = TemporaryModel.saveTempModel(context, new JSONObject("{foo: bar}"));
        Assert.assertNotNull("Saving temp model unsuccessful", tempModelPath);
        JSONObject tempModel = TemporaryModel.getTempModel(tempModelPath);
        Assert.assertNotNull("Temp model not read successfully", tempModel);
        Assert.assertEquals(new JSONObject("{foo: bar}").toString(), tempModel.toString());

        // Make sure clearing works
        Assert.assertEquals(1, TemporaryModel.clearTempModelFiles());
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.");
        Assert.assertNull("tempModel not correctly deleted", TemporaryModel.getTempModel(tempModelPath));
    }


    @Test
    public void testAddDeleteTracking() throws Exception {

        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first
        TemporaryModel tempModel = new TemporaryModel();

        tempModel.addTemplateChange(ADD, 3);
        Object[][] expected1 = {{3, ADD}};
        assertTemplateChangesEqual(expected1, tempModel.getTemplateChanges());
        tempModel.addTemplateChange(DELETE, 2);
        tempModel.addTemplateChange(DELETE, 1);
        Object[][] expected2 = {{3, ADD}, {2, DELETE}, {1, DELETE}};
        assertTemplateChangesEqual(expected2, tempModel.getTemplateChanges());
        tempModel.addTemplateChange(ADD, 2);
        Object[][] expected3 = {{3, ADD}, {2, DELETE}, {1, DELETE}, {2, ADD}};
        assertTemplateChangesEqual(expected3, tempModel.getTemplateChanges());

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        tempModel.addTemplateChange(DELETE, 1);
        Object[][] expected4 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected4, tempModel.getTemplateChanges());
        tempModel.addTemplateChange(ADD, 2);
        Object[][] expected5 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        assertTemplateChangesEqual(expected5, tempModel.getTemplateChanges());
        tempModel.addTemplateChange(DELETE, 2);
        Object[][] expected6 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected6, tempModel.getTemplateChanges());
    }


    private void assertTemplateChangesEqual(Object[][] expected, Serializable actual) {
        if (!(actual instanceof ArrayList)) {
            Assert.fail("actual array null or not the correct type");
        }
        Assert.assertEquals("arrays didn't have the same length?", expected.length, ((ArrayList) actual).size());
        for (int i = 0; i < expected.length; i++) {
            if (!(((ArrayList) actual).get(i) instanceof Object[])) {
                Assert.fail("actual array does not contain Object[] entries");
            }
            Object[] actualChange = (Object[]) ((ArrayList) actual).get(i);
            Assert.assertEquals("ordinal at " + i + " not correct?", expected[i][0], actualChange[0]);
            Assert.assertEquals("changeType at " + i + " not correct?", expected[i][1], actualChange[1]);
        }
    }
}
