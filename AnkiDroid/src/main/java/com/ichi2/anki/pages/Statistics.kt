/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.StatisticsActivity
import com.ichi2.anki.StatisticsViewModel
import com.ichi2.anki.utils.getTimestamp
import com.ichi2.libanki.utils.TimeManager
import timber.log.Timber

class Statistics : PageFragment() {
    override val title: String?
        get() = null

    override val pageName = "graphs"

    private val statisticsViewModel: StatisticsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.statistics_fragment_layout,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)

        toolbar.apply {
            inflateMenu(R.menu.statistics)
            menu.findItem(R.id.action_export_stats).title = CollectionManager.TR.statisticsSavePdf()

            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_export_stats) {
                    exportWebViewContentAsPDF()
                }
                true
            }
            val toolbarSpinner = findViewById<Spinner>(R.id.stats_toolbar_spinner)
            toolbarSpinner.isVisible = true
            val supportActionBar = (activity as? StatisticsActivity)?.supportActionBar

            if (supportActionBar != null) {
                (activity as? StatisticsActivity)?.setupDeckSelector(toolbarSpinner, supportActionBar)
            } else {
                Timber.w("SupportActionBar is null")
            }
        }
        statisticsViewModel.deckName.observe(
            viewLifecycleOwner,
            Observer { deck ->
                changeDeck(deck)
            }
        )
    }

    /**
     * This method is a workaround to change the deck in the webview by finding the text box and
     * replacing the deck name with the selected deck name from the dialog and updating the stats
     **/
    private fun changeDeck(selectedDeck: String) {
        val javascriptCode = """
        var textBox = [].slice.call(document.getElementsByTagName('input'), 0).filter(x => x.type == "text")[0];
        textBox.value = "deck:$selectedDeck";
        textBox.dispatchEvent(new Event("input", { bubbles: true }));
        textBox.dispatchEvent(new Event("change"));
        """.trimIndent()
        webView.evaluateJavascript(javascriptCode, null)
    }

    /**Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        val printManager = getSystemService(requireContext(), PrintManager::class.java)
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager?.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return StatisticsActivity.getIntent(context, Statistics::class)
        }
    }
}
