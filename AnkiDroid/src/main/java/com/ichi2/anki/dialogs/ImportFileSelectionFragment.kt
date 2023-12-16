/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.title
import kotlinx.parcelize.Parcelize
import timber.log.Timber

@NeedsTest("Selecting APKG does not allow multiple files")
@NeedsTest("Selecting COLPKG does not allow multiple files")
@NeedsTest("Restore backup dialog does not allow multiple files")
class ImportFileSelectionFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val entries = buildImportEntries()
        return AlertDialog.Builder(requireActivity())
            .title(R.string.menu_import)
            .setItems(
                entries.map { requireActivity().getString(it.titleRes) }.toTypedArray()
            ) { _, position ->
                val entry = entries[position]
                UsageAnalytics.sendAnalyticsEvent(
                    UsageAnalytics.Category.LINK_CLICKED,
                    entry.analyticsId
                )
                openImportFilePicker(
                    activity = requireActivity() as AnkiActivity,
                    fileType = entry.type,
                    multiple = entry.multiple,
                    mimeType = entry.mimeType,
                    extraMimes = entry.extraMimes
                )
            }
            .create()
    }

    private fun buildImportEntries(): List<ImportEntry> {
        return arguments?.let { args ->
            args.classLoader = this@ImportFileSelectionFragment::class.java.classLoader
            val options =
                BundleCompat.getParcelable(args, ARG_IMPORT_OPTIONS, ImportOptions::class.java)
                    ?: return emptyList()
            mutableListOf<ImportEntry>().apply {
                if (options.importApkg) {
                    add(
                        ImportEntry(
                            R.string.import_deck_package,
                            UsageAnalytics.Actions.IMPORT_APKG_FILE,
                            ImportFileType.APKG
                        )
                    )
                }
                if (options.importColpkg) {
                    add(
                        ImportEntry(
                            R.string.import_collection_package,
                            UsageAnalytics.Actions.IMPORT_COLPKG_FILE,
                            ImportFileType.COLPKG
                        )
                    )
                }
                if (options.importTextFile) {
                    add(
                        ImportEntry(
                            R.string.import_csv,
                            UsageAnalytics.Actions.IMPORT_CSV_FILE,
                            ImportFileType.CSV,
                            multiple = false,
                            mimeType = "*/*",
                            extraMimes = arrayOf(
                                "text/plain",
                                "text/comma-separated-values",
                                "text/csv",
                                "text/tab-separated-values"
                            )
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    // safe as this data class is used as a container and it's not involved in any comparing
    @Suppress("ArrayInDataClass")
    private data class ImportEntry(
        @StringRes val titleRes: Int,
        val analyticsId: String,
        val type: ImportFileType,
        var multiple: Boolean = false,
        val mimeType: String = "*/*",
        val extraMimes: Array<String>? = null,
    )

    @Parcelize
    data class ImportOptions(
        val importColpkg: Boolean,
        val importApkg: Boolean,
        val importTextFile: Boolean,
    ) : Parcelable

    enum class ImportFileType {
        APKG, COLPKG, CSV
    }

    interface ApkgImportResultLauncherProvider {
        fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent?>
    }

    interface CsvImportResultLauncherProvider {
        fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent?>
    }

    companion object {
        private const val ARG_IMPORT_OPTIONS = "arg_import_options"

        fun newInstance(options: ImportOptions) = ImportFileSelectionFragment().apply {
            arguments = bundleOf(ARG_IMPORT_OPTIONS to options)
        }

        /**
         * Calls through the system with an [Intent] to pick a file to be imported.
         */
        fun openImportFilePicker(
            activity: AnkiActivity,
            fileType: ImportFileType,
            multiple: Boolean = false,
            mimeType: String = "*/*",
            extraMimes: Array<String>? = null,
        ) {
            Timber.d("openImportFilePicker() delegating to file picker intent")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = mimeType
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            intent.putExtra("android.content.extra.SHOW_FILESIZE", true)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            extraMimes?.let { intent.putExtra(Intent.EXTRA_MIME_TYPES, it) }

            if ((fileType == ImportFileType.APKG || fileType == ImportFileType.COLPKG) && activity is ApkgImportResultLauncherProvider) {
                activity.launchActivityForResultWithAnimation(
                    intent,
                    activity.getApkgFileImportResultLauncher(),
                    ActivityTransitionAnimation.Direction.NONE
                )
            } else if (fileType == ImportFileType.CSV && activity is CsvImportResultLauncherProvider) {
                activity.launchActivityForResultWithAnimation(
                    intent,
                    activity.getCsvFileImportResultLauncher(),
                    ActivityTransitionAnimation.Direction.NONE
                )
            } else {
                Timber.w("Activity($activity) can't handle requested import: $fileType")
            }
        }
    }
}
