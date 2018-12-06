package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.Serializable;
import java.util.ArrayList;

import androidx.fragment.app.FragmentManager;
import androidx.test.platform.app.InstrumentationRegistry;
import timber.log.Timber;

import static com.ichi2.anki.CardTemplateEditor.ChangeType.ADD;
import static com.ichi2.anki.CardTemplateEditor.ChangeType.DELETE;


@RunWith(RobolectricTestRunner.class)
@Config(shadows = { ShadowViewPager.class })
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


    @Test
    public void testAddDeleteTracking() throws Exception {

        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first

        // We'll create an actual Activity here to use later for lifecycle persistence checks. The model we use is unimportant for the test.
        String modelName = "Basic (and reversed card)";
        JSONObject collectionBasicModelOriginal = getCurrentDatabaseModelCopy(modelName);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra("modelId", collectionBasicModelOriginal.getLong("id"));
        ActivityController templateEditorController = Robolectric.buildActivity(CardTemplateEditor.class, intent).create().start().resume().visible();
        CardTemplateEditor testEditor = (CardTemplateEditor) templateEditorController.get();


        testEditor.addTemplateChange(ADD, 3);
        Object[][] expected1 = {{3, ADD}};
        assertTemplateChangesEqual(expected1, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(DELETE, 2);
        testEditor.addTemplateChange(DELETE, 1);
        Object[][] expected2 = {{3, ADD}, {2, DELETE}, {1, DELETE}};
        assertTemplateChangesEqual(expected2, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(ADD, 2);
        Object[][] expected3 = {{3, ADD}, {2, DELETE}, {1, DELETE}, {2, ADD}};
        assertTemplateChangesEqual(expected3, testEditor.getTemplateChanges());

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        testEditor.addTemplateChange(DELETE, 1);
        Object[][] expected4 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected4, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(ADD, 2);
        Object[][] expected5 = {{2, DELETE}, {1, DELETE}, {1, ADD}, {2, ADD}};
        assertTemplateChangesEqual(expected5, testEditor.getTemplateChanges());
        testEditor.addTemplateChange(DELETE, 2);
        Object[][] expected6 = {{2, DELETE}, {1, DELETE}, {1, ADD}};
        assertTemplateChangesEqual(expected6, testEditor.getTemplateChanges());
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



// Robolectric is great, but ViewPager support is very incomplete.
// We have to avoid paging or we get exceptions
// https://github.com/robolectric/robolectric/issues/3698
class NonPagingCardTemplateEditor extends CardTemplateEditor {
    public static int pagerCount = 2;


    public void selectTemplate(int idx) { /* do nothing */ }


    public TemplatePagerAdapter getNewTemplatePagerAdapter(FragmentManager fm) {
        return new TestTemplatePagerAdapter(fm);
    }


    class TestTemplatePagerAdapter extends CardTemplateEditor.TemplatePagerAdapter {
        private TestTemplatePagerAdapter(FragmentManager fm) {
            super(fm);
        }


        public int getCount() {
            return pagerCount;
        }
    }
}
