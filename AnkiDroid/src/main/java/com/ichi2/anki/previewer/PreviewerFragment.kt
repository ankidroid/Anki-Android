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
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.getViewerAssetLoader
import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import com.ichi2.anki.previewer.PreviewerViewModel.Companion.stdHtml
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.themes.Themes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class PreviewerFragment : Fragment(R.layout.previewer), Toolbar.OnMenuItemClickListener {
    private lateinit var viewModel: PreviewerViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val previewerIdsFile = requireNotNull(requireArguments().getSerializableCompat(IDS_FILE_EXTRA)) {
            "$IDS_FILE_EXTRA is required"
        } as PreviewerIdsFile
        val currentIndex = requireArguments().getInt(CURRENT_INDEX_EXTRA, 0)

        viewModel = ViewModelProvider(
            requireActivity(),
            PreviewerViewModel.factory(previewerIdsFile, currentIndex)
        )[PreviewerViewModel::class.java]

        val webView = view.findViewById<WebView>(R.id.webview)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        with(webView) {
            webViewClient = onCreateWebViewClient()
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            with(settings) {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                domStorageEnabled = true
            }
            loadDataWithBaseURL(
                "http://$LOCALHOST/",
                stdHtml(requireContext(), Themes.currentTheme.isNightMode),
                "text/html",
                null,
                null
            )
        }

        val slider = view.findViewById<Slider>(R.id.slider)
        val nextButton = view.findViewById<MaterialButton>(R.id.show_next)
        val previousButton = view.findViewById<MaterialButton>(R.id.show_previous)
        val progressIndicator = view.findViewById<MaterialTextView>(R.id.progress_indicator)

        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }
            .launchIn(lifecycleScope)
        viewModel.eval
            .flowWithLifecycle(lifecycle)
            .onEach { eval ->
                webView.evaluateJavascript(eval, null)
            }
            .launchIn(lifecycleScope)

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
            viewModel.backsideOnly
                .flowWithLifecycle(lifecycle)
                .collectLatest { isBacksideOnly ->
                    setBacksideOnlyButtonIcon(menu, isBacksideOnly)
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

        super.onViewCreated(view, savedInstanceState)
    }

    private fun onCreateWebViewClient(): WebViewClient {
        val assetLoader = requireContext().getViewerAssetLoader(LOCALHOST)
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                viewModel.loadCurrentCard()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (_: Exception) {
                    Timber.w("Could not open url")
                }
                return true
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> editCard()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_back_side_only -> viewModel.toggleBacksideOnly()
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

    private fun setBacksideOnlyButtonIcon(menu: Menu, isBacksideOnly: Boolean) {
        menu.findItem(R.id.action_back_side_only).apply {
            if (isBacksideOnly) {
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
        const val CURRENT_INDEX_EXTRA = "currentIndex"
        const val IDS_FILE_EXTRA = "idsFile"

        fun getIntent(context: Context, previewerIdsFile: PreviewerIdsFile, currentIndex: Int): Intent {
            val args = bundleOf(CURRENT_INDEX_EXTRA to currentIndex, IDS_FILE_EXTRA to previewerIdsFile)
            return SingleFragmentActivity.getIntent(context, PreviewerFragment::class, args)
        }
    }
}
