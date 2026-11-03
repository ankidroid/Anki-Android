// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.Reviewer
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.toIntent
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@RunWith(AndroidJUnit4::class)
class SwitchProfilesShortcutsReviewTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun releaseRestartLocks() {
        listOf(syncLock, mediaSyncLock, backupLock).filter { it.isLocked }.forEach { it.unlock() }
    }

    @Test
    fun `a shortcut opens the originating deck before switching`() {
        col.decks.rename(1L, "Private French")
        addBasicNote("French question", "French answer")
        val shortcut = pinShortcut()

        assertTrue(launchPinnedShortcut(shortcut).any { it.component?.className == Reviewer::class.java.name })
        assertEquals("Private French", col.decks.getLegacy(col.decks.selected())!!.name)
    }

    @Test
    fun `a Default shortcut must not review Works deck with the same id`() =
        runTest {
            val app = AnkiDroidApp.instance
            val originalManager = requireNotNull(app.profileManager)
            col.decks.rename(1L, "Private French")
            addBasicNote("French question", "French answer")
            // This is the exact destination stored by DeckPicker.createIcon.
            val shortcut = pinShortcut()
            val target = originalManager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val model =
                SwitchProfilesViewModel(originalManager, ProfileSwitchGuard(originalManager, emptyList())) {
                    safeRestartApp(app)
                    // Recreate the profile and backend as they would be after process restart.
                    CollectionManager.discardBackend()
                    releaseRestartLocks()
                    app.profileManager =
                        ProfileManager.create(
                            object : ContextWrapper(app.baseContext) {
                                override fun getApplicationContext(): Context? = null
                            },
                        )
                }
            try {
                model.requestSwitch(model.profiles.value.single { it.id == target })
                model.switchProfile()
                advanceUntilIdle()
                assertEquals(target, app.profileManager!!.activeProfileContext.profileId)
                assertEquals(0, col.noteCount(), "Work must have its own collection")
                col.decks.rename(1L, "Work Spanish")
                addBasicNote("Spanish question", "Spanish answer")

                val launched = launchPinnedShortcut(shortcut)
                val wrongProfileReviewed =
                    app.profileManager!!.activeProfileContext.profileId != ProfileId.DEFAULT &&
                        launched.any { it.component?.className == Reviewer::class.java.name }
                assertFalse(
                    wrongProfileReviewed,
                    "A Private French shortcut opened ${col.decks.getLegacy(col.decks.selected())!!.name} in Work",
                )
            } finally {
                releaseRestartLocks()
                CollectionManager.discardBackend()
                app.profileManager = originalManager
            }
        }

    private fun pinShortcut(): String {
        val manager = targetContext.getSystemService(ShortcutManager::class.java)
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        val shortcut =
            ShortcutInfo
                .Builder(targetContext, "1")
                .setShortLabel("Private French")
                .setIntent(ReviewDeckDestination.ExternalLaunch(1L).toIntent(targetContext))
                .build()
        assertTrue(manager.requestPinShortcut(shortcut, null))
        return shortcut.id
    }

    private fun launchPinnedShortcut(id: String): List<Intent> {
        val manager = targetContext.getSystemService(ShortcutManager::class.java)
        val shortcut = manager.pinnedShortcuts.single { it.id == id }
        return if (shortcut.isEnabled) launchShortcut(requireNotNull(shortcut.intent)) else emptyList()
    }

    private fun launchShortcut(intent: Intent): List<Intent> =
        Robolectric.buildActivity(IntentHandler::class.java, intent).use { controller ->
            controller.create()
            buildList {
                val activity = shadowOf(controller.get())
                while (true) add(activity.nextStartedActivity ?: break)
            }
        }
}
