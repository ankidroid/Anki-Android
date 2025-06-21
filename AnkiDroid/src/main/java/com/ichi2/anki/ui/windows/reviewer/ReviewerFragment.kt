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
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.preferences.reviewer.ReviewerMenuView
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_CARD
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.BURY_NOTE
import com.ichi2.anki.preferences.reviewer.ViewerAction.FLAG_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.MARK
import com.ichi2.anki.preferences.reviewer.ViewerAction.REDO
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_CARD
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_MENU
import com.ichi2.anki.preferences.reviewer.ViewerAction.SUSPEND_NOTE
import com.ichi2.anki.preferences.reviewer.ViewerAction.UNDO
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.settings.enums.ToolbarPosition
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.menu
import com.ichi2.anki.utils.ext.removeSubMenu
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.libanki.sched.Counts
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    ActionMenuView.OnMenuItemClickListener,
    DispatchKeyEventListener,
    TagsDialogListener {
    override val viewModel: ReviewerViewModel by viewModels {
        val repository = StudyScreenRepository(sharedPrefs())
        ReviewerViewModel.factory(CardMediaPlayer(), getServerPort(), repository)
    }

    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        val fragmentView = this@ReviewerFragment.view
        val typeAnswerContainer = fragmentView?.findViewById<View>(R.id.type_answer_container)
        val answerArea = fragmentView?.findViewById<View>(R.id.answer_area)
        anchorView =
            when {
                typeAnswerContainer?.isVisible == true -> typeAnswerContainer
                answerArea?.isVisible == true -> answerArea
                (Prefs.toolbarPosition == ToolbarPosition.BOTTOM || !resources.isWindowCompact()) ->
                    fragmentView?.findViewById(
                        R.id.tools_layout,
                    )
                else -> null
            }
    }

    private lateinit var tagsDialogFactory: TagsDialogFactory

    override fun onLoadInitialHtml(): String =
        stdHtml(
            context = requireContext(),
            extraJsAssets = listOf("scripts/ankidroid.js"),
            nightMode = Themes.currentTheme.isNightMode,
        )

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(requireActivity())
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnGenericMotionListener { _, event ->
            viewModel.onGenericMotionEvent(event)
        }

        view.findViewById<AppCompatImageButton>(R.id.back_button).setOnClickListener {
            requireActivity().finish()
        }

        setupImmersiveMode(view)
        setupFrame(view)
        setupTypeAnswer(view)
        setupAnswerButtons(view)
        setupCounts(view)
        setupMenu(view)
        setupToolbarPosition(view)

        viewModel.actionFeedbackFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { message ->
                showSnackbar(message, duration = 500)
            }

        viewModel.finishResultFlow.collectIn(lifecycleScope) { result ->
            requireActivity().run {
                setResult(result)
                finish()
            }
        }

        viewModel.statesMutationEval.collectIn(lifecycleScope) { eval ->
            webView.evaluateJavascript(eval) {
                viewModel.onStateMutationCallback()
            }
        }

        viewModel.showingAnswer.collectIn(lifecycleScope) {
            resetZoom()
            // focus on the whole layout so motion controllers can be captured
            // without navigating the other View elements
            view.findViewById<CoordinatorLayout>(R.id.root_layout).requestFocus()
        }

        viewModel.destinationFlow.collectIn(lifecycleScope) { destination ->
            startActivity(destination.toIntent(requireContext()))
        }

        viewModel.editNoteTagsFlow.collectIn(lifecycleScope) { noteId ->
            val dialogFragment =
                tagsDialogFactory.newTagsDialog().withArguments(
                    requireContext(),
                    TagsDialog.DialogType.EDIT_TAGS,
                    listOf(noteId),
                )
            showDialogFragment(dialogFragment)
        }

        viewModel.setDueDateFlow.collectIn(lifecycleScope) { cardId ->
            val dialogFragment = SetDueDateDialog.newInstance(listOf(cardId))
            showDialogFragment(dialogFragment)
        }
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (view?.findViewById<TextInputEditText>(R.id.type_answer_edit_text)?.isFocused == true) {
            return false
        }
        return viewModel.dispatchKeyEvent(event)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = viewModel.onMenuItemClick(item)

    private fun setupAnswerButtons(view: View) {
        val prefs = sharedPrefs()
        val answerArea = view.findViewById<FrameLayout>(R.id.answer_area)
        if (prefs.getBoolean(getString(R.string.hide_answer_buttons_key), false)) {
            answerArea.isVisible = false
            if (!resources.isWindowCompact()) {
                val constraintLayout = view.findViewById<ConstraintLayout>(R.id.tools_layout)
                // Expand the menu if there is no answer buttons in big screens
                with(ConstraintSet()) {
                    clone(constraintLayout)
                    clear(R.id.reviewer_menu_view, ConstraintSet.START)
                    connect(
                        R.id.reviewer_menu_view,
                        ConstraintSet.START,
                        R.id.guideline_counts,
                        ConstraintSet.END,
                    )
                    applyTo(constraintLayout)
                }
            }
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
        val answerButtonsLayout = view.findViewById<LinearLayout>(R.id.answer_buttons)

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

    private fun setupToolbarPosition(view: View) {
        if (!resources.isWindowCompact()) return
        when (Prefs.toolbarPosition) {
            ToolbarPosition.NONE -> {
                view.findViewById<View>(R.id.tools_layout).isVisible = false
                view.findViewById<MaterialCardView>(R.id.webview_container).updateLayoutParams<MarginLayoutParams> {
                    topMargin = 8F.dp.toPx(requireContext())
                }
            }
            ToolbarPosition.BOTTOM -> {
                val mainLayout = view.findViewById<LinearLayout>(R.id.main_layout)
                val toolbar = view.findViewById<View>(R.id.tools_layout)
                val answerArea = view.findViewById<FrameLayout>(R.id.answer_area)

                mainLayout.removeView(toolbar)
                mainLayout.addView(toolbar, mainLayout.indexOfChild(answerArea) + 1)

                answerArea.updateLayoutParams<MarginLayoutParams> {
                    bottomMargin = 0
                }
                view.findViewById<MaterialCardView>(R.id.webview_container).updateLayoutParams<MarginLayoutParams> {
                    topMargin = 8F.dp.toPx(requireContext())
                }
            }
            ToolbarPosition.TOP -> return
        }
    }

    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) = viewModel.onEditedTags(selectedTags)

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): WebViewClient = ReviewerWebViewClient(savedInstanceState)

    private inner class ReviewerWebViewClient(
        savedInstanceState: Bundle?,
    ) : CardViewerWebViewClient(savedInstanceState) {
        @Suppress("DEPRECATION") // the deprecation suggests using `onScaleChanged` to avoid
        // race conditions when the scale is being changed. The method is already being used below
        // for that matter. For only getting the initial scale, it's safe to use the property.
        // Robolectric crashes with 'java.lang.Integer cannot be cast to class java.lang.Float'
        private var scale: Float = if (!isRobolectric) webView.scale else 1F
        private var isScrolling: Boolean = false
        private var isScrollingJob: Job? = null

        init {
            webView.setOnScrollChangeListener { _, _, _, _, _ ->
                isScrolling = true
                isScrollingJob?.cancel()
                isScrollingJob =
                    lifecycleScope.launch {
                        delay(300)
                        isScrolling = false
                    }
            }
        }

        override fun handleUrl(url: Uri): Boolean {
            return when (url.scheme) {
                "gesture" -> {
                    val gesture = GestureParser.parse(url, isScrolling, scale, webView) ?: return true
                    viewModel.onGesture(gesture)
                    true
                }
                else -> super.handleUrl(url)
            }
        }

        override fun onScaleChanged(
            view: WebView?,
            oldScale: Float,
            newScale: Float,
        ) {
            super.onScaleChanged(view, oldScale, newScale)
            scale = newScale
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = CardViewerActivity.getIntent(context, ReviewerFragment::class)

        fun getServerPort(): Int {
            if (!Prefs.useFixedPortInReviewer) return 0
            return try {
                ServerSocket(Prefs.reviewerPort)
                    .use {
                        it.reuseAddress = true
                        it.localPort
                    }.also {
                        if (Prefs.reviewerPort == 0) {
                            Prefs.reviewerPort = it
                        }
                    }
            } catch (_: BindException) {
                Timber.w("Fixed port %d under use. Using dynamic port", Prefs.reviewerPort)
                0
            }
        }
    }
}
