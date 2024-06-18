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
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.updateDeckConfigsRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@NeedsTest("pressing back: icon + button should go to the previous screen")
@NeedsTest("15130: pressing back: icon + button should return to options if the manual is open")
class DeckOptions : PageFragment() {

    // handle going back from the manual
    private val onBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Timber.v("webView: navigating back")
            webView.goBack()
        }
    }

    // HACK: this is enabled unconditionally as we currently cannot get the 'changed' status
    private val onBackSaveCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Timber.v("DeckOptions: showing 'discard changes'")
            DiscardChangesDialog.showDialog(requireContext()) {
                Timber.i("OK button pressed to confirm discard changes")
                this.isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackSaveCallback)
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackCallback)
        return PageWebViewClient().apply {
            onPageFinishedCallback = OnPageFinishedCallback { view ->
                Timber.v("canGoBack: %b", view.canGoBack())
                onBackCallback.isEnabled = view.canGoBack()
            }
        }
    }

    companion object {
        fun getIntent(context: Context, deckId: Long): Intent {
            val title = context.getString(R.string.menu__deck_options)
            return getIntent(context, "deck-options/$deckId", title, DeckOptions::class)
        }
    }
}

suspend fun FragmentActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output = withContext(Dispatchers.Main) {
        withProgress(
            extractProgress = {
                text = if (progress.hasComputeWeights()) {
                    val tr = CollectionManager.TR
                    val value = progress.computeWeights
                    val label = tr.deckConfigOptimizingPreset(
                        currentCount = value.currentPreset,
                        totalCount = value.totalPresets
                    )
                    val pct = if (value.total > 0) (value.current / value.total * 100) else 0
                    val reviewsLabel = tr.deckConfigPercentOfReviews(pct = pct.toString(), reviews = value.reviews)
                    label + "\n" + reviewsLabel
                } else {
                    getString(R.string.dialog_processing)
                }
            }
        ) {
            withContext(Dispatchers.IO) {
                CollectionManager.withCol { updateDeckConfigsRaw(input) }
            }
        }
    }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) { finish() }
    return output
}
