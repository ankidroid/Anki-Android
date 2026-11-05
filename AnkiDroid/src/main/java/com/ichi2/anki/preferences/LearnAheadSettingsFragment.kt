// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

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
 * Screen allowing users to configure the learn ahead limit.
 *
 * Hosts the Compose [LearnAheadSettingsScreen].
 */
class LearnAheadSettingsFragment : Fragment() {
    private val viewModel: LearnAheadSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LearnAheadSettingsScreen(
                        isLoading = state.isLoading,
                        isEnabled = state.isEnabled,
                        limit = state.limit,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onSetMinutes = viewModel::setMinutes,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
}
