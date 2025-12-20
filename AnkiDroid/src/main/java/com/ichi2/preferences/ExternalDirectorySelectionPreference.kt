/*
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 * Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>
 *
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

package com.ichi2.preferences

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.R
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.showThemedToast
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import com.ichi2.anki.R as AnkiR

/**
 * Displays a list of external directories to select for the AnkiDroid Directory
 *
 * Improving discoverability of using a SD Card for the directory
 *
 * Also provides the ability to input a custom path
 *
 * @see ListPreferenceTrait - this preference can either be a List or an EditText
 */
class ExternalDirectorySelectionPreference(
    context: Context,
    attrs: AttributeSet?,
) : ListPreference(context, attrs),
    ListPreferenceTrait {
    init {
        dialogLayoutResource = R.layout.preference_dialog_edittext
        summaryProvider =
            SummaryProvider<ListPreference> { pref ->
                pref.value.takeUnless { it.isNullOrEmpty() } ?: context.getString(AnkiR.string.pref_directory_not_set)
            }
    }

    override var listEntries: List<ListPreferenceTrait.Entry> = emptyList()
    override var listValue: String = ""

    /** Safely retrieves the default AnkiDroid directory, returning null on failure. */
    private val defaultAnkiDir: File?
        get() =
            try {
                CollectionHelper.getDefaultAnkiDroidDirectory(context)
            } catch (e: Exception) {
                Timber.e(e, "Could not access default AnkiDroid directory")
                null
            }

    /** Builds the list of available directories for selection. */
    private fun loadDirectories(): List<ListPreferenceTrait.Entry> =
        buildList {
            val defaultDir = defaultAnkiDir
            defaultDir?.let { add(createEntry(it)) }
            try {
                CollectionHelper
                    .getAppSpecificExternalDirectories(context)
                    .filterNotNull()
                    .flatMap { findAnkiDroidSubDirectories(it) }
                    .distinct()
                    .filter { it.absolutePath != defaultDir?.absolutePath }
                    .forEach { add(createEntry(it)) }
            } catch (e: Exception) {
                Timber.e(e, "Error scanning for external directories")
            }
            val currentPath = value ?: defaultDir?.absolutePath ?: ""
            if (currentPath.isNotEmpty() && none { it.value == currentPath }) {
                add(0, createEntry(File(currentPath)))
            }
        }

    // TODO: Possibly move loadDirectories() to a background thread if ANR occurs
    override fun makeDialogFragment(): DialogFragment {
        listEntries = loadDirectories()
        entries = listEntries.map { it.key }.toTypedArray()
        setEntryValues(listEntries.map { it.value as CharSequence }.toTypedArray())
        listValue = value ?: defaultAnkiDir?.absolutePath ?: ""
        setValue(listValue)
        return FullWidthListPreferenceDialogFragment()
    }

    /** Creates a display entry. */
    private fun createEntry(file: File): ListPreferenceTrait.Entry {
        val path = file.absolutePath
        val androidIndex = path.indexOf("/Android/")
        if (androidIndex == -1) return ListPreferenceTrait.Entry(path, path)
        val displayString = "${path.take(androidIndex)}\n${path.substring(androidIndex)}"
        val spannable =
            SpannableString(displayString).apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, androidIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        return ListPreferenceTrait.Entry(spannable, path)
    }

    companion object {
        private val ANKI_DIR_FILTER = FileFilter { it.isDirectory && it.name.startsWith("AnkiDroid") }

        /** Finds subdirectories matching "AnkiDroid*" pattern within the given directory. */
        fun findAnkiDroidSubDirectories(f: File): List<File> = f.listFiles(ANKI_DIR_FILTER)?.toList() ?: emptyList()
    }
}

/** A DialogFragment that allows custom path input if on a device before Android 11, or on a full release version. */
class FullWidthListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        val isPlayStoreBuild = BuildConfig.FLAVOR == "play"
        val isScopedStorageEnforced = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        if (!isPlayStoreBuild || !isScopedStorageEnforced) {
            builder.setNeutralButton(AnkiR.string.pref_custom_path) { _, _ -> showCustomPathInput() }
        }
    }

    private fun showCustomPathInput() {
        val context = requireContext()
        val pref = (preference as? ExternalDirectorySelectionPreference) ?: return
        AlertDialog
            .Builder(context)
            .show {
                setTitle(AnkiR.string.pref_enter_custom_path)
                setView(AnkiR.layout.dialog_generic_text_input)
                positiveButton(android.R.string.ok)
                negativeButton(android.R.string.cancel)
            }.input(
                prefill = pref.value ?: "",
                allowEmpty = false,
            ) { dialog, text ->
                val newPath = text.toString().trim()
                val directory = File(newPath)
                when {
                    !directory.exists() && !directory.mkdirs() -> {
                        showThemedToast(
                            context,
                            context.getString(AnkiR.string.pref_cannot_create_directory),
                            true,
                        )
                    }
                    !directory.canWrite() -> {
                        showThemedToast(
                            context,
                            context.getString(AnkiR.string.pref_directory_not_writable),
                            true,
                        )
                    }
                    else -> {
                        dialog.dismiss()
                        if (pref.callChangeListener(newPath)) {
                            pref.value = newPath
                            pref.listValue = newPath
                        }
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}
