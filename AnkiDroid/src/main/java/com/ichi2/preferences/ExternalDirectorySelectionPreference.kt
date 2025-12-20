/*
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.R
import com.ichi2.anki.CollectionHelper
import com.ichi2.utils.Permissions
import java.io.File
import java.io.FileFilter

class ExternalDirectorySelectionPreference(
    context: Context,
    attrs: AttributeSet?,
) : EditTextPreference(
        context,
        attrs,
        styleAttr(context),
        android.R.attr.dialogPreferenceStyle,
    ),
    ListPreferenceTrait {
    var forceCustomSelector = false
    var requestingCustomPath = false

    private val initialLayoutResId = dialogLayoutResource

    // only used if a user has no file access
    override var listEntries: List<ListPreferenceTrait.Entry> = emptyList()

    override fun makeDialogFragment(): DialogFragment? {
        if (forceCustomSelector || isEditText(context)) {
            forceCustomSelector = false
            requestingCustomPath = false
            dialogLayoutResource = R.layout.preference_dialog_edittext
            return null
        }
        val currentPath = text ?: CollectionHelper.getDefaultAnkiDroidDirectory(context).absolutePath
        listValue = currentPath
        val entries = ArrayList<ListPreferenceTrait.Entry>()
        val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(context)
        entries.add(createEntry(defaultDir))
        CollectionHelper
            .getAppSpecificExternalDirectories(context)
            .filterNotNull()
            .flatMap { findAnkiDroidSubDirectories(it) }
            .distinct()
            .forEach { file ->
                if (file.absolutePath != defaultDir.absolutePath) {
                    entries.add(createEntry(file))
                }
            }
        if (currentPath.isNotEmpty() && entries.none { it.value == currentPath }) {
            entries.add(0, createEntry(File(currentPath)))
        }
        listEntries = entries
        dialogLayoutResource = initialLayoutResId
        return FullWidthListPreferenceDialogFragment()
    }

    /**
     * Helper to format file path for display.
     */
    private fun createEntry(file: File): ListPreferenceTrait.Entry {
        val label = file.absolutePath.replace("/Android/", "\n/Android/")
        return ListPreferenceTrait.Entry(label, file.absolutePath)
    }

    override var listValue: String = getPersistedString(CollectionHelper.getDefaultAnkiDroidDirectory(context).absolutePath)

    override fun callChangeListener(newValue: Any?): Boolean {
        val success = super.callChangeListener(newValue)
        if (success && newValue is String) {
            text = newValue
        }
        return success
    }

    // In future, we may want to disable this is there is only one selection
    override fun isEnabled() = true

    companion object {
        private val ANKI_DIR_FILTER = FileFilter { it.isDirectory && it.name.startsWith("AnkiDroid") }

        fun findAnkiDroidSubDirectories(f: File): List<File> {
            // either returns '/AnkiDroid1...AnkiDroid100' from storage migration
            // OR returns '/AnkiDroid' if no directories are available
            val found = f.listFiles(ANKI_DIR_FILTER)?.toList()
            return if (!found.isNullOrEmpty()) {
                found
            } else {
                listOf(File(f, "AnkiDroid"))
            }
        }

        /**
         * Determines the UI mode for the preference.
         *
         * @return true if the user has full storage access (MANAGE_EXTERNAL_STORAGE),
         * @return false if the user is restricted, forcing the Volume Picker UI.
         */
        private fun isEditText(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return true
            }
            return Permissions.canManageExternalStorage(context)
        }

        fun styleAttr(context: Context): Int =
            if (isEditText(context)) {
                R.attr.editTextPreferenceStyle
            } else {
                ListPreferenceTrait.STYLE_ATTR
            }
    }
}

/**
 * A custom DialogFragment that expands to fill the screen width.
 */
class FullWidthListPreferenceDialogFragment : ListPreferenceDialogFragment() {
    /**
     * Adds the custom path button to the dialog.
     */
    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNeutralButton("Custom path") { _, _ ->
            val preference = preference
            if (preference is ExternalDirectorySelectionPreference) {
                preference.requestingCustomPath = true
                preference.forceCustomSelector = true
            }
        }
    }

    /**
     * Function to dismiss the list dialog and open the text dialog.
     */
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val preference = preference
        if (preference is ExternalDirectorySelectionPreference && preference.requestingCustomPath) {
            android.os.Handler(Looper.getMainLooper()).post {
                preference.performClick()
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
