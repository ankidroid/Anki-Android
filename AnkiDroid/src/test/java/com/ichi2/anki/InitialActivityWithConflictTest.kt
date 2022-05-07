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
package com.ichi2.anki

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.getStartupFailureType
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowEnvironment

@RunWith(AndroidJUnit4::class)
class InitialActivityWithConflictTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        BackendEmulatingOpenConflict.enable()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        BackendEmulatingOpenConflict.disable()
    }

    @Test
    fun testInitialActivityResult() {
        try {
            setupForDatabaseConflict()

            val f = getStartupFailureType(targetContext)

            assertThat("A conflict should be returned", f, equalTo(StartupFailure.DATABASE_LOCKED))
        } finally {
            setupForDefault()
        }
    }

    companion object {
        @JvmStatic
        fun setupForDatabaseConflict() {
            grantWritePermissions()
            ShadowEnvironment.setExternalStorageState("mounted")
        }

        @JvmStatic
        fun setupForValid(context: Context?) {
            grantWritePermissions()
            ShadowEnvironment.setExternalStorageState("mounted")
            BackupManagerTestUtilities.setupSpaceForBackup(context)
        }

        @JvmStatic
        fun setupForDefault() {
            revokeWritePermissions()
            ShadowEnvironment.setExternalStorageState("removed")
        }

        @JvmStatic
        fun grantWritePermissions() {
            val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
            app.grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        @JvmStatic
        fun revokeWritePermissions() {
            val app = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application)
            app.denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
