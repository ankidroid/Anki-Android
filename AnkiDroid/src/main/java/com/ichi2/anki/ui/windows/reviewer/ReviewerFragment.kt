/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.libanki.sched.Counts
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import kotlinx.coroutines.launch

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    Toolbar.OnMenuItemClickListener {

    override val viewModel: ReviewerViewModel by viewModels {
        ReviewerViewModel.factory(CardMediaPlayer())
    }

    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = this@ReviewerFragment.view?.findViewById(R.id.buttons_area)
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImmersiveMode(view)
        setupAnswerButtons(view)
        setupCounts(view)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@ReviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            (menu as? MenuBuilder)?.let {
                setupMenuItems(it)
                it.setOptionalIconsVisible(true)
                requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(it)
            }
        }

        viewModel.actionFeedbackFlow.flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { message ->
                showSnackbar(message, duration = 500)
            }

        viewModel.isQueueFinishedFlow.collectIn(lifecycleScope) { isQueueFinished ->
            if (isQueueFinished) {
                requireActivity().run {
                    setResult(RESULT_NO_MORE_CARDS)
                    finish()
                }
            }
        }

        viewModel.statesMutationEval.collectIn(lifecycleScope) { eval ->
            webView.evaluateJavascript(eval) {
                viewModel.onStateMutationCallback()
            }
        }
    }

    // TODO
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_note -> launchAddNote()
            R.id.action_bury_card -> viewModel.buryCard()
            R.id.action_bury_note -> viewModel.buryNote()
            R.id.action_card_info -> launchCardInfo()
            R.id.action_delete -> viewModel.deleteNote()
            R.id.action_edit -> launchEditNote()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_open_deck_options -> launchDeckOptions()
            R.id.action_redo -> viewModel.redo()
            R.id.action_suspend_card -> viewModel.suspendCard()
            R.id.action_suspend_note -> viewModel.suspendNote()
            R.id.action_undo -> viewModel.undo()
            R.id.flag_none -> viewModel.setFlag(Flag.NONE)
            R.id.flag_red -> viewModel.setFlag(Flag.RED)
            R.id.flag_orange -> viewModel.setFlag(Flag.ORANGE)
            R.id.flag_green -> viewModel.setFlag(Flag.GREEN)
            R.id.flag_blue -> viewModel.setFlag(Flag.BLUE)
            R.id.flag_pink -> viewModel.setFlag(Flag.PINK)
            R.id.flag_turquoise -> viewModel.setFlag(Flag.TURQUOISE)
            R.id.flag_purple -> viewModel.setFlag(Flag.PURPLE)
            R.id.user_action_1 -> viewModel.userAction(1)
            R.id.user_action_2 -> viewModel.userAction(2)
            R.id.user_action_3 -> viewModel.userAction(3)
            R.id.user_action_4 -> viewModel.userAction(4)
            R.id.user_action_5 -> viewModel.userAction(5)
            R.id.user_action_6 -> viewModel.userAction(6)
            R.id.user_action_7 -> viewModel.userAction(7)
            R.id.user_action_8 -> viewModel.userAction(8)
            R.id.user_action_9 -> viewModel.userAction(9)
        }
        return true
    }

    private fun setupAnswerButtons(view: View) {
        val hideAnswerButtons = sharedPrefs().getBoolean(getString(R.string.hide_answer_buttons_key), false)
        if (hideAnswerButtons) {
            view.findViewById<FrameLayout>(R.id.buttons_area).isVisible = false
            return
        }

        fun MaterialButton.setAnswerButtonNextTime(@StringRes title: Int, nextTime: String?) {
            val titleString = context.getString(title)
            text = ReviewerViewModel.buildAnswerButtonText(titleString, nextTime)
        }

        val againButton = view.findViewById<MaterialButton>(R.id.again_button).apply {
            setOnClickListener { viewModel.answerAgain() }
        }
        val hardButton = view.findViewById<MaterialButton>(R.id.hard_button).apply {
            setOnClickListener { viewModel.answerHard() }
        }
        val goodButton = view.findViewById<MaterialButton>(R.id.good_button).apply {
            setOnClickListener { viewModel.answerGood() }
        }
        val easyButton = view.findViewById<MaterialButton>(R.id.easy_button).apply {
            setOnClickListener { viewModel.answerEasy() }
        }

        viewModel.answerButtonsNextTimeFlow.flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { times ->
                againButton.setAnswerButtonNextTime(R.string.ease_button_again, times?.again)
                hardButton.setAnswerButtonNextTime(R.string.ease_button_hard, times?.hard)
                goodButton.setAnswerButtonNextTime(R.string.ease_button_good, times?.good)
                easyButton.setAnswerButtonNextTime(R.string.ease_button_easy, times?.easy)
            }

        val showAnswerButton = view.findViewById<MaterialButton>(R.id.show_answer).apply {
            setOnClickListener {
                viewModel.showAnswer()
            }
        }
        val answerButtonsLayout = view.findViewById<ConstraintLayout>(R.id.answer_buttons)

        // TODO add some kind of feedback/animation after tapping show answer or the answer buttons
        viewModel.showingAnswer.collectLatestIn(lifecycleScope) { shouldShowAnswer ->
            if (shouldShowAnswer) {
                showAnswerButton.isVisible = false
                answerButtonsLayout.isVisible = true
            } else {
                showAnswerButton.isVisible = true
                answerButtonsLayout.isVisible = false
            }
        }

        if (sharedPrefs().getBoolean(getString(R.string.hide_hard_and_easy_key), false)) {
            hardButton.isVisible = false
            easyButton.isVisible = false
        }
    }

    private fun setupCounts(view: View) {
        val newCount = view.findViewById<MaterialTextView>(R.id.new_count)
        val learnCount = view.findViewById<MaterialTextView>(R.id.lrn_count)
        val reviewCount = view.findViewById<MaterialTextView>(R.id.rev_count)

        viewModel.countsFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { (counts, countsType) ->
                newCount.text = counts.new.toString()
                learnCount.text = counts.lrn.toString()
                reviewCount.text = counts.rev.toString()

                val currentCount = when (countsType) {
                    Counts.Queue.NEW -> newCount
                    Counts.Queue.LRN -> learnCount
                    Counts.Queue.REV -> reviewCount
                }
                val spannableString = SpannableString(currentCount.text)
                spannableString.setSpan(UnderlineSpan(), 0, currentCount.text.length, 0)
                currentCount.text = spannableString
            }
    }

    private fun setupFlagMenu(menu: Menu) {
        val submenu = menu.findItem(R.id.action_flag).subMenu
        lifecycleScope.launch {
            for ((flag, name) in Flag.queryDisplayNames()) {
                submenu?.add(Menu.NONE, flag.id, Menu.NONE, name)
                    ?.setIcon(flag.drawableRes)
            }
        }

        viewModel.flagFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { flagCode ->
                menu.findItem(R.id.action_flag).setIcon(flagCode.drawableRes)
            }
    }

    private fun setupMenuItems(menu: Menu) {
        setupFlagMenu(menu)

        // TODO show that the card is marked somehow when the menu item is overflowed or not shown
        val markItem = menu.findItem(R.id.action_mark)
        viewModel.isMarkedFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { isMarked ->
                if (isMarked) {
                    markItem.setIcon(R.drawable.ic_star)
                    markItem.setTitle(R.string.menu_unmark_note)
                } else {
                    markItem.setIcon(R.drawable.ic_star_border_white)
                    markItem.setTitle(R.string.menu_mark_note)
                }
            }

        val buryItem = menu.findItem(R.id.action_bury)
        val buryCardItem = menu.findItem(R.id.action_bury_card)
        viewModel.canBuryNoteFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { canBuryNote ->
                if (canBuryNote) {
                    buryItem.isVisible = true
                    buryCardItem.isVisible = false
                } else {
                    buryItem.isVisible = false
                    buryCardItem.isVisible = true
                }
            }

        val suspendItem = menu.findItem(R.id.action_suspend)
        val suspendCardItem = menu.findItem(R.id.action_suspend_card)
        viewModel.canSuspendNoteFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { canSuspendNote ->
                if (canSuspendNote) {
                    suspendItem.isVisible = true
                    suspendCardItem.isVisible = false
                } else {
                    suspendItem.isVisible = false
                    suspendItem.isVisible = true
                }
            }

        val undoItem = menu.findItem(R.id.action_undo)
        viewModel.undoLabelFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { label ->
                undoItem.title = label ?: CollectionManager.TR.undoUndo()
                undoItem.isEnabled = label != null
            }

        val redoItem = menu.findItem(R.id.action_redo)
        viewModel.redoLabelFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { label ->
                redoItem.title = label ?: CollectionManager.TR.undoRedo()
                redoItem.isEnabled = label != null
            }
    }

    private fun setupImmersiveMode(view: View) {
        val hideSystemBarsSetting = HideSystemBars.from(requireContext())
        val barsToHide = when (hideSystemBarsSetting) {
            HideSystemBars.NONE -> return
            HideSystemBars.STATUS_BAR -> WindowInsetsCompat.Type.statusBars()
            HideSystemBars.NAVIGATION_BAR -> WindowInsetsCompat.Type.navigationBars()
            HideSystemBars.ALL -> WindowInsetsCompat.Type.systemBars()
        }

        val window = requireActivity().window
        with(WindowInsetsControllerCompat(window, window.decorView)) {
            hide(barsToHide)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val ignoreDisplayCutout = sharedPrefs().getBoolean(getString(R.string.ignore_display_cutout_key), false)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val typeMask = if (ignoreDisplayCutout) {
                WindowInsetsCompat.Type.systemBars()
            } else {
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            }
            val bars = insets.getInsets(typeMask)
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private val noteEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.data?.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) == true ||
                result.data?.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false) == true
            ) {
                viewModel.refreshCard()
            }
        }

    private fun launchEditNote() {
        lifecycleScope.launch {
            val intent = viewModel.getEditNoteDestination().getIntent(requireContext())
            noteEditorLauncher.launch(intent)
        }
    }

    private fun launchAddNote() {
        val intent = NoteEditorLauncher.AddNoteFromReviewer().getIntent(requireContext())
        noteEditorLauncher.launch(intent)
    }

    private fun launchCardInfo() {
        lifecycleScope.launch {
            val intent = viewModel.getCardInfoDestination().toIntent(requireContext())
            startActivity(intent)
        }
    }

    private val deckOptionsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshCard()
    }

    private fun launchDeckOptions() {
        lifecycleScope.launch {
            val intent = viewModel.getDeckOptionsDestination().getIntent(requireContext())
            deckOptionsLauncher.launch(intent)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return CardViewerActivity.getIntent(context, ReviewerFragment::class)
        }
    }
}
