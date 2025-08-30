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
package com.ichi2.anki.previewer

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.themes.Themes
import com.ichi2.utils.BundleUtils.getNullableInt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: TemplatePreviewerViewModel by viewModels {
        val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
        TemplatePreviewerViewModel.factory(arguments)
    }
    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = this@TemplatePreviewerFragment.view?.findViewById(R.id.show_answer) }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val showAnswerButton =
            view.findViewById<MaterialButton>(R.id.show_answer).apply {
                setOnClickListener { viewModel.toggleShowAnswer() }
            }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                showAnswerButton.text =
                    if (showingAnswer) {
                        getString(R.string.hide_answer)
                    } else {
                        getString(R.string.show_answer)
                    }
            }.launchIn(lifecycleScope)

        if (sharedPrefs().getBoolean("safeDisplay", false)) {
            view.findViewById<MaterialCardView>(R.id.webview_container).elevation = 0F
        }

        arguments?.getNullableInt(ARG_BACKGROUND_OVERRIDE_COLOR)?.let { color ->
            view.setBackgroundColor(color)
        }
    }

    /**
     * Sets up the tab layout for this previewer fragment.
     * This method should be called from the hosting activity after the fragment is attached.
     *
     * @param tabLayout The TabLayout to configure with template tabs
     */
    fun setupTabs(tabLayout: TabLayout) {
        lifecycleScope.launch {
            try {
                setupPreviewerTabs(tabLayout)
            } catch (e: Exception) {
                Timber.w(e, "Failed to setup previewer tabs")
            }
        }
    }

    /**
     * Sets up the previewer tabs with appropriate titles and selection handling.
     *
     * @param tabLayout The tab layout to configure
     */
    private suspend fun setupPreviewerTabs(tabLayout: TabLayout) {
        tabLayout.removeAllTabs()

        val backgroundColor =
            Themes.getColorFromAttr(requireContext(), R.attr.alternativeBackgroundColor)
        tabLayout.setBackgroundColor(backgroundColor)

        val cardsWithEmptyFronts = viewModel.cardsWithEmptyFronts?.await()
        for ((index, templateName) in viewModel.getTemplateNames().withIndex()) {
            val tabTitle =
                if (cardsWithEmptyFronts?.get(index) == true) {
                    getString(R.string.card_previewer_empty_front_indicator, templateName)
                } else {
                    templateName
                }
            val newTab = tabLayout.newTab().setText(tabTitle)
            tabLayout.addTab(newTab)
        }

        tabLayout.selectTab(tabLayout.getTabAt(viewModel.getCurrentTabIndex()))

        // Remove any existing listeners to avoid duplicates
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    Timber.v("Selected tab %d", tab.position)
                    viewModel.onTabSelected(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                    // do nothing
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                    // do nothing
                }
            },
        )
    }

    companion object {
        const val ARGS_KEY = "templatePreviewerArgs"
        private const val ARG_BACKGROUND_OVERRIDE_COLOR = "arg_background_override_color"

        /**
         * @param backgroundOverrideColor optional color to be used as background on the root view
         * of this fragment
         */
        fun newInstance(
            arguments: TemplatePreviewerArguments,
            backgroundOverrideColor: Int? = null,
        ): TemplatePreviewerFragment =
            TemplatePreviewerFragment().apply {
                val args = bundleOf(ARGS_KEY to arguments)
                backgroundOverrideColor?.let { args.putInt(ARG_BACKGROUND_OVERRIDE_COLOR, backgroundOverrideColor) }
                this.arguments = args
            }
    }
}
