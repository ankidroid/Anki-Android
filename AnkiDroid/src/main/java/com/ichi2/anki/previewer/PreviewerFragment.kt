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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PreviewerFragment :
    CardViewerFragment(R.layout.previewer),
    Toolbar.OnMenuItemClickListener,
    BaseSnackbarBuilderProvider {

    override val viewModel: PreviewerViewModel by viewModels {
        val previewerIdsFile = requireNotNull(requireArguments().getSerializableCompat(CARD_IDS_FILE_ARG)) {
            "$CARD_IDS_FILE_ARG is required"
        } as PreviewerIdsFile
        val currentIndex = requireArguments().getInt(CURRENT_INDEX_ARG, 0)
        PreviewerViewModel.factory(previewerIdsFile, currentIndex)
    }
    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = {
            val slider = this@PreviewerFragment.view?.findViewById<Slider>(R.id.slider)
            anchorView = if (slider?.isVisible == true) {
                slider
            } else {
                this@PreviewerFragment.view?.findViewById<MaterialButton>(R.id.show_next)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
        /* ************************************* Menu items ************************************* */
        val menu = view.findViewById<Toolbar>(R.id.toolbar).menu

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

        lifecycleScope.launch {
            viewModel.flagCode
                .flowWithLifecycle(lifecycle)
                .collectLatest { flagCode ->
                    menu.findItem(R.id.action_flag).setIcon(Flag.fromCode(flagCode).drawableRes)
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
                        viewModel.currentIndex.tryEmit(slider.value.toInt() - 1)
                    }
                }
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

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@PreviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> editCard()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_back_side_only -> viewModel.toggleBackSideOnly()
            R.id.action_flag_zero -> viewModel.setFlag(Flag.NONE)
            R.id.action_flag_one -> viewModel.setFlag(Flag.RED)
            R.id.action_flag_two -> viewModel.setFlag(Flag.ORANGE)
            R.id.action_flag_three -> viewModel.setFlag(Flag.GREEN)
            R.id.action_flag_four -> viewModel.setFlag(Flag.BLUE)
            R.id.action_flag_five -> viewModel.setFlag(Flag.PINK)
            R.id.action_flag_six -> viewModel.setFlag(Flag.TURQUOISE)
            R.id.action_flag_seven -> viewModel.setFlag(Flag.PURPLE)
        }
        return true
    }

    private fun setBackSideOnlyButtonIcon(menu: Menu, isBackSideOnly: Boolean) {
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

    private val editCardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.handleEditCardResult(result)
    }

    private fun editCard() {
        val intent = viewModel.getNoteEditorDestination().toIntent(requireContext())
        editCardLauncher.launch(intent)
    }

    companion object {
        /** Index of the card to be first displayed among the IDs provided by [CARD_IDS_FILE_ARG] */
        const val CURRENT_INDEX_ARG = "currentIndex"

        /** Argument key to a [PreviewerIdsFile] with the IDs of the cards to be displayed */
        const val CARD_IDS_FILE_ARG = "cardIdsFile"

        fun getIntent(context: Context, previewerIdsFile: PreviewerIdsFile, currentIndex: Int): Intent {
            val arguments = bundleOf(
                CURRENT_INDEX_ARG to currentIndex,
                CARD_IDS_FILE_ARG to previewerIdsFile
            )
            return PreviewerActivity.getIntent(context, PreviewerFragment::class, arguments)
        }
    }
}
