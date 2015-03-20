package com.ichi2.anki.tests;

import android.test.AndroidTestCase;
import com.ichi2.anki.AnkiDroidApp;

/**
 * This test case verifies that the directory initialization works even if the app is not yet fully initialized.
 */
public class DirInitTest extends AndroidTestCase{
    public void testDirInit() {
        assertNull("AnkiDroidApp is already initialized, test is pointless",
                AnkiDroidApp.getInstance());
        assertFalse("Collection is already open, test is pointless",
                AnkiDroidApp.colIsOpen());
        assertNull("Collection is already open, test is pointless",
                AnkiDroidApp.getCol());
        String path = AnkiDroidApp.getCollectionPath(getContext());
        assertNotNull("Collection path is invalid", path);
        assertNotNull("Collection could not be opened", AnkiDroidApp.openCollection(getContext(), path));
    }

}
