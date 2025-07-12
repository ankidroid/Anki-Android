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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.showError
import com.ichi2.anki.withProgress
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import timber.log.Timber
import kotlin.reflect.KClass

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

    private lateinit var database: ReviewRemindersDatabase
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

        // Set up database
        database = ReviewRemindersDatabase()

        // Attempt a database read: for migration demonstration purposes
        // TODO: delete this once normal RecyclerView usage is pushed for review
        launchCatchingTask {
            catchDatabaseExceptions {
                database.getAllAppWideReminders()
            }
        }
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

    /**
     * Handles and tries to recover from possible [SerializationException]s and [IllegalArgumentException]s that
     * can be thrown by [ReviewRemindersDatabase] methods. Should wrap every call to [ReviewRemindersDatabase] methods.
     *
     * To learn more about the schema migration fallback, see [ReviewReminder]. This method will try every possible
     * old [ReviewReminder] schema in [oldReviewReminderSchemasForMigration] until one succeeds or all fail,
     * reverting any changes made to review reminder SharedPreferences to their original state after each failure.
     *
     * We need to opt into an experimental serialization API feature because we are determining classes to deserialize
     * dynamically rather than at compile-time. The possible schemas to deserialize from are inputted dynamically so that unit tests are possible.
     */
    @OptIn(InternalSerializationApi::class)
    private suspend fun <T> catchDatabaseExceptions(block: () -> T): T? {
        val remainingMigrationSchema = oldReviewReminderSchemasForMigration.toMutableList()
        var nextSchemaToTry: KClass<out OldReviewReminderSchema>? = null
        val sharedPrefsBackup = database.getAllReviewReminderSharedPrefsAsMap()

        val restoreAndPossiblyTryNextSchema: (String, Exception) -> Boolean = { errorMessage, e ->
            Timber.d("Json error encountered, reverting review reminder SharedPreferences")
            database.deleteAllReviewReminderSharedPrefs()
            AnkiDroidApp.sharedPrefs().edit {
                sharedPrefsBackup.forEach { (key, value) ->
                    putString(key, value.toString())
                }
            }
            if (remainingMigrationSchema.isNotEmpty()) {
                nextSchemaToTry = remainingMigrationSchema.removeAt(remainingMigrationSchema.lastIndex)
                Timber.d("Attempting ReviewReminder schema migration: $nextSchemaToTry")
                true
            } else {
                Timber.d("No more ReviewReminder migration schemas are available, showing error dialog")
                showError(requireContext(), "$errorMessage $e")
                false
            }
        }

        while (true) {
            try {
                Timber.d("Attempting ReviewRemindersDatabase operation")
                val result =
                    withProgress {
                        nextSchemaToTry?.let { database.attemptSchemaMigration(it.serializer()) }
                        block()
                    }
                return result
            } catch (e: SerializationException) {
                if (restoreAndPossiblyTryNextSchema(SERIALIZATION_ERROR_MESSAGE, e)) continue else return null
            } catch (e: IllegalArgumentException) {
                if (restoreAndPossiblyTryNextSchema(DATA_TYPE_ERROR_MESSAGE, e)) continue else return null
            }
        }
    }

    companion object {
        /**
         * Arguments key for passing the [ReviewReminderScope] to open this fragment with.
         */
        private const val EXTRAS_SCOPE_KEY = "scope"

        private const val SERIALIZATION_ERROR_MESSAGE =
            "Something went wrong. A serialization error was encountered while working with review reminders."
        private const val DATA_TYPE_ERROR_MESSAGE =
            "Something went wrong. An unexpected data type was found while working with review reminders."

        /**
         * A list of all old [ReviewReminder] schemas that [ScheduleReminders.catchDatabaseExceptions]
         * will attempt to migrate old review reminders in SharedPreferences from.
         * @see [ReviewReminder]
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        var oldReviewReminderSchemasForMigration: List<KClass<out OldReviewReminderSchema>> = listOf()

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
