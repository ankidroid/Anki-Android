// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.ProfileName
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class DeckPickerProfileLoadFailureTest : RobolectricTest() {
    private val registry
        get() = targetContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    private val DeckPicker.profileFailureDialogs: List<AlertDialog>
        get() = dialogsTitled(getString(CommonString.profile_load_failed_title, "Work"))

    @After
    fun resetProfileFailure() {
        ProfileManager.resetForTesting()
    }

    @Test
    fun `startup explains that a profile could not be opened`() {
        failToOpenProfile()

        deckPicker {
            assertEquals(1, profileFailureDialogs.size)
        }
    }

    @Test
    fun `the explanation stays when storage is also full`() {
        failToOpenProfile()

        deckPicker {
            assertEquals(1, dialogsTitled(getString(CommonString.storage_full_title)).size)
            assertEquals(1, profileFailureDialogs.size)
        }
    }

    @Test
    fun `Use Default opens the Default profile from the next start`() {
        failToOpenProfile()

        deckPicker {
            clickProfileFailureButton(DialogInterface.BUTTON_NEUTRAL)

            assertEquals(ProfileId.DEFAULT.value, registry.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
        }
    }

    @Test
    fun `OK tries the profile again on the next start`() {
        val work = failToOpenProfile()

        deckPicker {
            clickProfileFailureButton(DialogInterface.BUTTON_POSITIVE)

            assertEquals(work.value, registry.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
        }
    }

    @Test
    fun `the explanation survives a configuration change`() {
        failToOpenProfile()
        val controller = startDeckPicker()

        val recreated = controller.recreateDeckPicker()

        assertEquals(1, recreated.profileFailureDialogs.size)
    }

    @Test
    fun `a dismissed explanation does not come back`() {
        failToOpenProfile()
        val controller = startDeckPicker()
        controller.get().clickProfileFailureButton(DialogInterface.BUTTON_POSITIVE)

        val recreated = controller.recreateDeckPicker()

        assertEquals(0, recreated.profileFailureDialogs.size)
    }

    @Test
    fun `the explanation is shown once after the process is restored`() {
        failToOpenProfile()
        val savedState = Bundle()
        startDeckPicker()
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        assertNull(ProfileManager.createOrNull(targetContext))

        val restored =
            Robolectric
                .buildActivity(DeckPicker::class.java, Intent())
                .create(savedState)
                .start()
                .restoreInstanceState(savedState)
                .postCreate(savedState)
                .resume()
                .visible()
        saveControllerForCleanup(restored)
        restored.get().viewModel.awaitStartup()
        advanceRobolectricLooper()

        assertEquals(1, restored.get().profileFailureDialogs.size)
    }

    private fun startDeckPicker(): ActivityController<DeckPicker> {
        setIntroductionSlidesShown(true)
        return startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            advanceRobolectricLooper()
            assertEquals(1, it.get().profileFailureDialogs.size)
        }
    }

    private fun ActivityController<DeckPicker>.recreateDeckPicker(): DeckPicker {
        recreate()
        get().viewModel.awaitStartup()
        advanceRobolectricLooper()
        return get()
    }

    private fun DeckPicker.clickProfileFailureButton(which: Int) {
        profileFailureDialogs.single().getButton(which).performClick()
        advanceRobolectricLooper()
    }

    private fun DeckPicker.dialogsTitled(title: String): List<AlertDialog> =
        ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter {
            it.isShowing && it.title == title && it.context.findAnkiActivity() === this
        }
}

fun RobolectricTest.failToOpenProfile(
    profileName: String = "Work",
    defaultProfileName: String? = null,
): ProfileId {
    val manager = ProfileManager.create(targetContext)
    defaultProfileName?.let { manager.renameProfile(ProfileId.DEFAULT, ProfileName.fromTrustedSource(it)) }
    val profileId = manager.createNewProfile(ProfileName.fromTrustedSource(profileName))
    File(targetContext.filesDir.parentFile, profileId.value).writeText("a file where the profile folder belongs")
    targetContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE).edit(commit = true) {
        putString(PREF_LAST_ACTIVE_PROFILE_ID, profileId.value)
    }
    assertNull(ProfileManager.createOrNull(targetContext))
    return profileId
}
