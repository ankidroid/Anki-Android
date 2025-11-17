/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.utils.message
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowNullExternalFilesDir::class])
class DeckPickerNoExternalFilesDirTest : RobolectricTest() {
    // TODO: @Config(sdk = [Build.VERSION_CODES.BAKLAVA]) // Bug 460912704 occurs on Android 16
    @Test
    fun `Fatal error is shown on fresh startup getExternalFilesDir is null`() {
        // Currently undefined if we should fail when PREF_COLLECTION_PATH is set
        //  but getExternalFilesDir returns null

        getPreferences().edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }

        startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
            val message = (ShadowDialog.getLatestDialog() as AlertDialog).message
            assertThat(message, containsString("getExternalFilesDir unexpectedly returned null"))
        }
    }
}

/**
 * A shadow which makes [Context.getExternalFilesDir] return `null`
 */
@Suppress("ProtectedInFinal", "unused")
@Implements(
    className = "android.app.ContextImpl",
    isInAndroidSdk = false,
)
class ShadowNullExternalFilesDir {
    @Implementation
    protected fun getExternalFilesDir(type: String?): File? = null
}
