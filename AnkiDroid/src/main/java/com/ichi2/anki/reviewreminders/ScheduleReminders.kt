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
import com.ichi2.libanki.DeckId
import timber.log.Timber

/**
 * Fragment for creating, viewing, editing, and deleting review reminders.
 */
class ScheduleReminders : Fragment(R.layout.fragment_schedule_reminders) {
    /**
     * If this flag is true, the user is viewing and editing all reminders in the entire app from a unified dashboard.
     * If this flag is false, the user is viewing and editing reminders for a single deck.
     */
    private var isInGlobalScope = false

    /**
     * This private variable is only used for the deck-specific editing scope.
     * In global scope, it is set to -1.
     */
    private var did: DeckId = -1L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar =
            view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
                setTitle(R.string.schedule_reminders_do_not_translate)
                setNavigationOnClickListener {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }

        // Retrieve arguments, update toolbar accordingly
        isInGlobalScope = requireArguments().getBoolean(EXTRAS_GLOBAL_SCOPE_KEY, true)
        if (!isInGlobalScope) {
            // Get the deck ID from arguments
            did = requireArguments().getLong(EXTRAS_DECK_ID_KEY)
            if (did == -1L) {
                throw IllegalArgumentException("A deck ID must be provided when using the deck-specific editing scope of ScheduleReminders")
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
        private const val EXTRAS_GLOBAL_SCOPE_KEY = "global_scope"
        private const val EXTRAS_DECK_ID_KEY = "did"

        /**
         * Creates an intent to start the ScheduleReminders fragment.
         * @param context
         * @param isInGlobalScope Whether the fragment should be opened in global or deck-specific scope.
         * @param did The ID of the deck being edited if [isInGlobalScope] is false. Defaults to null otherwise.
         * @return The new intent.
         */
        fun getIntent(
            context: Context,
            isInGlobalScope: Boolean,
            did: DeckId? = null,
        ): Intent =
            SingleFragmentActivity
                .getIntent(
                    context,
                    ScheduleReminders::class,
                    Bundle().apply {
                        putBoolean(EXTRAS_GLOBAL_SCOPE_KEY, isInGlobalScope)
                        putLong(EXTRAS_DECK_ID_KEY, did ?: -1L)
                    },
                ).apply {
                    Timber.i("launching ScheduleReminders for %s", did?.toString() ?: "all decks")
                }
    }
}
