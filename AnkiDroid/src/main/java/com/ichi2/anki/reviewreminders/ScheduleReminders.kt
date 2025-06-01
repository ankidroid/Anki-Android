/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.libanki.DeckId

/**
 * The fragment users use to schedule review reminders.
 * Has three possible editing scopes it can be triggered with.
 * @see SchedulerScope
 */
class ScheduleReminders : Fragment(R.layout.fragment_schedule_reminders) {
    private lateinit var schedulerScope: SchedulerScope

    /**
     * The possible editing scope modes the ScheduleReminders fragment can be triggered with.
     * - GLOBAL_APP_WIDE: View and edit app-wide reminders, which are not specific to any deck.
     * - GLOBAL_DECK_SPECIFIC: View and edit all reminders that are specific to a deck, all in one screen.
     * - SINGLE_DECK_SPECIFIC: View and edit reminders for a single deck, specified by deck ID.
     */
    enum class SchedulerScope {
        GLOBAL_APP_WIDE,
        GLOBAL_DECK_SPECIFIC,
        SINGLE_DECK_SPECIFIC,
    }

    // The following are only used for the SINGLE_DECK_SPECIFIC scope:
    private var did = -1L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve arguments, update toolbar accordingly
        schedulerScope = requireArguments().getSerializableCompat<SchedulerScope>(EXTRAS_SCOPE_KEY) as SchedulerScope

        val toolbar =
            view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
                setTitle(
                    when (schedulerScope) {
                        SchedulerScope.GLOBAL_APP_WIDE -> "App-wide reminders"
                        SchedulerScope.GLOBAL_DECK_SPECIFIC -> "Deck-specific reminders"
                        SchedulerScope.SINGLE_DECK_SPECIFIC -> "Schedule reminders"
                    },
                )
                setNavigationOnClickListener {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }

        if (schedulerScope == SchedulerScope.SINGLE_DECK_SPECIFIC) {
            // Get the deck ID from arguments
            did = requireArguments().getLong(EXTRAS_DECK_ID_KEY)
            if (did == -1L) {
                throw IllegalArgumentException("Deck ID must be provided for SINGLE_DECK_SPECIFIC scope in ScheduleReminders")
            }
            // Retrieve the deck name and set it as the toolbar subtitle
            launchCatchingTask {
                toolbar.subtitle =
                    withCol {
                        decks.name(did)
                    }
            }
        }
    }

    companion object {
        private const val EXTRAS_SCOPE_KEY = "scope"
        private const val EXTRAS_DECK_ID_KEY = "did"

        /**
         * Creates an intent to start the ScheduleReminders activity.
         *
         * @param context The context to use for creating the intent.
         * @param schedulerScope The editing scope with which the ScheduleReminders should be opened.
         * @param did Deck ID for SINGLE_DECK_SPECIFIC editing scope. For all other editing scopes, this defaults to, and should be, null.
         * @return An Intent to start the ScheduleReminders activity.
         * @see SchedulerScope
         */
        fun getIntent(
            context: Context,
            schedulerScope: SchedulerScope,
            did: DeckId? = null,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                ScheduleReminders::class,
                Bundle().apply {
                    putSerializable(EXTRAS_SCOPE_KEY, schedulerScope)
                    putLong(EXTRAS_DECK_ID_KEY, did ?: -1L)
                },
            )
    }
}
