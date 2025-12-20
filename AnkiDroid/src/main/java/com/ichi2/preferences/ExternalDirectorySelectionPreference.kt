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
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.runCatchingWithLog
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.utils.ext.containsFile
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
        summaryProvider =
            SummaryProvider<ListPreference> { pref ->
                pref.value.takeUnless { it.isNullOrEmpty() } ?: context.getString(R.string.pref_directory_not_set)
            }
    }

    // below are default values for the listEntries and listValue variables, they are set in makeDialogFragment()
    override var listEntries: List<ListPreferenceTrait.Entry> = emptyList()
    override var listValue: String = ""

    /** Safely retrieves the default AnkiDroid directory, returning null on failure. */
    private val defaultAnkiDir: File?
        get() =
            try {
                CollectionHelper.getDefaultAnkiDroidDirectory(context)
            } catch (e: Exception) {
                Timber.w(e, "Could not access default AnkiDroid directory")
                null
            }

    /** Builds a list of valid candidate paths for the AnkiDroid directory.
     * These may point to an existing collection or a potential new storage location.
     */
    private fun loadDirectories(): List<ListPreferenceTrait.Entry> =
        buildList {
            if (value?.isNotEmpty() == true) {
                add(File(value))
            }
            defaultAnkiDir?.let { add(it) }
            addAll(getScannedDirectories())
        }.mapNotNull { runCatching { it.absolutePath }.getOrNull() }
            .distinct()
            .map(::absolutePathToDisplayEntry)

    private fun isValidAnkiDir(dir: File): Boolean {
        try {
            if (!dir.isDirectory || dir.name.startsWith(".")) return false
            if (IGNORED_DIRECTORIES.contains(dir.name.lowercase())) return false
            if (dir.name.startsWith("AnkiDroid", ignoreCase = true)) return true
            return dir.containsFile("collection.anki2")
        } catch (e: Exception) {
            Timber.w(e, "Could not access directory")
            return false
        }
    }
    // dir.list() is inefficient, the below extension method can be used instead

    /**
     * Safely scans all external directories.
     * If one directory fails to scan, we log it and continue to the next one
     */
    private fun getScannedDirectories(): List<File> {
        val storageRoots =
            runCatchingWithLog("get storage roots") {
                context.getExternalFilesDirs(null)
            }.getOrNull()
                .orEmpty()
                .filterNotNull()

        fun File.getValidAnkiSubfolders(): List<File>? {
            val subFolders =
                runCatchingWithLog("Could not list files") {
                    listFiles()
                }.getOrNull() ?: return null

            return subFolders.filter { isValidAnkiDir(it) }.ifEmpty { null }
        }

        return storageRoots
            .flatMap { root ->
                when (val ankiFolders = root.getValidAnkiSubfolders()) {
                    // If no anki directories are found, we can list this as it is likely an SD card
                    null -> listOf(File(root, "AnkiDroid"))
                    else -> ankiFolders
                }
            }.distinct()
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
    private fun absolutePathToDisplayEntry(path: String): ListPreferenceTrait.Entry {
        // Find the standard Android directory to split the path for display
        // Eg: "/storage/emulated/0"->Gray  "/Android/data/com.ichi2.anki"->Normal
        val androidIndex = path.indexOf("/Android/")
        // If index is not found, then return the path as is
        if (androidIndex == -1) return ListPreferenceTrait.Entry(path, path)
        val displayString = "${path.take(androidIndex)}\n${path.substring(androidIndex)}"
        val spannable =
            SpannableString(displayString).apply {
                setSpan(ForegroundColorSpan(Color.GRAY), 0, androidIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        return ListPreferenceTrait.Entry(spannable, path)
    }

    companion object {
        private val IGNORED_DIRECTORIES = setOf("collection.media", "backups", "cache", "code_cache")
    }
}

/** A DialogFragment that allows custom path input if on a device before Android 11, or on a full release version. */
class FullWidthListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNeutralButton(R.string.pref_custom_path) { _, _ -> showCustomPathInput() }
    }

    private fun showCustomPathInput() {
        val context = requireContext()
        val pref = (preference as? ExternalDirectorySelectionPreference) ?: return
        AlertDialog
            .Builder(context)
            .show {
                setTitle(R.string.pref_enter_custom_path)
                setView(R.layout.dialog_generic_text_input)
                positiveButton(android.R.string.ok)
                negativeButton(android.R.string.cancel)
            }.input(
                prefill = pref.value ?: "",
                allowEmpty = false,
            ) { dialog, text ->
                try {
                    val newPath = text.toString().trim()
                    val pathObj = Paths.get(newPath).normalize()
                    Files.createDirectories(pathObj)
                    if (!Files.isWritable(pathObj)) {
                        showThemedToast(
                            context,
                            context.getString(R.string.pref_directory_not_writable),
                            true,
                        )
                        return@input
                    }
                    dialog.dismiss()
                    if (pref.callChangeListener(newPath)) {
                        pref.value = newPath
                        pref.listValue = newPath
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to set custom path")
                    AlertDialog
                        .Builder(context)
                        .setTitle(context.getString(R.string.could_not_create_dir, text.toString()))
                        .setMessage(android.util.Log.getStackTraceString(e))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
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
