// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * State holder for [SwitchProfilesFragment]. Keeps the dialog visibility out
 * of the view layer so it survives configuration changes.
 */
class SwitchProfilesViewModel(
    private val profileManager: ProfileManager,
) : ViewModel() {
    /** Profiles shown in the list. */
    val profiles: StateFlow<List<ProfileItem>>
        field = MutableStateFlow(profileManager.profileItems())

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
        // TODO: handle profile creation via ProfileManager.createNewProfile
    }

    fun editProfile(profile: ProfileItem) {
        Timber.i("Edit profile requested: %s", profile.id)
        // TODO: implement profile rename via ProfileManager
    }

    fun deleteProfile(profile: ProfileItem) {
        Timber.i("Delete profile requested: %s", profile.id)
        // TODO: implement profile deletion via ProfileManager
    }

    /** Reloads the list from the registry. */
    fun refresh() {
        profiles.value = profileManager.profileItems()
    }

    companion object {
        fun factory(profileManager: ProfileManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SwitchProfilesViewModel(profileManager)
                }
            }

        /**
         * The registry is backed by SharedPreferences, which has no defined order,
         * so sort by name to keep the list stable across launches.
         */
        private fun ProfileManager.profileItems(): List<ProfileItem> =
            getAllProfiles()
                .map { (id, metadata) -> ProfileItem(id = id, name = metadata.displayName.value) }
                .sortedBy { it.name }
    }
}
