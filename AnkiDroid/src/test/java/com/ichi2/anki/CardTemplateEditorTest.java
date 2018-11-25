package com.ichi2.anki;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import androidx.test.platform.app.InstrumentationRegistry;
import timber.log.Timber;


@RunWith(RobolectricTestRunner.class)
public class CardTemplateEditorTest extends RobolectricTest {

    @Test
    public void testTempModelStorage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Start off with clean state in the cache dir
        CardTemplateEditor.clearTempModelFiles(context);

        // Make sure save / retrieve works
        String tempModelPath = CardTemplateEditor.saveTempModel(context, new JSONObject("{foo: bar}"));
        Assert.assertNotNull("Saving temp model unsuccessful", tempModelPath);
        JSONObject tempModel = CardTemplateEditor.getTempModel(tempModelPath);
        Assert.assertNotNull("Temp model not read successfully", tempModel);
        Assert.assertEquals(new JSONObject("{foo: bar}").toString(), tempModel.toString());

        // Make sure clearing works
        Assert.assertEquals(1, CardTemplateEditor.clearTempModelFiles(context));
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.");
        Assert.assertNull("tempModel not correctly deleted", CardTemplateEditor.getTempModel(tempModelPath));
    }
}
