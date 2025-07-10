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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import timber.log.Timber

/**
 * Fragment for creating, viewing, editing, and deleting review reminders.
 */
class ScheduleReminders : Fragment(R.layout.fragment_schedule_reminders) {
    /**
     * Whether this fragment has been opened to edit all review reminders or just a specific deck's reminders.
     * @see ReviewReminderScope
     */
    private val scheduleRemindersScope: ReviewReminderScope by lazy {
        BundleCompat.getParcelable(
            requireArguments(),
            EXTRAS_SCOPE_KEY,
            ReviewReminderScope::class.java,
        ) ?: ReviewReminderScope.Global
    }

    private lateinit var toolbar: MaterialToolbar

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Set up toolbar
        toolbar = view.findViewById(R.id.toolbar)
        reloadToolbarText()
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
    }

    private fun reloadToolbarText() {
        Timber.d("Reloading toolbar text")
        toolbar.title = getString(R.string.schedule_reminders_do_not_translate)
        when (val scope = scheduleRemindersScope) {
            is ReviewReminderScope.Global -> {}
            is ReviewReminderScope.DeckSpecific ->
                launchCatchingTask {
                    toolbar.subtitle = scope.getDeckName()
                }
        }
    }

    companion object {
        /**
         * Arguments key for passing the [ReviewReminderScope] to open this fragment with.
         */
        private const val EXTRAS_SCOPE_KEY = "scope"

        /**
         * Creates an intent to start the ScheduleReminders fragment.
         * @param context
         * @param scope The editing scope of the ScheduleReminders fragment.
         * @return The new intent.
         */
        fun getIntent(
            context: Context,
            scope: ReviewReminderScope,
        ): Intent =
            SingleFragmentActivity
                .getIntent(
                    context,
                    ScheduleReminders::class,
                    Bundle().apply {
                        putParcelable(EXTRAS_SCOPE_KEY, scope)
                    },
                ).apply {
                    Timber.i("launching ScheduleReminders for $scope scope")
                }
    }
}
