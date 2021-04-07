package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.testutils.BackendEmulatingOpenConflict;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class DBLockedTest extends  RobolectricTestBase {


    @Test
    public void databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityTest.setupForDefault();
            BackendEmulatingOpenConflict.enable();

            DeckPickerTest.DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerTest.DeckPickerEx.class, new Intent());

            // grant permissions
            InitialActivityTest.setupForDatabaseConflict();

            d.onStoragePermissionGranted();

            assertThat("A specific dialog for a conflict should be shown", d.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_DB_LOCKED));
        } finally {
            BackendEmulatingOpenConflict.disable();
            InitialActivityTest.setupForDefault();
        }
    }

    @Test
    public void databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sSentExceptionReportHack = false;
        try {
            BackendEmulatingOpenConflict.enable();
            InitialActivityTest.setupForDatabaseConflict();

            DeckPickerTest.DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerTest.DeckPickerEx.class, new Intent());

            assertThat("A specific dialog for a conflict should be shown", d.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_DB_LOCKED));

            assertThat("No exception reports should be thrown", AnkiDroidApp.sSentExceptionReportHack, is(false));
        } finally {
            BackendEmulatingOpenConflict.disable();
            InitialActivityTest.setupForDefault();
        }
    }
}

