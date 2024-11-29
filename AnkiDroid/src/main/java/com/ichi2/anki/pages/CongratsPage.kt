/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.view.View
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.FilteredDeckOptions
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsActivity
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.anki.utils.TIME_HOUR
import com.ichi2.anki.utils.TIME_MINUTE
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import kotlin.math.round

class CongratsPage :
    PageFragment(),
    CustomStudyDialog.CustomStudyListener,
    ChangeManager.Subscriber {

    private val viewModel by viewModels<CongratsViewModel>()

    init {
        ChangeManager.subscribe(this)
    }

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        // typically due to 'day rollover'
        if (changes.studyQueues) {
            Timber.i("refreshing: study queues updated")
            webView.reload()
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        return super.onCreateWebViewClient(savedInstanceState).also { client ->
            client.onPageFinishedCallback = OnPageFinishedCallback { webView ->
                webView.evaluateJavascript(
                    "bridgeCommand = function(request){ ankidroid.bridgeCommand(request); };"
                ) {}
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }
            .launchIn(lifecycleScope)

        viewModel.openStudyOptions
            .onEach { openStudyOptionsAndFinish() }
            .launchIn(lifecycleScope)

        viewModel.deckOptionsDestination
            .flowWithLifecycle(lifecycle)
            .onEach { destination ->
                val intent = destination.getIntent(requireContext())
                startActivity(intent, null)
            }
            .launchIn(lifecycleScope)

        webView.addJavascriptInterface(BridgeCommand(), "ankidroid")

        with(view.findViewById<MaterialToolbar>(R.id.toolbar)) {
            inflateMenu(R.menu.congrats)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_open_deck_options) {
                    viewModel.onDeckOptions()
                }
                true
            }
        }
    }

    inner class BridgeCommand {
        @JavascriptInterface
        fun bridgeCommand(request: String) {
            when (request) {
                "unbury" -> viewModel.onUnbury()
                "customStudy" -> onStudyMore()
            }
        }
    }

    private fun openStudyOptionsAndFinish() {
        val intent = Intent(requireContext(), StudyOptionsActivity::class.java).apply {
            putExtra("withDeckOptions", false)
        }
        startActivity(intent, null)
        requireActivity().finish()
    }

    private fun onStudyMore() {
        val col = CollectionManager.getColUnsafe()
        val dialogFragment = CustomStudyDialog(CollectionManager.getColUnsafe(), this).withArguments(
            col.decks.selected()
        )
        dialogFragment.show(childFragmentManager, null)
    }

    /******************************** CustomStudyListener methods ********************************/
    override fun onExtendStudyLimits() {
        Timber.v("CustomStudyListener::onExtendStudyLimits()")
        openStudyOptionsAndFinish()
    }

    override fun showDialogFragment(newFragment: DialogFragment) {
        Timber.v("CustomStudyListener::showDialogFragment()")
        newFragment.show(childFragmentManager, null)
    }

    override fun onCreateCustomStudySession() {
        Timber.v("CustomStudyListener::onCreateCustomStudySession()")
        openStudyOptionsAndFinish()
    }

    override fun dismissAllDialogFragments() {
        Timber.v("CustomStudyListener::dismissAllDialogFragments() - not handled")
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return getIntent(context, path = "congrats", clazz = CongratsPage::class)
        }

        private fun displayNewCongratsScreen(context: Context): Boolean =
            context.sharedPrefs().getBoolean("new_congrats_screen", false)

        fun display(activity: FragmentActivity) {
            if (displayNewCongratsScreen(activity)) {
                activity.startActivity(getIntent(activity))
            } else {
                activity.launchCatchingTask {
                    val message = getDeckFinishedMessage(activity)
                    showThemedToast(activity, message, false)
                }
            }
        }

        fun onReviewsCompleted(activity: FragmentActivity, cardsInDeck: Boolean) {
            if (displayNewCongratsScreen(activity)) {
                activity.startActivity(getIntent(activity))
                return
            }

            // Show a message when reviewing has finished
            if (cardsInDeck) {
                activity.launchCatchingTask {
                    val message = getDeckFinishedMessage(activity)
                    activity.showSnackbar(message)
                }
            } else {
                activity.showSnackbar(R.string.studyoptions_no_cards_due)
            }
        }

        // based in https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/routes/congrats/lib.ts#L8C17-L8C34
        private suspend fun getDeckFinishedMessage(activity: FragmentActivity): String {
            val info = withCol { sched.congratulationsInfo() }
            val secsUntilNextLearn = info.secsUntilNextLearn
            if (secsUntilNextLearn >= SECONDS_PER_DAY) {
                return activity.getString(R.string.studyoptions_congrats_finished)
            }
            // https://github.com/ankitects/anki/blob/9b4dd54312de8798a3f2bee07892bb3a488d1f9b/ts/lib/tslib/time.ts#L22
            val (unit, amount) = if (secsUntilNextLearn < TIME_MINUTE) {
                "seconds" to secsUntilNextLearn.toDouble()
            } else if (secsUntilNextLearn < TIME_HOUR) {
                "minutes" to secsUntilNextLearn / TIME_MINUTE
            } else {
                "hours" to secsUntilNextLearn / TIME_HOUR
            }

            val nextLearnDue = TR.schedulingNextLearnDue(unit, round(amount).toInt())
            return activity.getString(R.string.studyoptions_congrats_next_due_in, nextLearnDue)
        }

        fun DeckPicker.onDeckCompleted() {
            startActivity(getIntent(this))
        }
    }
}

class CongratsViewModel : ViewModel(), OnErrorListener {
    override val onError = MutableSharedFlow<String>()
    val openStudyOptions = MutableSharedFlow<Boolean>()
    val deckOptionsDestination = MutableSharedFlow<DeckOptionsDestination>()

    fun onUnbury() {
        launchCatchingIO {
            undoableOp {
                sched.unburyDeck(decks.getCurrentId())
            }
            openStudyOptions.emit(true)
        }
    }

    fun onDeckOptions() {
        launchCatchingIO {
            val deckId = withCol { decks.getCurrentId() }
            val isFiltered = withCol { decks.isFiltered(deckId) }
            deckOptionsDestination.emit(DeckOptionsDestination(deckId, isFiltered))
        }
    }
}

class DeckOptionsDestination(private val deckId: DeckId, private val isFiltered: Boolean) {
    fun getIntent(context: Context): Intent {
        return if (isFiltered) {
            Intent(context, FilteredDeckOptions::class.java)
        } else {
            DeckOptions.getIntent(context, deckId)
        }
    }
}
