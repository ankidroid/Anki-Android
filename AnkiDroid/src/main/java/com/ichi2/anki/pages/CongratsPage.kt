// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.collection.OpChanges
import anki.scheduler.UnburyDeckRequest
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.study.StudyAvailability
import com.ichi2.anki.study.currentDeckStudyState
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.listItems
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class CongratsPage :
    PageFragment(),
    ChangeManager.Subscriber {
    override val pagePath: String = "congrats"

    private val viewModel by viewModels<CongratsViewModel>()

    init {
        ChangeManager.subscribe(this)
    }

    // TODO: move this to the ViewModel
    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // typically due to 'day rollover'. handler !== viewModel excludes changes this
        // screen caused itself (eg. unburying), which are already handled directly
        if (changes.studyQueues && handler !== viewModel) {
            Timber.i("study queues updated")
            viewModel.onStudyQueuesChanged()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(CommonString.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }.launchIn(lifecycleScope)

        viewModel.unburyState
            .onEach { state ->
                when (state) {
                    UnburyState.OpenStudy -> openStudyOptionsAndFinish()
                    UnburyState.SelectMode -> {
                        val unburyOptions =
                            mutableListOf(
                                TR.studyingManuallyBuriedCards(),
                                TR.studyingBuriedSiblings(),
                                TR.studyingAllBuriedCards(),
                            )
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.studyingWhatWouldYouLikeToUnbury())
                            negativeButton(CommonString.dialog_cancel)
                            listItems(
                                unburyOptions,
                            ) { _, position ->
                                val mode =
                                    when (position) {
                                        0 -> UnburyDeckRequest.Mode.USER_ONLY
                                        1 -> UnburyDeckRequest.Mode.SCHED_ONLY
                                        2 -> UnburyDeckRequest.Mode.ALL
                                        else -> error("Unhandled unbury option: ${unburyOptions[position]}")
                                    }
                                viewModel.onUnburyModeSelected(mode)
                            }
                        }
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.deckOptionsDestination
            .flowWithLifecycle(lifecycle)
            .onEach { destination ->
                navigate(destination)
            }.launchIn(lifecycleScope)

        // a rebuild triggered elsewhere (eg. deck options) while this screen wasn't visible may
        // have been missed, so recheck on every STARTED entry instead of trusting only the
        // opExecuted callback. Launched from inside repeatOnLifecycle so the collector is
        // guaranteed active before it emits (congratsRefreshState has no replay cache, so
        // emitting with no active collector silently drops the event).
        // viewLifecycleOwner (not the fragment's own lifecycle) is required by lint - see
        // UnsafeRepeatOnLifecycleDetector.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.refreshIfNeeded() }
                viewModel.congratsRefreshState.collect { state ->
                    when (state) {
                        CongratsRefreshState.ReloadPage -> {
                            Timber.d("congrats: reloading page after rebuild")
                            webViewLayout.post { webViewLayout.reload() }
                        }
                        CongratsRefreshState.DeckHasCardsToStudy -> openStudyOptionsAndFinish()
                    }
                }
            }
        }

        with(view.findViewById<MaterialToolbar>(R.id.toolbar)) {
            inflateMenu(R.menu.congrats)
            menu.findItem(R.id.action_open_deck_options)?.title = TR.sentenceCase.deckOptions
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_open_deck_options) {
                    viewModel.onDeckOptions()
                }
                true
            }
        }

        setFragmentResultListener(CustomStudyAction.REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION,
                CustomStudyAction.EXTEND_STUDY_LIMITS,
                -> openStudyOptionsAndFinish()
            }
        }
    }

    override val bridgeCommands =
        mapOf(
            "unbury" to { viewModel.onUnbury() },
            "customStudy" to { onStudyMore() },
        )

    private fun openStudyOptionsAndFinish() {
        Timber.i("opening study options")
        navigate(StudyOptionsDestination)
        requireActivity().finish()
    }

    private fun onStudyMore() {
        launchCatchingTask {
            val customStudy = CustomStudyDialog.createInstance(deckId = withCol { decks.selected() })
            requireActivity().showDialogFragment(customStudy)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = SingleFragmentActivity.getIntent(context, fragmentClass = CongratsPage::class)
    }
}

class CongratsViewModel :
    ViewModel(),
    OnErrorListener {
    override val onError = MutableSharedFlow<String>()
    val unburyState = MutableSharedFlow<UnburyState>()
    val deckOptionsDestination = MutableSharedFlow<DeckOptionsDestination>()

    fun onUnbury() {
        launchCatchingIO {
            // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/overview.py#L154
            val congratsInfo = withCol { sched.congratulationsInfo() }
            if (congratsInfo.haveSchedBuried && congratsInfo.haveUserBuried) {
                unburyState.emit(UnburyState.SelectMode)
                return@launchCatchingIO
            }
            unburyAndStudy(UnburyDeckRequest.Mode.ALL)
        }
    }

    fun onUnburyModeSelected(mode: UnburyDeckRequest.Mode) {
        launchCatchingIO {
            unburyAndStudy(mode)
        }
    }

    private suspend fun unburyAndStudy(mode: UnburyDeckRequest.Mode) {
        undoableOp(handler = this) {
            sched.unburyDeck(decks.getCurrentId(), mode)
        }
        unburyState.emit(UnburyState.OpenStudy)
    }

    fun onDeckOptions() {
        launchCatchingIO {
            val deckId = withCol { decks.getCurrentId() }
            val isFiltered = withCol { decks.isFiltered(deckId) }
            deckOptionsDestination.emit(DeckOptionsDestination(deckId, isFiltered))
        }
    }

    val congratsRefreshState = MutableSharedFlow<CongratsRefreshState>()

    // set when onStudyQueuesChanged() has nothing to emit to (screen not STARTED, so
    // congratsRefreshState has no collector and would silently drop the event) - consumed
    // and cleared by refreshIfNeeded() on the next STARTED
    private var missedRefresh = false

    fun onStudyQueuesChanged() {
        launchCatchingIO {
            if (congratsRefreshState.subscriptionCount.value == 0) {
                missedRefresh = true
                return@launchCatchingIO
            }
            emitRefreshState()
        }
    }

    /** Called every time the screen becomes STARTED. [isDeckFinished] is always checked,
     * but reload/navigation only happens if something was actually
     * missed while unwatched, or the deck no longer needs the congrats page. */
    suspend fun refreshIfNeeded() {
        // desktop's Overview re-checks sched._is_finished() on every refresh instead of
        // assuming the deck is still done - do the same before reloading the congrats page
        val stillFinished = isDeckFinished()
        if (!missedRefresh && stillFinished) return
        missedRefresh = false
        congratsRefreshState.emit(
            if (stillFinished) CongratsRefreshState.ReloadPage else CongratsRefreshState.DeckHasCardsToStudy,
        )
    }

    private suspend fun emitRefreshState() {
        missedRefresh = false
        val stillFinished = isDeckFinished()
        congratsRefreshState.emit(
            if (stillFinished) CongratsRefreshState.ReloadPage else CongratsRefreshState.DeckHasCardsToStudy,
        )
    }

    private suspend fun isDeckFinished(): Boolean = withCol { currentDeckStudyState().availability == StudyAvailability.NoCardsAvailable }
}

sealed class UnburyState {
    data object OpenStudy : UnburyState()

    data object SelectMode : UnburyState()
}

sealed class CongratsRefreshState {
    data object ReloadPage : CongratsRefreshState()

    data object DeckHasCardsToStudy : CongratsRefreshState()
}
