/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.ui.windows.managespace

import android.app.ActivityManager
import android.app.Application
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.preference.Preference
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.dialogs.tools.AsyncDialogBuilder.CheckedItems
import com.ichi2.anki.ui.dialogs.tools.DialogResult
import com.ichi2.anki.ui.dialogs.tools.awaitDialog
import com.ichi2.anki.ui.preferences.screens.BackupLimitsPresenter
import com.ichi2.async.deleteMedia
import com.ichi2.libanki.Media
import com.ichi2.preferences.TextWidgetPreference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

sealed interface Size {
    object Calculating : Size
    class Bytes(val totalSize: Long) : Size
    class FilesAndBytes(val files: Collection<File>, val totalSize: Long) : Size
    class Error(
        val exception: Exception,
        @StringRes val widgetTextId: Int = R.string.pref__widget_text__error
    ) : Size
}

/**************************************************************************************************
 ********************************************* Model **********************************************
 **************************************************************************************************/

class ManageSpaceViewModel(val app: Application) : AndroidViewModel(app), CollectionDirectoryProvider {
    override val collectionDirectory = CollectionManager.getCollectionDirectory()

    val flowOfDeleteUnusedMediaSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteBackupsSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteCollectionSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteEverythingSize = MutableStateFlow<Size>(Size.Calculating)

    init {
        launchCalculationOfBackupsSize()
        launchCalculationOfSizeOfEverything()
        launchCalculationOfCollectionSize()
        launchSearchForUnusedMedia()
    }

    /************************************* Unused media files *************************************/

    private fun launchSearchForUnusedMedia() = viewModelScope.launch {
        flowOfDeleteUnusedMediaSize.ifCollectionDirectoryExistsEmit {
            withCol {
                val unusedFiles = with(media) { findUnusedMediaFiles() }
                val unusedFilesSize = unusedFiles.sumOf(::calculateSize)
                Size.FilesAndBytes(unusedFiles, unusedFilesSize)
            }
        }
    }

    suspend fun performMediaCheck() {
        try {
            withCol { media.performFullCheck() }
        } finally {
            launchSearchForUnusedMedia()
        }
    }

    suspend fun deleteMedia(filesNamesToDelete: List<String>) {
        try {
            withCol { deleteMedia(this, filesNamesToDelete) }
        } finally {
            launchCalculationOfSizeOfEverything()
            launchCalculationOfCollectionSize()
            launchSearchForUnusedMedia()
        }
    }

    /***************************************** Backups ********************************************/

    private fun launchCalculationOfBackupsSize() = viewModelScope.launch {
        flowOfDeleteBackupsSize.ifCollectionDirectoryExistsEmit {
            withCol {
                val backupFiles = BackupManager.getBackups(File(this.path)).toList()
                val backupFilesSize = backupFiles.sumOf(::calculateSize)
                Size.FilesAndBytes(backupFiles, backupFilesSize)
            }
        }
    }

    suspend fun deleteBackups(backupsToDelete: List<File>) {
        try {
            withCol { BackupManager.deleteBackups(this, backupsToDelete) }
        } finally {
            launchCalculationOfBackupsSize()
            launchCalculationOfCollectionSize()
            launchCalculationOfSizeOfEverything()
        }
    }

    /*************************************** Collection *******************************************/

    private fun launchCalculationOfCollectionSize() = viewModelScope.launch {
        flowOfDeleteCollectionSize.ifCollectionDirectoryExistsEmit {
            withContext(Dispatchers.IO) {
                Size.Bytes(calculateSize(collectionDirectory))
            }
        }
    }

    suspend fun deleteCollection() {
        try {
            CollectionManager.deleteCollectionDirectory() // Executed in withQueue
        } finally {
            launchCalculationOfBackupsSize()
            launchCalculationOfSizeOfEverything()
            launchCalculationOfCollectionSize()
            launchSearchForUnusedMedia()
        }
    }

    /*************************************** Everything *******************************************/

    private fun launchCalculationOfSizeOfEverything() = viewModelScope.launch {
        flowOfDeleteEverythingSize.emit(Size.Calculating)
        flowOfDeleteEverythingSize.emit(
            withContext(Dispatchers.IO) {
                Size.Bytes(app.getUserDataAndCacheSize())
            }
        )
    }

    // This kills the process afterwards, so no need to recalculate sizes
    fun deleteEverything() {
        getSystemService(app, ActivityManager::class.java)?.clearApplicationUserData()
    }

    /**********************************************************************************************/

    private suspend fun MutableStateFlow<Size>.ifCollectionDirectoryExistsEmit(block: suspend () -> Size) {
        try {
            ensureCanWriteToOrCreateCollectionDirectory()
            if (!collectionDirectoryExists()) {
                emit(Size.FilesAndBytes(emptyList(), 0L))
            } else {
                emit(Size.Calculating)
                emit(block())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Size.Error(e))
        }
    }
}

/**************************************************************************************************
 ******************************************** Fragment ********************************************
 **************************************************************************************************/

class ManageSpaceFragment : SettingsFragment() {
    override val preferenceResource = R.xml.manage_space
    override val analyticsScreenNameConstant = "manageSpace"

    private val backupLimitsPresenter = BackupLimitsPresenter(this).also { it.observeLifecycle() }

    private val viewModel: ManageSpaceViewModel by viewModels()

    override fun initSubscreen() {
        val deleteUnusedMediaPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_media_key)
        val deleteBackupsPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_backups_key)
        val deleteCollectionPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_collection_key)
        val deleteEverythingPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_everything_key)

        deleteUnusedMediaPreference.launchOnPreferenceClick { onDeleteUnusedMediaClick() }
        deleteBackupsPreference.launchOnPreferenceClick { onDeleteBackupsClick() }
        deleteCollectionPreference.launchOnPreferenceClick { onDeleteCollectionClick() }
        deleteEverythingPreference.launchOnPreferenceClick { onDeleteEverythingClick() }

        adjustDeleteEverythingStringsDependingOnCollectionLocation(deleteEverythingPreference)

        listOf(
            viewModel.flowOfDeleteUnusedMediaSize to deleteUnusedMediaPreference,
            viewModel.flowOfDeleteCollectionSize to deleteCollectionPreference,
            viewModel.flowOfDeleteEverythingSize to deleteEverythingPreference,
            viewModel.flowOfDeleteBackupsSize to deleteBackupsPreference,
        ).forEach { (flowOfSize, preference) ->
            lifecycleScope.launch { flowOfSize.collect { size -> preference.setWidgetTextBy(size) } }
        }
    }

    /************************************ Delete unused media *************************************/

    private suspend fun onDeleteUnusedMediaClick() {
        val size = viewModel.flowOfDeleteUnusedMediaSize.value
        if (size is Size.Error && size.exception is Media.MediaCheckRequiredException) {
            val mediaCheckPromptResult = requireContext().awaitDialog {
                setMessage(R.string.dialog__media_check_required__message)
                setPositiveButton(R.string.check_media)
                setNegativeButton(R.string.dialog_cancel)
            }

            if (mediaCheckPromptResult is DialogResult.Ok) {
                withProgress(R.string.check_media_message) {
                    viewModel.performMediaCheck()
                }
            }
        } else if (size is Size.FilesAndBytes) {
            val unusedFiles = size.files
            val unusedFileNames = unusedFiles.map { it.name }

            val deleteFilesPromptResult = requireContext().awaitDialog {
                setTitle(R.string.dialog__delete_unused_media_files__title)
                setMultiChoiceItems(unusedFileNames, CheckedItems.All)
                setPositiveButton(R.string.dialog_positive_delete)
                setNegativeButton(R.string.dialog_cancel)
            }

            if (deleteFilesPromptResult is DialogResult.Ok.MultipleChoice) {
                val checkedItems = deleteFilesPromptResult.checkedItems
                val filesNamesToDelete = unusedFileNames.filterIndexed { index, _ -> checkedItems[index] }

                withProgress(R.string.delete_media_message) {
                    viewModel.deleteMedia(filesNamesToDelete)
                }
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    /*************************************** Delete backups ***************************************/

    private suspend fun onDeleteBackupsClick() {
        val size = viewModel.flowOfDeleteBackupsSize.value
        if (size is Size.FilesAndBytes) {
            val formatter = LocalizedUnambiguousBackupTimeFormatter()
            val backupFiles = size.files
            val backupNames = backupFiles.map { formatter.getTimeOfBackupAsText(it) }

            val chooseBackupsPromptResult = requireContext().awaitDialog {
                setTitle(R.string.dialog__delete_backups__title)
                setMultiChoiceItems(backupNames, CheckedItems.None)
                setPositiveButton(R.string.dialog_positive_delete)
                setNegativeButton(R.string.dialog_cancel)
            }

            if (chooseBackupsPromptResult is DialogResult.Ok.MultipleChoice) {
                val checkedItems = chooseBackupsPromptResult.checkedItems
                val backupsToDelete = backupFiles.filterIndexed { index, _ -> checkedItems[index] }

                withProgress(R.string.progress__deleting_backups) {
                    viewModel.deleteBackups(backupsToDelete)
                }
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    /************************************* Delete collection **************************************/

    // TODO: Finish other AnkiDroid activities when the collection is deleted.
    //   Note that this might be not quite trivial, as the activities might be visible to user.
    //   One way would be to have the activities register broadcast receivers that perform finish;
    //   Another would be maintaining weak references to them. Would be nice to find a better way.
    private suspend fun onDeleteCollectionClick() {
        val size = viewModel.flowOfDeleteCollectionSize.value
        if (size is Size.Bytes) {
            val deleteCollectionPromptResult = requireContext().awaitDialog {
                setTitle(R.string.dialog__delete_collection__title)
                setMessage(R.string.dialog__delete_collection__message)
                setPositiveButton(R.string.dialog_positive_delete)
                setNegativeButton(R.string.dialog_cancel)
            }

            if (deleteCollectionPromptResult is DialogResult.Ok) {
                try {
                    withProgress(R.string.progress__deleting_collection) {
                        viewModel.deleteCollection()
                    }
                } finally {
                    backupLimitsPresenter.refresh()
                }
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    /************************************* Delete everything **************************************/

    @StringRes private var deleteEverythingDialogTitle: Int = 0
    @StringRes private var deleteEverythingDialogMessage: Int = 0

    private fun adjustDeleteEverythingStringsDependingOnCollectionLocation(preference: Preference) {
        if (viewModel.collectionDirectory.isInsideDirectoriesRemovedWithTheApp(requireContext())) {
            preference.setTitle(R.string.pref__delete_everything__title)
            preference.setSummary(R.string.pref__delete_everything__summary)
            deleteEverythingDialogTitle = R.string.dialog__delete_everything__title
            deleteEverythingDialogMessage = R.string.dialog__delete_everything__message
        } else {
            preference.setTitle(R.string.pref__delete_app_data__title)
            preference.setSummary(R.string.pref__delete_app_data__summary)
            deleteEverythingDialogTitle = R.string.dialog__delete_app_data__title
            deleteEverythingDialogMessage = R.string.dialog__delete_app_data__message
        }
    }

    private suspend fun onDeleteEverythingClick() {
        val deleteEverythingPromptResult = requireContext().awaitDialog {
            setTitle(deleteEverythingDialogTitle)
            setMessage(deleteEverythingDialogMessage)
            setPositiveButton(R.string.dialog_positive_delete)
            setNegativeButton(R.string.dialog_cancel)
        }

        if (deleteEverythingPromptResult is DialogResult.Ok) {
            viewModel.deleteEverything()
        }
    }

    /**********************************************************************************************
     ************************************* Misplaced methods **************************************
     **********************************************************************************************/

    // TODO Android N and earlier, formatFileSize & formatShortFileSize use powers of 1024.
    //   Perhaps correct input so that powers of 1000 are used on every API level?
    private fun TextWidgetPreference.setWidgetTextBy(size: Size) {
        fun Long.toHumanReadableSize() = Formatter.formatShortFileSize(requireContext(), this)

        widgetText = when (size) {
            is Size.Calculating -> getString(R.string.pref__widget_text__calculating)
            is Size.Error -> getString(size.widgetTextId)
            is Size.Bytes -> size.totalSize.toHumanReadableSize()
            is Size.FilesAndBytes -> resources.getQuantityString(
                R.plurals.pref__widget_text__n_files_n_bytes,
                size.files.size,
                size.files.size,
                size.totalSize.toHumanReadableSize()
            )
        }

        isEnabled = !(
            size is Size.Bytes && size.totalSize == 0L ||
                size is Size.FilesAndBytes && size.files.isEmpty()
            )
    }

    private fun Preference.launchOnPreferenceClick(block: suspend CoroutineScope.() -> Unit) {
        setOnPreferenceClickListener {
            launchCatchingTask { block() }
            true
        }
    }

    private fun showSnackbarIfCalculatingOrError(size: Size) {
        when (size) {
            is Size.Calculating -> showSnackbar(R.string.pref__etc__snackbar__calculating)
            is Size.Error -> showSnackbar(requireContext().getUserFriendlyErrorText(size.exception))
            else -> {}
        }
    }
}
