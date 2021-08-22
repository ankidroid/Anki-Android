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

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.async.ProgressSender
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.importer.TextImporter
import com.ichi2.utils.ExceptionUtil
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Converts from AnkiDroid to the libAnki representation of text import options, starts the process
 * displays progress and results/failure
 */
@RequiresApi(Build.VERSION_CODES.O)
class ImporterImportProgressFragment : Fragment(R.layout.import_csv_import_progress) {

    // UI elements
    private lateinit var closeButton: Button
    private lateinit var importLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadPercentage: TextView
    private lateinit var importLogWrapper: View
    /** Informs users import is done */
    private lateinit var importProgressDone: TextView

    private lateinit var col: Collection
    private lateinit var options: ImportOptions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        col = CollectionHelper.getInstance().getCol(requireContext())

        options = requireArguments().getParcelable("options")!!

        Timber.d("options: %s", options)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        downloadPercentage = view.findViewById(R.id.import_progress_text)
        progressBar = view.findViewById(R.id.import_progress_bar)
        importLog = view.findViewById(R.id.import_log)
        importLogWrapper = view.findViewById(R.id.import_log_wrapper)
        closeButton = view.findViewById(R.id.close_csv_import)
        importProgressDone = view.findViewById(R.id.import_progress_done)

        Timber.i("Starting import process")

        val exceptionHandler = CoroutineExceptionHandler { _, ex ->
            Timber.w(ex, "Importing failed")
            AnkiDroidApp.sendExceptionReport(ex, "import failed")
            onException(ex)
        }
        GlobalScope.launch(Dispatchers.Main + exceptionHandler) {
            performImport()
        }
    }

    private fun onException(exception: Throwable) {
        hideProgressControls()
        showFinishedControls(getString(R.string.import_progress_failed), ExceptionUtil.getFullStackTrace(exception))
    }

    private suspend fun performImport() {
        val importer: TextImporter

        withContext(Dispatchers.IO) {
            importer = TextImporter(col, options.path).apply {
                val model = col.models[options.noteTypeId]!!
                // model.did needs to be set for genCard()
                // selected() needs to be set to obtain the correct settings
                model.put("did", options.deck)
                col.decks.select(options.deck)
                setModel(model)
                // needs to be called to setup the class
                fields()

                setImportMode(options.importMode.constant)
                setAllowHtml(options.allowHtml.asBoolean())
                delimiter = options.delimiterChar

                // mapping needs to be called after delimiter
                setMapping(options.mapping)

                val importModeTag = if (options.importMode == ImportConflictMode.UPDATE) options.importModeTag else null
                setTagModified(importModeTag)
                setProgressCallback {
                    GlobalScope.launch(Dispatchers.Main) {
                        downloadPercentage.text = it
                    }
                }
            }
            Timber.i("Initialized importer. Executing")
            importer.run()
        }

        Timber.i("Importing completed")

        hideProgressControls()
        showFinishedControls(
            titleText = getString(R.string.import_progress_complete_summary),
            logMessage = importer.log.joinToString("\n")
        )
    }

    /** Hide the controls which display the progress of the operation */
    private fun hideProgressControls() {
        progressBar.visibility = View.GONE
        downloadPercentage.visibility = View.GONE
    }

    /** Display a "close" button and a selectable log/exception stack trace */
    private fun showFinishedControls(titleText: String, logMessage: String) {
        importLogWrapper.visibility = View.VISIBLE
        importProgressDone.visibility = View.VISIBLE
        importProgressDone.text = titleText

        importLog.text = logMessage

        // keep the text on the screen for as long as the user needs
        closeButton.apply {
            setOnClickListener { closeActivity() }
            visibility = View.VISIBLE
        }
    }

    private fun closeActivity() {
        Timber.i("Closing screen")
        requireActivity().finish()
    }

    /** Wraps the TextImporter's [TaskManager.ProgressCallback] in a sensible interface */
    private fun TextImporter.setProgressCallback(onProgress: (String) -> Unit) {
        val callback = object : TaskManager.ProgressCallback<String>(
            ProgressSender {
                onProgress(it!!)
            },
            resources
        ) {
        }
        this.setProgressCallback(callback)
    }
}
