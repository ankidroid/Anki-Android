// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.lifecycle.ViewModel
import com.ichi2.anki.multiprofile.ProfileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * State holder for [SwitchProfilesFragment]. Keeps the dialog visibility out
 * of the view layer so it survives configuration changes.
 */
class SwitchProfilesViewModel : ViewModel() {
    /** Profiles shown in the list. */
    // TODO: load from ProfileManager.getAllProfiles once ProfileManager is wired into the app
    val profiles: StateFlow<List<ProfileItem>>
        field = MutableStateFlow<List<ProfileItem>>(emptyList())

    val isAddProfileDialogVisible: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun showAddProfileDialog() {
        isAddProfileDialogVisible.value = true
    }

    fun dismissAddProfileDialog() {
        isAddProfileDialogVisible.value = false
    }

    /** Called when the user confirms a valid name in the add-profile dialog. */
    fun addProfile(name: ProfileName) {
        isAddProfileDialogVisible.value = false
        Timber.i("Add profile confirmed (%d chars)", name.value.length)
        // TODO: handle profile creation via ProfileManager.createNewProfile once
        //  ProfileManager is wired into the app
    }

    fun editProfile(profile: ProfileItem) {
        Timber.i("Edit profile requested: %s", profile.id)
        // TODO: implement profile rename via ProfileManager
    }

    fun deleteProfile(profile: ProfileItem) {
        Timber.i("Delete profile requested: %s", profile.id)
        // TODO: implement profile deletion via ProfileManager
    }
}
