// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.SafetyCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SwitchProfilesViewModelTest {
    private lateinit var context: Context
    private lateinit var profileManager: ProfileManager

    private val prefs
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    private var restarts = 0

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        ioDispatcher = dispatcher
        context = ApplicationProvider.getApplicationContext()
        prefs.edit(commit = true) { clear() }
        profileManager = ProfileManager.create(context)
    }

    @After
    fun tearDown() {
        prefs.edit(commit = true) { clear() }
        Dispatchers.resetMain()
        ioDispatcher = Dispatchers.IO
    }

    private fun viewModel(vararg checks: SafetyCheck) =
        SwitchProfilesViewModel(
            profileManager = profileManager,
            switchGuard = ProfileSwitchGuard(profileManager, checks.toList()),
            restartApp = { restarts++ },
        )

    private val lastActiveProfileId: String?
        get() = prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null)

    private val validName: ProfileName
        get() = (ProfileName.validate("Work") as ValidationResult.Valid).name

    private fun createProfile(name: String) = profileManager.createNewProfile(nameOf(name))

    private fun nameOf(raw: String) = (ProfileName.validate(raw) as ValidationResult.Valid).name

    @Test
    fun `add-profile dialog is hidden initially`() {
        assertFalse(viewModel().isAddProfileDialogVisible.value)
    }

    @Test
    fun `showAddProfileDialog makes the dialog visible`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        assertTrue(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `dismissAddProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.dismissAddProfileDialog()
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.addProfile(validName)
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile lists the new profile`() {
        val viewModel = viewModel()

        viewModel.addProfile(nameOf("Work"))

        assertEquals(listOf("Default", "Work"), viewModel.profiles.value.map { it.name })
    }

    @Test
    fun `showRenameProfileDialog opens the dialog for that profile`() {
        val viewModel = viewModel()
        val default = viewModel.profiles.value.single()

        viewModel.showRenameProfileDialog(default)

        assertEquals(default, viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `dismissRenameProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single())

        viewModel.dismissRenameProfileDialog()

        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `renameProfile lists the new name and closes the dialog`() {
        createProfile("Work")
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single { it.name == "Work" })

        viewModel.renameProfile(nameOf("Office"))

        assertEquals(listOf("Default", "Office"), viewModel.profiles.value.map { it.name })
        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `tapping another profile asks before switching to it`() {
        createProfile("Work")
        val viewModel = viewModel()
        val work = viewModel.profiles.value.single { it.name == "Work" }

        viewModel.requestSwitch(work)

        assertEquals(work, viewModel.profileToSwitchTo.value)
    }

    @Test
    fun `tapping the active profile does not ask to switch`() {
        val viewModel = viewModel()

        viewModel.requestSwitch(viewModel.profiles.value.single())

        assertNull(viewModel.profileToSwitchTo.value)
    }

    @Test
    fun `dismissing the switch dialog keeps the current profile`() {
        createProfile("Work")
        val viewModel = viewModel()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })

        viewModel.dismissSwitchProfileDialog()

        assertNull(viewModel.profileToSwitchTo.value)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
        assertEquals(0, restarts)
    }

    @Test
    fun `confirming a switch saves the profile and restarts the app`() {
        val work = createProfile("Work")
        val viewModel = viewModel()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertNull(viewModel.profileToSwitchTo.value)
        assertEquals(work.value, lastActiveProfileId)
        assertEquals(1, restarts)
    }

    @Test
    fun `a blocked switch keeps the current profile and says why`() {
        val work = createProfile("Work")
        val viewModel = viewModel(SafetyCheck { BlockReason.SYNC_IN_PROGRESS })
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertTrue(viewModel.isSwitchBlocked.value)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
        assertEquals(0, restarts)
    }

    @Test
    fun `dismissing the blocked message hides it`() {
        val viewModel = viewModel(SafetyCheck { BlockReason.BACKUP_IN_PROGRESS })
        createProfile("Work")
        viewModel.refresh()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })
        viewModel.switchProfile()

        viewModel.dismissSwitchBlockedMessage()

        assertFalse(viewModel.isSwitchBlocked.value)
    }

    @Test
    fun `the list starts with the Default profile`() {
        assertEquals(listOf(ProfileId.DEFAULT), viewModel().profiles.value.map { it.id })
    }

    @Test
    fun `every registered profile is listed`() {
        val work = createProfile("Work")

        val listed = viewModel().profiles.value.map { it.id }

        assertEquals(setOf(ProfileId.DEFAULT, work), listed.toSet())
    }

    @Test
    fun `profiles are listed by name so the order is stable`() {
        createProfile("Zebra")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "Default", "Zebra"), names)
    }

    @Test
    fun `profiles are listed by name ignoring case`() {
        createProfile("bravo")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "bravo", "Default"), names)
    }

    @Test
    fun `refresh picks up a profile added after the view model was created`() {
        val viewModel = viewModel()
        createProfile("Later")

        assertEquals(1, viewModel.profiles.value.size, "not visible until refreshed")
        viewModel.refresh()

        assertEquals(listOf("Default", "Later"), viewModel.profiles.value.map { it.name })
    }
}
