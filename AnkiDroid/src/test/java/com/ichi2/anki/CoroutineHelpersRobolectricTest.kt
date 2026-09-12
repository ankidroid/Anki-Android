// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.testutils.BackendEmulatingOpenConflict
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CoroutineHelpersRobolectricTest : RobolectricTest() {
    /**
     * A [StorageNotConfiguredException] escaping a coroutine means an activity raced or
     * outlived its [ensureStorageIsReady][com.ichi2.anki.startup.ensureStorageIsReady] check:
     * the user should be sent to the main entry point, which handles storage setup.
     */
    @Test
    fun `launchCatchingTask redirects to main entry point when storage is not configured`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()

        activity.launchCatchingTask { throw StorageNotConfiguredException() }
        advanceRobolectricLooper()

        assertTrue(activity.isFinishing, "activity should finish")
        val redirect = shadowOf(activity).nextStartedActivity
        assertNotNull(redirect, "the main entry point should be opened")
        assertEquals(IntentHandler::class.qualifiedName, redirect.component?.className)
    }

    /**
     * #21051: the collection lock is normally held by a second AnkiDroid install sharing the
     * AnkiDroid folder. The backend's 'Anki already open' text doesn't explain this on Android,
     * where the other app is invisible: [CollectionLockedException] carries guidance naming the
     * likely cause instead.
     */
    @Test
    fun `launchCatchingTask explains a locked collection`() =
        withLockedCollection {
            throwOnShowError = false
            val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
            activity.setTheme(R.style.Theme_Light)

            activity.launchCatchingTask { withCol { } }
            advanceRobolectricLooper()

            assertThat(getAlertDialogText(true), containsString("Advanced settings"))
        }

    /** See `launchCatchingTask explains a locked collection`: the ViewModel error funnel */
    @Test
    fun `launchCatching explains a locked collection`() =
        withLockedCollection {
            runTest {
                var message: String? = null

                launchCatching(errorMessageHandler = { message = it }) { withCol { } }.join()

                assertThat(message, containsString("Advanced settings"))
            }
        }

    /**
     * The locked-collection dialog offers Settings, opened at the Advanced screen, where
     * changing the 'AnkiDroid directory' resolves the conflict
     */
    @Test
    fun `a locked collection error links to Advanced settings`() =
        withLockedCollection {
            throwOnShowError = false
            val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
            activity.setTheme(R.style.Theme_Light)

            activity.launchCatchingTask { withCol { } }
            advanceRobolectricLooper()

            val helpButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
            assertEquals("Settings", helpButton.text.toString())

            helpButton.performClick()
            advanceRobolectricLooper()

            val settings = shadowOf(activity).nextStartedActivity
            assertNotNull(settings, "Advanced settings should be opened")
            assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
        }

    /** Emulates #21051: another AnkiDroid install holds the collection lock */
    private fun withLockedCollection(block: () -> Unit) {
        BackendEmulatingOpenConflict.enable()
        try {
            block()
        } finally {
            BackendEmulatingOpenConflict.disable()
        }
    }
}
