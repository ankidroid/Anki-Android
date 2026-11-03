// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.Reviewer
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.deckShortcutId
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.toIntent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProfileDeckLaunchTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    private val app get() = AnkiDroidApp.instance
    private lateinit var defaultManager: ProfileManager

    @Before
    override fun setUp() {
        super.setUp()
        defaultManager = requireNotNull(app.profileManager)
    }

    @After
    override fun tearDown() {
        runBlocking { CollectionManager.discardBackend() }
        app.profileManager = defaultManager
        super.tearDown()
    }

    @Test
    fun `a deck shortcut made in another profile opens its deck there`() {
        openProfile(createProfile("Work"))
        col.decks.rename(1L, "Work Spanish")

        val launched = launch(ReviewDeckDestination.ExternalLaunch(1L).toIntent(targetContext))

        assertTrue(launched.any { it.component?.className == Reviewer::class.java.name })
        assertEquals("Work Spanish", col.decks.getLegacy(col.decks.selected())!!.name)
    }

    @Test
    fun `a deck shortcut made in another profile does not open in Default`() {
        openProfile(createProfile("Work"))
        val shortcut = ReviewDeckDestination.ExternalLaunch(1L).toIntent(targetContext)
        openProfile(ProfileId.DEFAULT)

        val launched = launch(shortcut)

        assertFalse(launched.any { it.component?.className == Reviewer::class.java.name }, "Default's deck 1 is not Work's")
        assertEquals(targetContext.getString(CommonString.deck_in_other_profile, "Work"), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a reminder made in another profile does not open in Default`() {
        openProfile(createProfile("Work"))
        val reminderTap = IntentHandler.getReviewDeckIntent(targetContext, 1L)
        openProfile(ProfileId.DEFAULT)

        val launched = launch(reminderTap)

        assertFalse(launched.any { it.component?.className == Reviewer::class.java.name }, "Default's deck 1 is not Work's")
    }

    @Test
    fun `a reminder made in another profile opens its deck there`() {
        openProfile(createProfile("Work"))
        col.decks.rename(1L, "Work Spanish")

        val launched = launch(IntentHandler.getReviewDeckIntent(targetContext, 1L))

        assertTrue(launched.any { it.component?.className == Reviewer::class.java.name })
        assertEquals("Work Spanish", col.decks.getLegacy(col.decks.selected())!!.name)
    }

    @Test
    fun `a deck shortcut from a deleted profile says the deck is gone`() {
        val shortcut =
            ReviewDeckDestination
                .ExternalLaunch(1L)
                .toIntent(targetContext)
                .putExtra(IntentHandler.EXTRA_PROFILE_ID, "p_deleted")

        val launched = launch(shortcut)

        assertFalse(launched.any { it.component?.className == Reviewer::class.java.name })
        assertEquals(targetContext.getString(CommonString.deck_shortcut_doesnt_exist), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `deck intents made in Default are unchanged`() {
        val shortcut = ReviewDeckDestination.ExternalLaunch(1L).toIntent(targetContext)
        val reminderTap = IntentHandler.getReviewDeckIntent(targetContext, 1L)

        assertEquals(
            setOf(IntentHandler.EXTRA_DECK_ID),
            shortcut.extras!!.keySet(),
            "pinned shortcuts made before profiles must keep working",
        )
        assertEquals(setOf(IntentHandler.EXTRA_DECK_ID), reminderTap.extras!!.keySet())
    }

    @Test
    fun `deck shortcut ids in Default stay the deck id`() {
        assertEquals("1", targetContext.deckShortcutId(1L), "pinned shortcuts made before profiles must keep their id")
    }

    @Test
    fun `deck shortcuts in another profile do not reuse Default's ids`() {
        val work = createProfile("Work")
        openProfile(work)

        assertEquals("${work.value}_1", targetContext.deckShortcutId(1L), "Default's pinned shortcut for deck 1 would be replaced")
    }

    private fun createProfile(name: String) =
        defaultManager.createNewProfile((ProfileName.validate(name) as ProfileName.ValidationResult.Valid).name)

    private fun openProfile(profileId: ProfileId) {
        runBlocking { CollectionManager.discardBackend() }
        app.baseContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(PREF_LAST_ACTIVE_PROFILE_ID, profileId.value)
        }
        app.profileManager =
            ProfileManager.create(
                object : ContextWrapper(app.baseContext) {
                    override fun getApplicationContext(): Context? = null
                },
            )
    }

    private fun launch(intent: Intent): List<Intent> =
        Robolectric.buildActivity(IntentHandler::class.java, intent).use { controller ->
            controller.create()
            buildList {
                val activity = shadowOf(controller.get())
                while (true) add(activity.nextStartedActivity ?: break)
            }
        }
}
