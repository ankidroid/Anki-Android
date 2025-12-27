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

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import androidx.core.content.ContextCompat.getSystemService
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.databinding.StatisticsBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.startDeckSelection
import com.ichi2.anki.withProgress
import dev.androidbroadcast.vbpd.viewBinding

class Statistics :
    PageFragment(R.layout.statistics),
    DeckSelectionDialog.DeckSelectionListener {
    override val pagePath: String = "graphs"
    private val binding by viewBinding(StatisticsBinding::bind)

    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.deckName.setOnClickListener { startDeckSelection(all = false, filtered = false) }
        binding.appBar
            .addLiftOnScrollListener { _, backgroundColor ->
                activity?.window?.statusBarColor = backgroundColor
            }

        binding.toolbar.apply {
            menu.findItem(R.id.action_export_stats).title = CollectionManager.TR.statisticsSavePdf()
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_export_stats) {
                    exportWebViewContentAsPDF()
                }
                true
            }
        }
        requireActivity().launchCatchingTask {
            withProgress {
                val deckName =
                    savedInstanceState?.getString(KEY_DECK_NAME, null) ?: withCol { decks.current().name }
                changeDeck(deckName)
            }
        }
    }

    /** Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    private fun exportWebViewContentAsPDF() {
        val printManager = getSystemService(requireContext(), PrintManager::class.java)
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = webViewLayout.createPrintDocumentAdapter(jobName)
        printManager?.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build(),
        )
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        changeDeck(deck.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DECK_NAME, binding.deckName.text.toString())
    }

    private fun changeDeck(selectedDeckName: String) {
        binding.deckName.text = selectedDeckName
        val safeDeckName = selectedDeckName.replace("\"", "\\\"")

        val javascriptCode =
            """
            (function() {
                var textBox = document.getElementById("statisticsSearchText");
                var targetDeck = "deck:\"$safeDeckName\"";

                // set search box to the selected deck right away
                textBox.value = targetDeck;
                textBox.dispatchEvent(new Event("input", { bubbles: true }));
                textBox.dispatchEvent(new Event("change", { bubbles: true }));

                // saveing the original value property so we can call it later
                var originalDescriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');

                // intercept any attempt to change the value of this box
                Object.defineProperty(textBox, 'value', {
                    get: function() {
                        return originalDescriptor.get.call(this);
                    },
                    set: function(val) {
                        // if Anki tries to reset to "deck:current", block it and keep our deck
                        if (val === "deck:current") {
                            originalDescriptor.set.call(this, targetDeck);

                            // Fire the right events so the graphs update as if the user typed it
                            var self = this;
                            setTimeout(function() {
                                self.dispatchEvent(new Event("input", { bubbles: true }));
                                self.dispatchEvent(new Event("change", { bubbles: true }));
                            }, 0);
                        } else {
                            // Let any other changes go through 
                            originalDescriptor.set.call(this, val);
                        }
                    }
                });
            })();
            """.trimIndent()
        webViewLayout.evaluateJavascript(javascriptCode, null)
    }

    companion object {
        private const val KEY_DECK_NAME = "key_deck_name"
    }
}
