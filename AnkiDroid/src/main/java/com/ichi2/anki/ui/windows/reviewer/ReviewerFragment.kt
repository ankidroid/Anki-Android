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
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ActionMenuView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.scheduler.CardAnswer.Rating
import com.google.android.material.shape.ShapeAppearanceModel
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.databinding.Reviewer2Binding
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.settings.enums.ToolbarPosition
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.ext.window
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.themes.Themes
import com.ichi2.utils.dp
import com.ichi2.utils.show
import com.squareup.seismic.ShakeDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt
import kotlin.reflect.jvm.jvmName

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    ActionMenuView.OnMenuItemClickListener,
    DispatchKeyEventListener,
    TagsDialogListener,
    ShakeDetector.Listener {
    override val viewModel: ReviewerViewModel by viewModels()

    // binding pattern to handle onCreateView/onDestroyView
    private var fragmentBinding: Reviewer2Binding? = null
    private val binding: Reviewer2Binding
        get() = fragmentBinding!!

    override val webView: WebView
        get() = binding.webView
    private val timer: AnswerTimer?
        get() = fragmentBinding?.timer
    private lateinit var bindingMap: BindingMap<ReviewerBinding, ViewerAction>
    private var shakeDetector: ShakeDetector? = null
    private val sensorManager get() = ContextCompat.getSystemService(requireContext(), SensorManager::class.java)
    private var webviewHasFocus = false

    override val baseSnackbarBuilder: SnackbarBuilder = {
        val typeAnswerContainer = fragmentBinding?.typeAnswerContainer
        val answerArea = fragmentBinding?.answerArea
        anchorView =
            when {
                typeAnswerContainer?.isVisible == true -> typeAnswerContainer
                answerArea?.isVisible == true -> answerArea
                (Prefs.toolbarPosition == ToolbarPosition.BOTTOM || !resources.isWindowCompact()) ->
                    fragmentBinding?.toolsLayout
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = Reviewer2Binding
        .inflate(inflater, container, false)
        .apply {
            fragmentBinding = this
        }.root

    override fun onStart() {
        super.onStart()
        if (!requireActivity().isChangingConfigurations) {
            if (viewModel.answerTimerStatusFlow.value is AnswerTimerStatus.Running) {
                timer?.resume()
            }
            shakeDetector?.start(sensorManager, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) {
            viewModel.stopAutoAdvance()
            timer?.stop()
            shakeDetector?.stop()
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

        binding.backButton.setOnClickListener {
            requireActivity().finish()
        }

        setupBindings(view)
        setupImmersiveMode(view)
        setupFrame()
        setupTypeAnswer()
        setupAnswerButtons()
        setupCounts()
        setupMenu()
        setupToolbarPosition()
        setupAnswerTimer()
        setupMargins()
        setupCheckPronunciation()
        setupWhiteboard()
        setupTimebox()

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
            binding.rootLayout.requestFocus()
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

        if (Prefs.showAnswerFeedback) {
            viewModel.answerFeedbackFlow.collectIn(lifecycleScope) { ease ->
                if (ease == Rating.AGAIN) {
                    binding.wrongAnswerFeedback.toggle()
                    return@collectIn
                }
                val drawableId =
                    when (ease) {
                        Rating.HARD -> R.drawable.ic_ease_hard
                        Rating.GOOD -> R.drawable.ic_ease_good
                        Rating.EASY -> R.drawable.ic_ease_easy
                        Rating.AGAIN, Rating.UNRECOGNIZED -> throw IllegalArgumentException("Invalid rating")
                    }
                binding.correctAnswerFeedback.apply {
                    setImageResource(drawableId)
                    toggle()
                }
            }
        }

        val repository = StudyScreenRepository(sharedPrefs())

        viewModel.isMarkedFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { isMarked ->
                if (!repository.isMarkShownInToolbar) {
                    binding.markIcon.isVisible = isMarked
                }
            }
        viewModel.flagFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { flag ->
                if (!repository.isFlagShownInToolbar) {
                    if (flag == Flag.NONE) {
                        binding.flagIcon.isVisible = false
                    } else {
                        binding.flagIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), flag.drawableRes))
                        binding.flagIcon.isVisible = true
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentBinding = null
    }

    private fun setupTypeAnswer() {
        val typeAnswerContainer = binding.typeAnswerContainer
        val typeAnswerEditText = binding.typeAnswerEditText

        typeAnswerEditText.apply {
            setOnEditorActionListener { editTextView, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    viewModel.onShowAnswer()
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
            addTextChangedListener { editable ->
                viewModel.typedAnswer = editable?.toString() ?: ""
            }
        }

        lifecycleScope.launch {
            if (Prefs.isHtmlTypeAnswerEnabled) return@launch
            val autoFocusTypeAnswer = Prefs.autoFocusTypeAnswer
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.typeAnswerFlow.collect { typeInAnswer ->
                    if (typeInAnswer == null) {
                        typeAnswerContainer.isVisible = false
                        return@collect
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
        }
        viewModel.onCardUpdatedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            typeAnswerEditText.text = null
        }
    }

    private fun resetZoom() {
        webView.settings.loadWithOverviewMode = false
        webView.settings.loadWithOverviewMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (webviewHasFocus ||
            event.action != KeyEvent.ACTION_DOWN ||
            fragmentBinding?.typeAnswerEditText?.isFocused == true
        ) {
            return false
        }
        return bindingMap.onKeyDown(event)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        Timber.v("ReviewerFragment::onMenuItemClick %s", item)
        if (item.hasSubMenu()) return false
        val action = ViewerAction.fromId(item.itemId)
        viewModel.executeAction(action)
        return true
    }

    override fun hearShake() {
        bindingMap.onGesture(Gesture.SHAKE)
    }

    private fun setupBindings(view: View) {
        bindingMap = BindingMap(sharedPrefs(), ViewerAction.entries, viewModel)
        view.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }
        if (bindingMap.isBound(Gesture.SHAKE)) {
            shakeDetector = ShakeDetector(this)
            shakeDetector?.start(sensorManager, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun setupAnswerButtons() {
        val answerArea = binding.answerArea
        if (!Prefs.showAnswerButtons) {
            answerArea.isVisible = false
            return
        }

        binding.againButton.setOnClickListener { viewModel.answerCard(Rating.AGAIN) }
        binding.hardButton.setOnClickListener { viewModel.answerCard(Rating.HARD) }
        binding.goodButton.setOnClickListener { viewModel.answerCard(Rating.GOOD) }
        binding.easyButton.setOnClickListener { viewModel.answerCard(Rating.EASY) }
        viewModel.answerButtonsNextTimeFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { times ->
                binding.againButton.setNextTime(times?.again)
                binding.hardButton.setNextTime(times?.hard)
                binding.goodButton.setNextTime(times?.good)
                binding.easyButton.setNextTime(times?.easy)
            }

        binding.showAnswerButton.setOnClickListener { viewModel.onShowAnswer() }

        viewModel.showingAnswer.collectLatestIn(lifecycleScope) { isAnswerShown ->
            if (isAnswerShown) {
                binding.showAnswerButton.visibility = View.INVISIBLE
                binding.answerButtonsLayout.visibility = View.VISIBLE
            } else {
                binding.showAnswerButton.visibility = View.VISIBLE
                binding.answerButtonsLayout.visibility = View.INVISIBLE
            }
        }

        if (sharedPrefs().getBoolean(getString(R.string.hide_hard_and_easy_key), false)) {
            binding.hardButton.isVisible = false
            binding.easyButton.isVisible = false
        }

        val buttonsHeight = Prefs.newStudyScreenAnswerButtonSize
        if (buttonsHeight > 100) {
            binding.answerButtonsLayout.post {
                binding.answerButtonsLayout.updateLayoutParams {
                    height = binding.answerButtonsLayout.measuredHeight * buttonsHeight / 100
                }
            }
        }
    }

    private fun setupCounts() {
        viewModel.countsFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { (counts, countsType) ->
                binding.newCount.text = counts.new.toString()
                binding.learnCount.text = counts.lrn.toString()
                binding.reviewCount.text = counts.rev.toString()

                val currentCount =
                    when (countsType) {
                        Counts.Queue.NEW -> binding.newCount
                        Counts.Queue.LRN -> binding.learnCount
                        Counts.Queue.REV -> binding.reviewCount
                    }
                val spannableString = SpannableString(currentCount.text)
                spannableString.setSpan(UnderlineSpan(), 0, currentCount.text.length, 0)
                currentCount.text = spannableString
            }

        lifecycleScope.launch {
            if (!CollectionPreferences.getShowRemainingDueCounts()) {
                binding.newCount.isVisible = false
                binding.learnCount.isVisible = false
                binding.reviewCount.isVisible = false
            }
        }
    }

    private fun setupMenu() {
        binding.reviewerMenuView.apply {
            setup(lifecycle, viewModel)
            setOnMenuItemClickListener(this@ReviewerFragment)
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

    private fun setupFrame() {
        if (Prefs.frameStyle == FrameStyle.BOX) {
            binding.webViewContainer.apply {
                updateLayoutParams<MarginLayoutParams> {
                    leftMargin = 0
                    rightMargin = 0
                }
                cardElevation = 0F
                shapeAppearanceModel = ShapeAppearanceModel() // Remove corners
            }
        }
    }

    private fun setupToolbarPosition() {
        if (!resources.isWindowCompact()) return
        when (Prefs.toolbarPosition) {
            ToolbarPosition.TOP -> return
            ToolbarPosition.NONE -> binding.toolsLayout.isVisible = false
            ToolbarPosition.BOTTOM -> {
                val mainLayout = binding.mainLayout!! // we can use !! due to isWindowCompact
                mainLayout.removeView(binding.toolsLayout)
                mainLayout.addView(binding.toolsLayout, mainLayout.childCount)
            }
        }
    }

    /**
     * Updates margins based on the possible combinations
     * of [Prefs.toolbarPosition] and `Hide answer buttons`
     */
    private fun setupMargins() {
        val hideAnswerButtons = !Prefs.showAnswerButtons
        // In big screens, let the menu expand if there are no answer buttons
        if (hideAnswerButtons && !resources.isWindowCompact()) {
            with(ConstraintSet()) {
                clone(binding.toolsLayout)
                clear(R.id.reviewer_menu_view, ConstraintSet.START)
                connect(
                    R.id.reviewer_menu_view,
                    ConstraintSet.START,
                    R.id.counts_flow,
                    ConstraintSet.END,
                )
                applyTo(binding.toolsLayout)
            }
            // applying a ConstraintSet resets the visibility of counts_flow,
            // which includes the timer, so set again its visibility.
            binding.timer.isVisible = viewModel.answerTimerStatusFlow.value != null
            return
        }

        val toolbarPosition = Prefs.toolbarPosition

        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            if (hideAnswerButtons) {
                binding.webViewContainer.updateLayoutParams<MarginLayoutParams> { bottomMargin = 0 }
                binding.typeAnswerContainer.updateLayoutParams<MarginLayoutParams> {
                    topMargin = 8F.dp.toPx(requireContext())
                }
            } else {
                binding.answerArea.updateLayoutParams<MarginLayoutParams> { bottomMargin = 0 }
            }
        }
    }

    private fun setupAnswerTimer() {
        val timer = binding.timer
        timer.isVisible = viewModel.answerTimerStatusFlow.value != null // necessary to handle configuration changes
        viewModel.answerTimerStatusFlow.collectIn(lifecycleScope) { status ->
            when (status) {
                is AnswerTimerStatus.Running -> {
                    timer.isVisible = true
                    timer.limitInMs = status.limitInMs
                    timer.restart()
                }
                AnswerTimerStatus.Stopped -> {
                    timer.isVisible = true
                    timer.stop()
                }
                null -> {
                    timer.isVisible = false
                }
            }
        }
    }

    private fun setupCheckPronunciation() {
        viewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
            binding.checkPronunciationContainer.isVisible = isEnabled
        }
    }

    private fun setupWhiteboard() {
        viewModel.whiteboardEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
            childFragmentManager.commit {
                val whiteboardFragment = childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName)
                if (isEnabled) {
                    if (whiteboardFragment != null) return@commit
                    add(R.id.webview_container, WhiteboardFragment::class.java, null, WhiteboardFragment::class.jvmName)
                } else {
                    whiteboardFragment?.let { remove(it) }
                }
            }
        }
        viewModel.onCardUpdatedFlow.collectIn(lifecycleScope) {
            val whiteboardFragment = childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName)
            (whiteboardFragment as? WhiteboardFragment)?.resetCanvas()
        }
    }

    private fun setupTimebox() {
        viewModel.timeBoxReachedFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { timebox ->
            Timber.i("ReviewerFragment: Timebox reached (reps %d - secs %d)", timebox.reps, timebox.secs)

            viewModel.stopAutoAdvance()

            val minutes = (timebox.secs / 60f).roundToInt()
            val message = CollectionManager.TR.studyingCardStudiedIn(timebox.reps) + " " + CollectionManager.TR.studyingMinute(minutes)

            AlertDialog.Builder(requireContext()).show {
                setTitle(R.string.timebox_reached_title)
                setMessage(message)
                setPositiveButton(CollectionManager.TR.studyingContinue()) { _, _ ->
                    Timber.i("ReviewerFragment: Timebox 'Continue'")
                    viewModel.onPageFinished(false)
                }
                setNegativeButton(CollectionManager.TR.studyingFinish()) { _, _ ->
                    Timber.i("ReviewerFragment: Timebox 'Finish'")
                    requireActivity().finish()
                }
                setCancelable(false)
            }
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
        private val gestureParser by lazy {
            GestureParser(
                scope = lifecycleScope,
                isDoubleTapEnabled = bindingMap.isBound(Gesture.DOUBLE_TAP),
            )
        }

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
                    if (isScrolling) return true
                    gestureParser.parse(url, scale, webView) { gesture ->
                        if (gesture == null) return@parse
                        Timber.v("ReviewerFragment::onGesture %s", gesture)
                        bindingMap.onGesture(gesture)
                    }
                    true
                }
                "ankidroid" -> {
                    when (url.host) {
                        "focusin" -> webviewHasFocus = true
                        "focusout" -> webviewHasFocus = false
                        "typeinput" -> url.path?.substring(1)?.let { viewModel.typedAnswer = it }
                    }
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

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            super.onPageFinished(view, url)
            Prefs.cardZoom.let {
                if (it == 100) return@let
                val scale = it / 100.0
                val script = """document.getElementById("qa").style.transform = `scale($scale)`;"""
                view?.evaluateJavascript(script, null)
            }
        }
    }

    companion object {
        fun getIntent(context: Context): Intent = CardViewerActivity.getIntent(context, ReviewerFragment::class)
    }
}
