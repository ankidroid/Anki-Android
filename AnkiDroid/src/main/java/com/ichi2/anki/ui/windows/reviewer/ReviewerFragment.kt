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
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.Flag
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.preferences.reviewer.ReviewerMenuView
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.ViewerAction.ADD_NOTE
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_CARD
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_NOTE
import com.ichi2.anki.preferences.reviewer.ViewerAction.CARD_INFO
import com.ichi2.anki.preferences.reviewer.ViewerAction.DECK_OPTIONS
import com.ichi2.anki.preferences.reviewer.ViewerAction.DELETE
import com.ichi2.anki.preferences.reviewer.ViewerAction.EDIT
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_BLUE
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_GREEN
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_ORANGE
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_PINK
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_PURPLE
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_RED
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_TURQUOISE
import com.ichi2.anki.preferences.reviewer.ViewerAction.MARK
import com.ichi2.anki.preferences.reviewer.ViewerAction.REDO
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_CARD
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_NOTE
import com.ichi2.anki.preferences.reviewer.ViewerAction.TOGGLE_AUTO_ADVANCE
import com.ichi2.anki.preferences.reviewer.ViewerAction.UNDO
import com.ichi2.anki.preferences.reviewer.ViewerAction.UNSET_FLAG
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_1
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_2
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_3
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_4
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_5
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_6
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_7
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_8
import com.ichi2.anki.preferences.reviewer.ViewerAction.USER_ACTION_9
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.menu
import com.ichi2.anki.utils.ext.removeSubMenu
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.window
import com.ichi2.libanki.sched.Counts
import kotlinx.coroutines.launch

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    ActionMenuView.OnMenuItemClickListener {
    override val viewModel: ReviewerViewModel by viewModels {
        ReviewerViewModel.factory(CardMediaPlayer())
    }

    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = this@ReviewerFragment.view?.findViewById(R.id.snackbar_anchor)
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<AppCompatImageButton>(R.id.back_button).setOnClickListener {
            requireActivity().finish()
        }

        setupImmersiveMode(view)
        setupFrame(view)
        setupTypeAnswer(view)
        setupAnswerButtons(view)
        setupCounts(view)
        setupMenu(view)

        viewModel.actionFeedbackFlow
            .flowWithLifecycle(lifecycle)
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

        viewModel.showingAnswer.collectIn(lifecycleScope) {
            resetZoom()
        }
    }

    // TODO
    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.hasSubMenu()) return false
        val action = ViewerAction.fromId(item.itemId)
        when (action) {
            ADD_NOTE -> launchAddNote()
            CARD_INFO -> launchCardInfo()
            DECK_OPTIONS -> launchDeckOptions()
            EDIT -> launchEditNote()
            DELETE -> viewModel.deleteNote()
            MARK -> viewModel.toggleMark()
            REDO -> viewModel.redo()
            UNDO -> viewModel.undo()
            TOGGLE_AUTO_ADVANCE -> viewModel.toggleAutoAdvance()
            BURY_NOTE -> viewModel.buryNote()
            BURY_CARD -> viewModel.buryCard()
            SUSPEND_NOTE -> viewModel.suspendNote()
            SUSPEND_CARD -> viewModel.suspendCard()
            UNSET_FLAG -> viewModel.setFlag(Flag.NONE)
            FLAG_RED -> viewModel.setFlag(Flag.RED)
            FLAG_ORANGE -> viewModel.setFlag(Flag.ORANGE)
            FLAG_BLUE -> viewModel.setFlag(Flag.BLUE)
            FLAG_GREEN -> viewModel.setFlag(Flag.GREEN)
            FLAG_PINK -> viewModel.setFlag(Flag.PINK)
            FLAG_TURQUOISE -> viewModel.setFlag(Flag.TURQUOISE)
            FLAG_PURPLE -> viewModel.setFlag(Flag.PURPLE)
            USER_ACTION_1 -> viewModel.userAction(1)
            USER_ACTION_2 -> viewModel.userAction(2)
            USER_ACTION_3 -> viewModel.userAction(3)
            USER_ACTION_4 -> viewModel.userAction(4)
            USER_ACTION_5 -> viewModel.userAction(5)
            USER_ACTION_6 -> viewModel.userAction(6)
            USER_ACTION_7 -> viewModel.userAction(7)
            USER_ACTION_8 -> viewModel.userAction(8)
            USER_ACTION_9 -> viewModel.userAction(9)
            SUSPEND_MENU -> viewModel.suspendCard()
            BURY_MENU -> viewModel.buryCard()
            FLAG_MENU -> return false
        }
        return true
    }

    private fun setupTypeAnswer(view: View) {
        // TODO keep text after configuration changes
        val typeAnswerContainer = view.findViewById<MaterialCardView>(R.id.type_answer_container)
        val typeAnswerEditText =
            view.findViewById<TextInputEditText>(R.id.type_answer_edit_text).apply {
                setOnEditorActionListener { editTextView, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        viewModel.onShowAnswer(editTextView.text.toString())
                        return@setOnEditorActionListener true
                    }
                    false
                }
                setOnFocusChangeListener { editTextView, hasFocus ->
                    val insetsController = WindowInsetsControllerCompat(window, editTextView)
                    if (hasFocus) {
                        insetsController.show(WindowInsetsCompat.Type.ime())
                    } else {
                        insetsController.hide(WindowInsetsCompat.Type.ime())
                    }
                }
            }
        val autoFocusTypeAnswer = Prefs.autoFocusTypeAnswer
        viewModel.typeAnswerFlow.collectIn(lifecycleScope) { typeInAnswer ->
            typeAnswerEditText.text = null
            if (typeInAnswer == null) {
                typeAnswerContainer.isVisible = false
                return@collectIn
            }
            typeAnswerContainer.isVisible = true
            typeAnswerEditText.apply {
                if (imeHintLocales != typeInAnswer.imeHintLocales) {
                    imeHintLocales = typeInAnswer.imeHintLocales
                    context?.getSystemService<InputMethodManager>()?.restartInput(this)
                }
                if (autoFocusTypeAnswer) {
                    requestFocus()
                }
            }
        }
    }

    private fun resetZoom() {
        webView.settings.loadWithOverviewMode = false
        webView.settings.loadWithOverviewMode = true
    }

    private fun setupAnswerButtons(view: View) {
        val prefs = sharedPrefs()
        val answerButtonsLayout = view.findViewById<LinearLayout>(R.id.answer_buttons)
        if (prefs.getBoolean(getString(R.string.hide_answer_buttons_key), false)) {
            // Expand the menu if there is no answer buttons in big screens
            view.findViewById<ReviewerMenuView>(R.id.reviewer_menu_view).updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxWidth = 0
            }
            answerButtonsLayout.isVisible = false
            return
        }

        fun MaterialButton.setAnswerButtonNextTime(
            @StringRes title: Int,
            nextTime: String?,
        ) {
            val titleString = context.getString(title)
            text = ReviewerViewModel.buildAnswerButtonText(titleString, nextTime)
        }

        val againButton =
            view.findViewById<MaterialButton>(R.id.again_button).apply {
                setOnClickListener { viewModel.answerAgain() }
            }
        val hardButton =
            view.findViewById<MaterialButton>(R.id.hard_button).apply {
                setOnClickListener { viewModel.answerHard() }
            }
        val goodButton =
            view.findViewById<MaterialButton>(R.id.good_button).apply {
                setOnClickListener { viewModel.answerGood() }
            }
        val easyButton =
            view.findViewById<MaterialButton>(R.id.easy_button).apply {
                setOnClickListener { viewModel.answerEasy() }
            }

        viewModel.answerButtonsNextTimeFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { times ->
                againButton.setAnswerButtonNextTime(R.string.ease_button_again, times?.again)
                hardButton.setAnswerButtonNextTime(R.string.ease_button_hard, times?.hard)
                goodButton.setAnswerButtonNextTime(R.string.ease_button_good, times?.good)
                easyButton.setAnswerButtonNextTime(R.string.ease_button_easy, times?.easy)
            }

        val showAnswerButton =
            view.findViewById<MaterialButton>(R.id.show_answer).apply {
                val editText = view.findViewById<TextInputEditText>(R.id.type_answer_edit_text)
                setOnClickListener {
                    val typedAnswer = editText?.text?.toString()
                    viewModel.onShowAnswer(typedAnswer = typedAnswer)
                }
            }

        viewModel.showingAnswer.collectLatestIn(lifecycleScope) { isAnswerShown ->
            if (isAnswerShown) {
                showAnswerButton.visibility = View.INVISIBLE
                answerButtonsLayout.visibility = View.VISIBLE
            } else {
                showAnswerButton.visibility = View.VISIBLE
                answerButtonsLayout.visibility = View.INVISIBLE
            }
        }

        if (prefs.getBoolean(getString(R.string.hide_hard_and_easy_key), false)) {
            hardButton.isVisible = false
            easyButton.isVisible = false
        }

        val buttonsHeight = Prefs.answerButtonsSize
        if (buttonsHeight != 100) {
            answerButtonsLayout.post {
                answerButtonsLayout.updateLayoutParams {
                    height = answerButtonsLayout.measuredHeight * buttonsHeight / 100
                }
            }
        }
    }

    private fun setupCounts(view: View) {
        val newCount = view.findViewById<MaterialTextView>(R.id.new_count)
        val learnCount = view.findViewById<MaterialTextView>(R.id.lrn_count)
        val reviewCount = view.findViewById<MaterialTextView>(R.id.rev_count)

        viewModel.countsFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { (counts, countsType) ->
                newCount.text = counts.new.toString()
                learnCount.text = counts.lrn.toString()
                reviewCount.text = counts.rev.toString()

                val currentCount =
                    when (countsType) {
                        Counts.Queue.NEW -> newCount
                        Counts.Queue.LRN -> learnCount
                        Counts.Queue.REV -> reviewCount
                    }
                val spannableString = SpannableString(currentCount.text)
                spannableString.setSpan(UnderlineSpan(), 0, currentCount.text.length, 0)
                currentCount.text = spannableString
            }
    }

    private fun setupBury(menu: ReviewerMenuView) {
        val menuItem = menu.findItem(BURY_MENU.menuId) ?: return
        val flow = viewModel.canBuryNoteFlow.flowWithLifecycle(lifecycle)
        flow.collectLatestIn(lifecycleScope) { canBuryNote ->
            if (canBuryNote) {
                if (menuItem.hasSubMenu()) return@collectLatestIn
                menuItem.setTitle(BURY_MENU.titleRes)
                val submenu =
                    SubMenuBuilder(menu.context, menuItem.menu, menuItem).apply {
                        add(Menu.NONE, BURY_NOTE.menuId, Menu.NONE, BURY_NOTE.titleRes)
                        add(Menu.NONE, BURY_CARD.menuId, Menu.NONE, BURY_CARD.titleRes)
                    }
                menuItem.setSubMenu(submenu)
            } else {
                menuItem.removeSubMenu()
                menuItem.setTitle(BURY_CARD.titleRes)
            }
        }
    }

    private fun setupSuspend(menu: ReviewerMenuView) {
        val menuItem = menu.findItem(SUSPEND_MENU.menuId) ?: return
        val flow = viewModel.canSuspendNoteFlow.flowWithLifecycle(lifecycle)
        flow.collectLatestIn(lifecycleScope) { canSuspendNote ->
            if (canSuspendNote) {
                if (menuItem.hasSubMenu()) return@collectLatestIn
                menuItem.setTitle(SUSPEND_MENU.titleRes)
                val submenu =
                    SubMenuBuilder(menu.context, menuItem.menu, menuItem).apply {
                        add(Menu.NONE, SUSPEND_NOTE.menuId, Menu.NONE, SUSPEND_NOTE.titleRes)
                        add(Menu.NONE, SUSPEND_CARD.menuId, Menu.NONE, SUSPEND_CARD.titleRes)
                    }
                menuItem.setSubMenu(submenu)
            } else {
                menuItem.removeSubMenu()
                menuItem.setTitle(SUSPEND_CARD.titleRes)
            }
        }
    }

    private fun setupMenu(view: View) {
        val menu = view.findViewById<ReviewerMenuView>(R.id.reviewer_menu_view)
        if (menu.isEmpty()) {
            menu.isVisible = false
            return
        }
        menu.setOnMenuItemClickListener(this)

        viewModel.flagFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { flagCode ->
                menu.findItem(FLAG_MENU.menuId)?.setIcon(flagCode.drawableRes)
            }

        setupBury(menu)
        setupSuspend(menu)

        // TODO show that the card is marked somehow when the menu item is overflowed or not shown
        val markItem = menu.findItem(MARK.menuId)
        viewModel.isMarkedFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { isMarked ->
                if (isMarked) {
                    markItem?.setIcon(R.drawable.ic_star)
                    markItem?.setTitle(R.string.menu_unmark_note)
                } else {
                    markItem?.setIcon(R.drawable.ic_star_border_white)
                    markItem?.setTitle(R.string.menu_mark_note)
                }
            }

        val undoItem = menu.findItem(UNDO.menuId)
        viewModel.undoLabelFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { label ->
                undoItem?.title = label ?: CollectionManager.TR.undoUndo()
                undoItem?.isEnabled = label != null
            }

        val redoItem = menu.findItem(REDO.menuId)
        viewModel.redoLabelFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { label ->
                redoItem?.title = label ?: CollectionManager.TR.undoRedo()
                redoItem?.isEnabled = label != null
            }
    }

    private fun setupImmersiveMode(view: View) {
        val barsToHide =
            when (Prefs.hideSystemBars) {
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

        val ignoreDisplayCutout = Prefs.ignoreDisplayCutout
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val defaultTypes = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val typeMask =
                if (ignoreDisplayCutout) {
                    defaultTypes
                } else {
                    defaultTypes or WindowInsetsCompat.Type.displayCutout()
                }
            val bars = insets.getInsets(typeMask)
            v.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupFrame(view: View) {
        if (Prefs.frameStyle == FrameStyle.BOX) {
            view.findViewById<MaterialCardView>(R.id.webview_container).apply {
                updateLayoutParams<MarginLayoutParams> {
                    topMargin = 0
                    leftMargin = 0
                    rightMargin = 0
                }
                cardElevation = 0F
                shapeAppearanceModel = ShapeAppearanceModel() // Remove corners
            }
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
            val intent = viewModel.getEditNoteDestination().toIntent(requireContext())
            noteEditorLauncher.launch(intent)
        }
    }

    private fun launchAddNote() {
        val intent = NoteEditorLauncher.AddNoteFromReviewer().toIntent(requireContext())
        noteEditorLauncher.launch(intent)
    }

    private fun launchCardInfo() {
        lifecycleScope.launch {
            val intent = viewModel.getCardInfoDestination().toIntent(requireContext())
            startActivity(intent)
        }
    }

    private val deckOptionsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshCard()
        }

    private fun launchDeckOptions() {
        lifecycleScope.launch {
            val intent = viewModel.getDeckOptionsDestination().toIntent(requireContext())
            deckOptionsLauncher.launch(intent)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = CardViewerActivity.getIntent(context, ReviewerFragment::class)
    }
}
