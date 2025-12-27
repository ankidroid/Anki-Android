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
package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.DispatchKeyEventListener
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.previewer.PreviewerFragment.Companion.CARD_IDS_FILE_ARG
import com.ichi2.anki.reviewer.BindingMap
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.performClickIfEnabled
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PreviewerFragment :
    CardViewerFragment(R.layout.previewer),
    Toolbar.OnMenuItemClickListener,
    BaseSnackbarBuilderProvider,
    DispatchKeyEventListener,
    BindingProcessor<MappableBinding, PreviewerAction> {
    override val viewModel: PreviewerViewModel by viewModels()
    override val webViewLayout: SafeWebViewLayout get() = requireView().findViewById(R.id.webview_layout)

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = {
            val slider = this@PreviewerFragment.view?.findViewById<Slider>(R.id.slider)
            anchorView =
                if (slider?.isVisible == true) {
                    slider
                } else {
                    this@PreviewerFragment.view?.findViewById<MaterialButton>(R.id.show_next)
                }
        }

    private lateinit var bindingMap: BindingMap<MappableBinding, PreviewerAction>

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val slider = view.findViewById<Slider>(R.id.slider)
        val nextButton = view.findViewById<MaterialButton>(R.id.show_next)
        val previousButton = view.findViewById<MaterialButton>(R.id.show_previous)
        val progressIndicator = view.findViewById<MaterialTextView>(R.id.progress_indicator)
        val cardsCount = viewModel.cardsCount()

        lifecycleScope.launch {
            viewModel.currentIndex
                .flowWithLifecycle(lifecycle)
                .collectLatest { currentIndex ->
                    val displayIndex = currentIndex + 1
                    slider.value = displayIndex.toFloat()
                    progressIndicator.text =
                        getString(R.string.preview_progress_bar_text, displayIndex, cardsCount)
                }
        }
        // ************************************* Menu items *************************************
        val menu = view.findViewById<Toolbar>(R.id.toolbar).menu
        setupFlagMenu(menu)

        lifecycleScope.launch {
            viewModel.backSideOnly
                .flowWithLifecycle(lifecycle)
                .collectLatest { isBackSideOnly ->
                    setBackSideOnlyButtonIcon(menu, isBackSideOnly)
                }
        }

        lifecycleScope.launch {
            viewModel.isMarked
                .flowWithLifecycle(lifecycle)
                .collectLatest { isMarked ->
                    with(menu.findItem(R.id.action_mark)) {
                        if (isMarked) {
                            setIcon(R.drawable.ic_star)
                            setTitle(R.string.menu_unmark_note)
                        } else {
                            setIcon(R.drawable.ic_star_border_white)
                            setTitle(R.string.menu_mark_note)
                        }
                    }
                }
        }

        // handle selection of a new flag
        lifecycleScope.launch {
            viewModel.flag
                .flowWithLifecycle(lifecycle)
                .collectLatest { flag ->
                    menu.findItem(R.id.action_flag).setIcon(flag.drawableRes)
                }
        }

        @NeedsTest("webview don't vanish when only one card is in the list")
        if (cardsCount == 1) {
            slider.visibility = View.GONE
            progressIndicator.visibility = View.GONE
        }

        slider.apply {
            valueTo = cardsCount.toFloat()
            addOnSliderTouchListener(
                object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}

                    override fun onStopTrackingTouch(slider: Slider) {
                        viewModel.onSliderChange(slider.value.toInt())
                    }
                },
            )
        }

        lifecycleScope.launch {
            viewModel.isNextButtonEnabled.collectLatest {
                nextButton.isEnabled = it
            }
        }

        nextButton.setOnClickListener {
            viewModel.onNextButtonClick()
        }

        lifecycleScope.launch {
            viewModel.isBackButtonEnabled.collectLatest {
                previousButton.isEnabled = it
            }
        }

        previousButton.setOnClickListener {
            viewModel.onPreviousButtonClick()
        }

        view.setOnGenericMotionListener { _, event ->
            bindingMap.onGenericMotionEvent(event)
        }

        viewModel.showingAnswer.collectIn(lifecycleScope) {
            // focus on the whole layout so motion controllers can be captured
            // without navigating the other View elements
            view.findViewById<CoordinatorLayout>(R.id.root_layout).requestFocus()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@PreviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        view.findViewById<MaterialCardView>(R.id.webview_container).setFrameStyle()

        bindingMap = BindingMap(sharedPrefs(), PreviewerAction.entries, this)
    }

    private fun setupFlagMenu(menu: Menu) {
        val submenu = menu.findItem(R.id.action_flag).subMenu
        lifecycleScope.launch {
            for ((flag, name) in Flag.queryDisplayNames()) {
                submenu
                    ?.add(Menu.NONE, flag.id, Menu.NONE, name)
                    ?.setIcon(flag.drawableRes)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> editCard()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_back_side_only -> viewModel.toggleBackSideOnly()
            R.id.flag_none -> viewModel.setFlag(Flag.NONE)
            R.id.flag_red -> viewModel.setFlag(Flag.RED)
            R.id.flag_orange -> viewModel.setFlag(Flag.ORANGE)
            R.id.flag_green -> viewModel.setFlag(Flag.GREEN)
            R.id.flag_blue -> viewModel.setFlag(Flag.BLUE)
            R.id.flag_pink -> viewModel.setFlag(Flag.PINK)
            R.id.flag_turquoise -> viewModel.setFlag(Flag.TURQUOISE)
            R.id.flag_purple -> viewModel.setFlag(Flag.PURPLE)
        }
        return true
    }

    override fun processAction(
        action: PreviewerAction,
        binding: MappableBinding,
    ): Boolean {
        when (action) {
            PreviewerAction.MARK -> viewModel.toggleMark()
            PreviewerAction.EDIT -> editCard()
            PreviewerAction.TOGGLE_BACKSIDE_ONLY -> viewModel.toggleBackSideOnly()
            PreviewerAction.REPLAY_AUDIO -> viewModel.replayMedia()
            PreviewerAction.TOGGLE_FLAG_RED -> viewModel.toggleFlag(Flag.RED)
            PreviewerAction.TOGGLE_FLAG_ORANGE -> viewModel.toggleFlag(Flag.ORANGE)
            PreviewerAction.TOGGLE_FLAG_GREEN -> viewModel.toggleFlag(Flag.GREEN)
            PreviewerAction.TOGGLE_FLAG_BLUE -> viewModel.toggleFlag(Flag.BLUE)
            PreviewerAction.TOGGLE_FLAG_PINK -> viewModel.toggleFlag(Flag.PINK)
            PreviewerAction.TOGGLE_FLAG_TURQUOISE -> viewModel.toggleFlag(Flag.TURQUOISE)
            PreviewerAction.TOGGLE_FLAG_PURPLE -> viewModel.toggleFlag(Flag.PURPLE)
            PreviewerAction.UNSET_FLAG -> viewModel.setFlag(Flag.NONE)
            PreviewerAction.BACK -> {
                requireView().findViewById<MaterialButton>(R.id.show_previous).performClickIfEnabled()
            }
            PreviewerAction.NEXT -> {
                requireView().findViewById<MaterialButton>(R.id.show_next).performClickIfEnabled()
            }
        }
        return true
    }

    private fun setBackSideOnlyButtonIcon(
        menu: Menu,
        isBackSideOnly: Boolean,
    ) {
        menu.findItem(R.id.action_back_side_only).apply {
            if (isBackSideOnly) {
                setIcon(R.drawable.ic_card_answer)
                setTitle(R.string.card_side_answer)
            } else {
                setIcon(R.drawable.ic_card_question)
                setTitle(R.string.card_side_both)
            }
        }
    }

    private fun editCard() {
        lifecycleScope.launch {
            val intent = viewModel.getNoteEditorDestination().toIntent(requireContext())
            startActivity(intent)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return bindingMap.onKeyDown(event)
    }

    companion object {
        /** Index of the card to be first displayed among the IDs provided by [CARD_IDS_FILE_ARG] */
        const val CURRENT_INDEX_ARG = "currentIndex"

        /** Argument key to a [IdsFile] with the IDs of the cards to be displayed */
        const val CARD_IDS_FILE_ARG = "cardIdsFile"

        fun getIntent(
            context: Context,
            idsFile: IdsFile,
            currentIndex: Int,
        ): Intent {
            val arguments =
                bundleOf(
                    CURRENT_INDEX_ARG to currentIndex,
                    CARD_IDS_FILE_ARG to idsFile,
                )
            return CardViewerActivity.getIntent(context, PreviewerFragment::class, arguments)
        }
    }
}
