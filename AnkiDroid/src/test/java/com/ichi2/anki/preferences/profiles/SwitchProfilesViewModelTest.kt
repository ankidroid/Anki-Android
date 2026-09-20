// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchProfilesViewModelTest {
    private lateinit var context: Context
    private lateinit var profileManager: ProfileManager

    private val prefs
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs.edit(commit = true) { clear() }
        profileManager = ProfileManager.create(context)
    }

    private fun viewModel() = SwitchProfilesViewModel(profileManager)

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
    fun `refresh picks up a profile added after the view model was created`() {
        val viewModel = viewModel()
        createProfile("Later")

        assertEquals(1, viewModel.profiles.value.size, "not visible until refreshed")
        viewModel.refresh()

        assertEquals(listOf("Default", "Later"), viewModel.profiles.value.map { it.name })
    }
}
