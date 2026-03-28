/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentReminderTroubleshootingBinding
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.utils.ext.setBackgroundTint
import com.ichi2.themes.Themes
import dev.androidbroadcast.vbpd.viewBinding

/** %alpha to use for the circular background of icons */
private const val BACKGROUND_ALPHA = 0.12f

class ReminderTroubleshootingFragment : Fragment(R.layout.fragment_reminder_troubleshooting) {
    private val viewModel: ReminderTroubleshootingViewModel by activityViewModels {
        reminderTroubleshootingViewModelFactory(requireContext())
    }

    private val binding by viewBinding(FragmentReminderTroubleshootingBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupSummary()
    }

    private fun setupSummary() {
        viewModel.state.launchCollectionInLifecycleScope { state ->
            val context = requireContext()
            val (iconRes, tintColor, summaryText) =
                when (state.summaryStatus) {
                    SummaryStatus.Error ->
                        Triple(
                            R.drawable.ic_cancel_24,
                            context.getColor(android.R.color.holo_red_dark),
                            "Reminders are unavailable.",
                        )

                    SummaryStatus.Warning ->
                        Triple(
                            R.drawable.ic_warning_24,
                            Themes.getColorFromAttr(context, R.attr.reminderTroubleshootingWarning),
                            "Reminders may not work correctly.",
                        )

                    SummaryStatus.Ok ->
                        Triple(
                            R.drawable.ic_check_circle_24,
                            Themes.getColorFromAttr(context, R.attr.reminderTroubleshootingOk),
                            "Your reminders should work as expected.",
                        )
                }
            binding.summaryIcon.setImageResource(iconRes)
            binding.summaryIcon.setColorFilter(tintColor)
            binding.summaryIconContainer.setBackgroundTint(tintColor, alpha = BACKGROUND_ALPHA)
            binding.summaryText.text = summaryText
        }
    }
}

/**
 * Shared factory for [ReminderTroubleshootingViewModel].
 *
 * Lives outside the ViewModel itself to keep the ViewModel free of `Context`. Both
 * [ScheduleReminders] and [ReminderTroubleshootingFragment] use this with
 * `by activityViewModels { … }` so they observe a single VM + repository instance.
 */
internal fun reminderTroubleshootingViewModelFactory(context: Context): ViewModelProvider.Factory {
    val appContext = context.applicationContext
    return viewModelFactory {
        initializer {
            ReminderTroubleshootingViewModel(ReminderTroubleshootingRepository(appContext))
        }
    }
}
