package com.ichi2.anki;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ichi2.utils.JSONObject;

import timber.log.Timber;


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
}
