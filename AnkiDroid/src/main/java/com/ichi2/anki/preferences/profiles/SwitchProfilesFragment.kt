/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.preferences.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.compose.theme.AnkiDroidTheme

/**
 * Lets the user switch between profiles.
 *
 * Thin host for the Compose [SwitchProfilesScreen]. Stays a Fragment because
 * preference_headers.xml launches it by class name.
 */
class SwitchProfilesFragment : Fragment() {
    private val viewModel: SwitchProfilesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
                    val isAddProfileDialogVisible by viewModel.isAddProfileDialogVisible.collectAsStateWithLifecycle()
                    SwitchProfilesScreen(
                        profiles = profiles,
                        isAddProfileDialogVisible = isAddProfileDialogVisible,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onAddProfileClick = viewModel::showAddProfileDialog,
                        onAddProfileConfirm = viewModel::addProfile,
                        onAddProfileDismiss = viewModel::dismissAddProfileDialog,
                        onEditProfile = viewModel::editProfile,
                        onDeleteProfile = viewModel::deleteProfile,
                    )
                }
            }
        }
}
