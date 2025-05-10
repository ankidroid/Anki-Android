/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.preferences.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import anki.config.Preferences.BackupLimits
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPrefs
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.managespace.CollectionDirectoryProvider
import com.ichi2.anki.ui.windows.managespace.collectionDirectoryExists
import com.ichi2.anki.ui.windows.managespace.ensureCanWriteToOrCreateCollectionDirectory
import com.ichi2.anki.utils.getUserFriendlyErrorText
import com.ichi2.preferences.HtmlHelpPreference
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat
import com.ichi2.preferences.NumberRangePreferenceCompat.ShouldShowDialog
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

private val backupManager = BackupManager()

sealed interface State {
    data object Fetching : State

    class Fetched(
        val backupLimits: BackupLimits,
    ) : State

    sealed interface Error : State {
        data object NoCollection : Error

        class Exception(
            val exception: kotlin.Exception,
        ) : Error
    }
}

class BackupLimitsViewModel :
    ViewModel(),
    CollectionDirectoryProvider {
    override val collectionDirectory = CollectionManager.getCollectionDirectory()

    val flowOfState = MutableStateFlow<State>(State.Fetching)

    fun launchFetchingOfBackupLimits() =
        viewModelScope.launch {
            flowOfState.emit(
                try {
                    ensureCanWriteToOrCreateCollectionDirectory()
                    if (!collectionDirectoryExists()) {
                        State.Error.NoCollection
                    } else {
                        val backupLimits = withCol { backend.getPreferences().backups }
                        State.Fetched(backupLimits)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    State.Error.Exception(e)
                },
            )
        }

    suspend fun updateBackupLimits(block: BackupLimits.Builder.() -> Unit) {
        withCol {
            val preferences = backend.getPreferences()
            val backups =
                preferences.backups
                    .toBuilder()
                    .apply(block)
                    .build()
            backend.setPreferences(preferences.toBuilder().setBackups(backups).build())
        }

        launchFetchingOfBackupLimits()
    }
}

/**
 * A backup limit preferences presenter for [PreferenceFragmentCompat].
 * Adds preferences from [R.xml.preferences_backup_limits] to current hierarchy, and manages them.
 *
 * To use, simply say
 *
 *     val backupLimitsPresenter = BackupLimitsPresenter()
 *     backupLimitsPresenter.observeLifecycle()
 *
 * To refresh in case of changes on disk or in schema, say
 *
 *     backupLimitsPresenter.refresh()
 */
class BackupLimitsPresenter(
    private val fragment: PreferenceFragmentCompat,
) : DefaultLifecycleObserver {
    private val viewModel: BackupLimitsViewModel by fragment.viewModels()

    private lateinit var backupsHelpPreference: HtmlHelpPreference
    private lateinit var minutesBetweenAutomaticBackupsPreference: IncrementerNumberRangePreferenceCompat
    private lateinit var dailyBackupsToKeepPreference: IncrementerNumberRangePreferenceCompat
    private lateinit var weeklyBackupsToKeepPreference: IncrementerNumberRangePreferenceCompat
    private lateinit var monthlyBackupsToKeepPreference: IncrementerNumberRangePreferenceCompat
    private lateinit var backupFolderPreference: Preference

    private val pickFolderLauncher =
        fragment.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            handleBackupFolder(uri)
        }

    override fun onCreate(owner: LifecycleOwner) {
        backupsHelpPreference = fragment.requirePreference(R.string.pref_backups_help_key)
        minutesBetweenAutomaticBackupsPreference = fragment.requirePreference(R.string.pref_minutes_between_automatic_backups_key)
        dailyBackupsToKeepPreference = fragment.requirePreference(R.string.pref_daily_backups_to_keep_key)
        weeklyBackupsToKeepPreference = fragment.requirePreference(R.string.pref_weekly_backups_to_keep_key)
        monthlyBackupsToKeepPreference = fragment.requirePreference(R.string.pref_monthly_backups_to_keep_key)
        backupFolderPreference = fragment.requirePreference(R.string.pref_backup_directory_key)

        minutesBetweenAutomaticBackupsPreference
            .launchWhenChanged<Int> { viewModel.updateBackupLimits { minimumIntervalMins = it } }
        dailyBackupsToKeepPreference
            .launchWhenChanged<Int> { viewModel.updateBackupLimits { daily = it } }
        weeklyBackupsToKeepPreference
            .launchWhenChanged<Int> { viewModel.updateBackupLimits { weekly = it } }
        monthlyBackupsToKeepPreference
            .launchWhenChanged<Int> { viewModel.updateBackupLimits { monthly = it } }

        backupFolderPreference.summary =
            sharedPrefs().getString(
                fragment.getString(R.string.pref_backup_directory_key),
                fragment.getString(R.string.not_set),
            )

        backupFolderPreference.setOnPreferenceClickListener {
            if (BackupManager.isBackupMoveInProgress()) {
                fragment.showSnackbar(R.string.backup_move_in_progress)
                return@setOnPreferenceClickListener false
            }
            pickFolderLauncher.launch(null)
            true
        }

        // TODO Make NumberRangePreferenceCompat, and perhaps other preferences,
        //   aware of the idea that the value may not have loaded yet, and simplify this.
        listOf(
            minutesBetweenAutomaticBackupsPreference,
            dailyBackupsToKeepPreference,
            weeklyBackupsToKeepPreference,
            monthlyBackupsToKeepPreference,
        ).forEach { preference ->
            preference.summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    when (viewModel.flowOfState.value) {
                        is State.Fetching -> fragment.getString(R.string.pref__etc__summary__fetching)
                        is State.Error.NoCollection -> fragment.getString(R.string.pref__etc__summary__no_collection)
                        is State.Error.Exception -> fragment.getString(R.string.pref__etc__summary__error)
                        is State.Fetched -> preference.text
                    }
                }

            preference.onClickListener = listener@{
                when (val state = viewModel.flowOfState.value) {
                    is State.Fetching -> fragment.showSnackbar(R.string.pref__etc__snackbar__fetching)
                    is State.Error.NoCollection -> fragment.showSnackbar(R.string.pref__etc__snackbar__no_collection)
                    is State.Error.Exception ->
                        fragment.showSnackbar(
                            text = fragment.requireContext().getUserFriendlyErrorText(state.exception),
                        )
                    is State.Fetched -> return@listener ShouldShowDialog.Yes
                }
                return@listener ShouldShowDialog.No
            }
        }

        // We don't have any useful data in case the state isn't State.Fetched, but
        // we still need to trigger the summary providers. Setting dummy values is one simple way.
        fragment.lifecycleScope.launch {
            viewModel.flowOfState.collect { state ->
                val limits = if (state is State.Fetched) state.backupLimits else BackupLimits.getDefaultInstance()
                minutesBetweenAutomaticBackupsPreference.setValue(limits.minimumIntervalMins)
                dailyBackupsToKeepPreference.setValue(limits.daily)
                weeklyBackupsToKeepPreference.setValue(limits.weekly)
                monthlyBackupsToKeepPreference.setValue(limits.monthly)
            }
        }
    }

    private fun handleBackupFolder(uri: Uri?) {
        val context = fragment.requireContext()
        Timber.d("Selected backup URI: $uri")

        // if no URI, show snackbar or prompt to remove folder
        if (uri == null) {
            val currentPath = backupFolderPreference.summary
            if (currentPath == context.getString(R.string.not_set)) {
                fragment.showSnackbar(R.string.no_folder_selected)
            } else {
                // give option to remove or keep
                showRemoveBackupFolderDialog()
            }
            return
        }
        val contentResolver = context.contentResolver

        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )

        backupFolderPreference.sharedPreferences?.edit {
            putString(fragment.getString(R.string.pref_backup_directory_key), uri.toString())
        }
        backupFolderPreference.summary = uri.toString()

        // persistedUri = 1: Newly fetched URI (was "Not Set")
        // persistedUri = 2: Updated from one URI to another
        // may break if persistedUri is used elsewhere
        if (contentResolver.persistedUriPermissions.count() == 1) {
            backupManager.moveBackupFilesFromDefault(context, true)
        } else {
            val sortedUris = contentResolver.persistedUriPermissions.sortedBy { it.persistedTime }

            val sourceUri = sortedUris.first().uri
            val targetUri = sortedUris.last().uri

            backupManager.moveBackupFiles(context, sourceUri, targetUri)

            contentResolver.releasePersistableUriPermission(
                sourceUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        refresh()
    }

    fun refresh() {
        viewModel.launchFetchingOfBackupLimits()
    }

    fun observeLifecycle() {
        fragment.lifecycle.addObserver(this)
    }

    // move to default folder if uri is removed
    private fun showRemoveBackupFolderDialog() {
        AlertDialog.Builder(fragment.requireContext()).show {
            title(R.string.remove_backup_folder)
            positiveButton(R.string.dialog_remove) {
                backupManager.moveBackupFilesToDefault(fragment.requireContext())
                backupFolderPreference.sharedPreferences?.edit {
                    putString(fragment.getString(R.string.pref_backup_directory_key), fragment.getString(R.string.not_set))
                }
                backupFolderPreference.summary = fragment.getString(R.string.not_set)
                fragment.showSnackbar(R.string.backup_folder_removed)
            }
            negativeButton(R.string.dialog_keep)
        }
    }

    // T is reified in order to make the cast checked
    private inline fun <reified T> Preference.launchWhenChanged(crossinline block: suspend (T) -> Unit) {
        setOnPreferenceChangeListener { _, newValue ->
            this@BackupLimitsPresenter.fragment.launchCatchingTask { block(newValue as T) }
            true
        }
    }
}
