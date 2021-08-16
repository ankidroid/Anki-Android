/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.app.Application;
import android.content.Context;

import com.ichi2.testutils.BackendEmulatingOpenConflict;
import com.ichi2.testutils.BackupManagerTestUtilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowEnvironment;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class InitialActivityWithConflictTest extends RobolectricTest {
    @Before
    @Override
    public void setUp() {
        super.setUp();
        BackendEmulatingOpenConflict.enable();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        BackendEmulatingOpenConflict.disable();
    }

    @Test
    public void testInitialActivityResult() {
        try {
            setupForDatabaseConflict();

            InitialActivity.StartupFailure f = InitialActivity.getStartupFailureType(getTargetContext());

            assertThat("A conflict should be returned", f, is(InitialActivity.StartupFailure.DATABASE_LOCKED));
        } finally {
            setupForDefault();
        }
    }

    public static void setupForDatabaseConflict() {
        grantWritePermissions();
        ShadowEnvironment.setExternalStorageState("mounted");
    }

    public static void setupForValid(Context context) {
        grantWritePermissions();
        ShadowEnvironment.setExternalStorageState("mounted");
        BackupManagerTestUtilities.setupSpaceForBackup(context);
    }

    public static void setupForDefault() {
        revokeWritePermissions();
        ShadowEnvironment.setExternalStorageState("removed");
    }


    protected static void grantWritePermissions() {
        ShadowApplication app = Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
        app.grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    protected static void revokeWritePermissions() {
        ShadowApplication app = Shadows.shadowOf((Application) ApplicationProvider.getApplicationContext());
        app.denyPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE);
    }
}
