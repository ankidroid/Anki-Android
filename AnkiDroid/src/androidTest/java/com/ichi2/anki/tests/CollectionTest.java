package com.ichi2.anki.tests;

import android.Manifest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.CollectionHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

/**
 * This test case verifies that the directory initialization works even if the app is not yet fully initialized.
 */
@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class CollectionTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Test
    public void testOpenCollection() {
        assertNotNull("Collection could not be opened",
                CollectionHelper.getInstance().getCol(InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }
}