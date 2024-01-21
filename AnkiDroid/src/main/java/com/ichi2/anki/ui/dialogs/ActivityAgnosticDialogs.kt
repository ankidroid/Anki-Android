/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.ui.dialogs

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.ui.dialogs.ActivityAgnosticDialogs.Companion.MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY
import com.ichi2.anki.utils.getUserFriendlyErrorText
import com.ichi2.utils.copyToClipboard
import makeLinksClickable

// TODO BEFORE-RELEASE Dismiss the related notification, if any, when the dialog is dismissed.
//   Currently we are leaving the notification dangling after the migration has completed.
//   Dismissing the notification should not also dismiss, or prevent from showing, this dialog,
//   as notifications are too easily dismissed inadvertently.
//   On the other hand, dismissing this dialog should probably dismiss the notification,
//   as you have to press a button to dismiss the dialog.
class MigrationSucceededDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.migration_successful_message)
            .setMessage(R.string.migration__succeeded__message)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
            .create()
            .apply { setCanceledOnTouchOutside(false) }
    }

    companion object {
        fun show(activity: FragmentActivity) {
            MigrationSucceededDialogFragment()
                .show(activity.supportFragmentManager, "MigrationSucceededDialogFragment")
        }
    }
}

// TODO BEFORE-RELEASE Add a "Retry" button,
//   and also add instructions to fix the issue in case of easily fixable problems,
//   such as running out of disk space.
class MigrationFailedDialogFragment : DialogFragment() {
    @Suppress("MoveVariableDeclarationIntoWhen") // changesRolledBack
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val errorText = arguments?.getString(ERROR_TEXT_KEY) ?: ""
        val stacktrace = arguments?.getString(STACKTRACE_KEY) ?: ""
        val changesRolledBack = arguments?.getBoolean(CHANGES_ROLLED_BACK_KEY) ?: false

        val messageTemplateId = when (changesRolledBack) {
            true -> R.string.migration__failed__changes_rolled_back__message
            false -> R.string.migration__failed__changes_not_rolled_back__message
        }
        val helpUrl = getString(R.string.link_migration_failed_dialog_learn_more_en)
        val message = getString(messageTemplateId, errorText, helpUrl).parseAsHtml()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.migration__failed__title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> dismiss() }
            .setNegativeButton(R.string.feedback_copy_debug) { _, _ ->
                requireContext().copyToClipboard(
                    text = stacktrace,
                    failureMessageId = R.string.about_ankidroid_error_copy_debug_info
                )
            }
            .create()
            .apply {
                makeLinksClickable()
                setCanceledOnTouchOutside(false)
            }
    }

    companion object {
        private const val ERROR_TEXT_KEY = "error text"
        private const val STACKTRACE_KEY = "stacktrace"
        private const val CHANGES_ROLLED_BACK_KEY = "changes rolled back"

        const val TAG = "MigrationFailedDialogFragment"

        fun show(activity: FragmentActivity, errorText: CharSequence, stacktrace: String, changesRolledBack: Boolean) {
            MigrationFailedDialogFragment()
                .apply {
                    arguments = bundleOf(
                        ERROR_TEXT_KEY to errorText,
                        STACKTRACE_KEY to stacktrace,
                        CHANGES_ROLLED_BACK_KEY to changesRolledBack
                    )
                }
                .show(activity.supportFragmentManager, TAG)
        }
    }
}

/* ********************************************************************************************** */

/**
 * This allows showing a dialog in the currently started activity if one exists,
 * or, if the app is in background, scheduling it to be shown the next time any activity is started.
 */
class ActivityAgnosticDialogs private constructor(private val application: Application) {
    fun showOrScheduleStorageMigrationSucceededDialog() {
        if (currentlyStartedFragmentActivity != null) {
            MigrationSucceededDialogFragment.show(currentlyStartedFragmentActivity!!)
        } else {
            preferences.edit { putBoolean(MIGRATION_SUCCEEDED_DIALOG_PENDING_KEY, true) }
        }
    }

    fun showOrScheduleStorageMigrationFailedDialog(exception: Exception, changesRolledBack: Boolean) {
        val currentlyStartedFragmentActivity = this.currentlyStartedFragmentActivity
        val context = currentlyStartedFragmentActivity ?: application
        val errorText = context.getUserFriendlyErrorText(exception)
        val stacktrace = exception.stackTraceToString()

        if (currentlyStartedFragmentActivity != null) {
            MigrationFailedDialogFragment
                .show(currentlyStartedFragmentActivity, errorText, stacktrace, changesRolledBack)
        } else {
            preferences.edit {
                putString(MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY, errorText)
                putString(MIGRATION_FAILED_DIALOG_STACKTRACE_KEY, stacktrace)
                putBoolean(MIGRATION_FAILED_DIALOG_CHANGES_ROLLED_BACK_KEY, changesRolledBack)
            }
        }
    }

    private val preferences = getDefaultSharedPreferences(application)

    private val startedActivityStack = mutableListOf<Activity>()

    private val currentlyStartedFragmentActivity get() = startedActivityStack
        .filterIsInstance<FragmentActivity>()
        .lastOrNull()

    private fun registerCallbacks() {
        application.registerActivityLifecycleCallbacks(object : DefaultActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivityStack.add(activity) }
            override fun onActivityStopped(activity: Activity) { startedActivityStack.remove(activity) }
        })

        application.registerActivityLifecycleCallbacks(object : DefaultActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity !is FragmentActivity) return

                if (preferences.getBoolean(MIGRATION_SUCCEEDED_DIALOG_PENDING_KEY, false)) {
                    MigrationSucceededDialogFragment.show(activity)
                    preferences.edit { remove(MIGRATION_SUCCEEDED_DIALOG_PENDING_KEY) }
                }

                val errorText = preferences.getString(MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY, null)
                val stacktrace = preferences.getString(MIGRATION_FAILED_DIALOG_STACKTRACE_KEY, null)
                val changesRolledBack = preferences.getBoolean(MIGRATION_FAILED_DIALOG_CHANGES_ROLLED_BACK_KEY, false)
                if (errorText != null && stacktrace != null) {
                    MigrationFailedDialogFragment.show(activity, errorText, stacktrace, changesRolledBack)
                    preferences.edit {
                        remove(MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY)
                        remove(MIGRATION_FAILED_DIALOG_STACKTRACE_KEY)
                        remove(MIGRATION_FAILED_DIALOG_CHANGES_ROLLED_BACK_KEY)
                    }
                }
            }
        })
    }

    companion object {
        private const val MIGRATION_SUCCEEDED_DIALOG_PENDING_KEY = "migration succeeded dialog pending"

        const val MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY = "migration failed dialog error text"
        private const val MIGRATION_FAILED_DIALOG_STACKTRACE_KEY = "migration failed dialog stacktrace"
        private const val MIGRATION_FAILED_DIALOG_CHANGES_ROLLED_BACK_KEY = "migration failed dialog changes rolled back"

        fun register(application: Application) = ActivityAgnosticDialogs(application)
            .apply { registerCallbacks() }
    }
}

fun storageMigrationFailedDialogIsShownOrPending(activity: AppCompatActivity) =
    activity.supportFragmentManager.findFragmentByTag(MigrationFailedDialogFragment.TAG) != null ||
        activity.sharedPrefs()
        .getString(MIGRATION_FAILED_DIALOG_ERROR_TEXT_KEY, null) != null

interface DefaultActivityLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
