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

package com.ichi2.anki.importer

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.*
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.importer.TextImporter
import timber.log.Timber
import java.io.File

/**
 * Allows a user to select a CSV/TSV for import
 * output: a file path to the file in the AnkiDroid cache
 * * "filePath" in the return bundle
 */
@RequiresApi(api = Build.VERSION_CODES.O) // TextImporter -> FileObj
class ImporterFileSelectionFragment : Fragment(R.layout.import_csv_file_selection) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.btn_select_file).setOnClickListener { openFilePicker() }
        view.findViewById<ImageButton>(R.id.help_button).setOnClickListener { openHelp() }
    }

    private fun openHelp() {
        val url = getString(R.string.link_importing_help)
        (requireActivity() as AnkiActivity).openUrl(Uri.parse(url))
    }

    private fun openFilePicker() {
        Timber.i("opening file picker")
        openFilePicker.launch(arrayOf("*/*"))
    }

    private val openFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result == null) {
            Timber.i("User cancelled file picker")
            return@registerForActivityResult
        }

        Timber.i("Selected file: %s", result)

        // Copy the file to cache. This lets us use a file path, which libAnki expects
        val localFilePath = copyToCache(result)
            .onFailure {
                // We might want to differentiate between "could not read" and "could not copy to cache"
                Timber.w(it, "Failed to copy file to cache")
                UIUtils.showThemedToast(requireContext(), R.string.something_wrong, true)
                return@registerForActivityResult
            }
            .getOrThrow()

        // display a message if the file is invalid
        // if the importer doesn't work, we can't continue
        if (!testFileIsValidForImport(localFilePath)) {
            return@registerForActivityResult
        }

        closeFragmentWithResult(localFilePath)
    }

    /**
     * Ensures that the file can be imported.
     * We use the importer to read the number of fields, which is used when setting import options
     */
    private fun testFileIsValidForImport(localFilePath: CacheFilePath): Boolean {
        try {
            // Since we have no other exporters, assume that we're importing via the default: TextImporter
            val textImporter = TextImporter(CollectionHelper.getInstance().getCol(context), localFilePath)
            val fieldCount = textImporter.fields()
            Timber.d("detected %d fields in CSV", fieldCount)
        } catch (e: TextImporter.UnknownDelimiterException) {
            Timber.i("File was invalid for import: UnknownDelimiterException") // do not log the stack trace
            UIUtils.showThemedToast(context, getString(R.string.import_error_exception, getString(R.string.import_error_delimiter)), false)
            return false
        } catch (e: TextImporter.EncodingException) {
            Timber.i("File was invalid for import: EncodingException") // do not log the stack trace
            UIUtils.showThemedToast(context, getString(R.string.import_error_exception, getString(R.string.import_error_not_utf8)), false)
            return false
        } catch (e: Exception) {
            Timber.w(e, "File was invalid for import: unknown exception")
            AnkiDroidApp.sendExceptionReport(e, "CSV import: unknown exception")
            UIUtils.showThemedToast(context, getString(R.string.import_error_exception, e.localizedMessage), false)
            return false
        }
        return true
    }

    private fun copyToCache(result: Uri): Result<CacheFilePath> {
        return kotlin.runCatching {
            val inputStream = requireContext().contentResolver.openInputStream(result)
            val outputPath = File.createTempFile("import", ".tmp").absolutePath
            CompatHelper.getCompat().copyFile(inputStream, outputPath)
            return@runCatching outputPath
        }
    }

    private fun closeFragmentWithResult(localFilePath: CacheFilePath) {
        Timber.i("success: closing import fragment with path")
        // see ImporterHostFragment - this detects the result and replaces the fragment
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_BUNDLE_FILE_PATH to localFilePath))
    }

    companion object {
        /** The key identifying the fragment result */
        const val RESULT_KEY = "FileSelection"
        /** The key of the file path in the resulting bundle */
        const val RESULT_BUNDLE_FILE_PATH = "filePath"
    }
}
