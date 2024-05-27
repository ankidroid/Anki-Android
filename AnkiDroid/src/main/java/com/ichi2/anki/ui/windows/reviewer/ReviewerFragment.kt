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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.navBarNeedsScrim
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAnswerButtons(view)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@ReviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            (menu as? MenuBuilder)?.let {
                setupMenuItems(it)
                it.setOptionalIconsVisible(true)
                requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(it)
            }
        }

        with(requireActivity()) {
            if (!navBarNeedsScrim) {
                window.navigationBarColor =
                    ThemeUtils.getThemeAttrColor(this, R.attr.alternativeBackgroundColor)
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
            R.id.action_card_info -> launchCardInfo()
            R.id.action_delete -> viewModel.deleteNote()
            R.id.action_edit -> launchEditNote()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_open_deck_options -> launchDeckOptions()
        }
        return true
    }

    private fun setupAnswerButtons(view: View) {
        view.findViewById<MaterialButton>(R.id.again_button).setOnClickListener {
            viewModel.answerAgain()
        }
        view.findViewById<MaterialButton>(R.id.hard_button).setOnClickListener {
            viewModel.answerHard()
        }
        view.findViewById<MaterialButton>(R.id.good_button).setOnClickListener {
            viewModel.answerGood()
        }
        view.findViewById<MaterialButton>(R.id.easy_button).setOnClickListener {
            viewModel.answerEasy()
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
    }

    private fun setupMenuItems(menu: Menu) {
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
    }

    private val noteEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.handleNoteEditorResult(result)
        }

    private fun launchEditNote() {
        lifecycleScope.launch {
            val intent = viewModel.getEditNoteDestination().toIntent(requireContext())
            noteEditorLauncher.launch(intent)
        }
    }

    private fun launchAddNote() {
        val intent = Intent(context, NoteEditor::class.java).apply {
            putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD)
        }
        noteEditorLauncher.launch(intent)
    }

    private fun launchCardInfo() {
        lifecycleScope.launch {
            val intent = viewModel.getCardInfoDestination().toIntent(requireContext())
            startActivity(intent)
        }
    }

    private val deckOptionsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.handleDeckOptionsResult()
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
