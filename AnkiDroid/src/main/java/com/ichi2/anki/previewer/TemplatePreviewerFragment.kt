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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.appcompat.widget.ThemeUtils
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.navBarNeedsScrim
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.template_previewer),
    BaseSnackbarBuilderProvider {

    override val viewModel: TemplatePreviewerViewModel by viewModels {
        val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
        TemplatePreviewerViewModel.factory(arguments, SoundPlayer())
    }
    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = this@TemplatePreviewerFragment.view?.findViewById(R.id.show_answer) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val showAnswerButton = view.findViewById<MaterialButton>(R.id.show_answer).apply {
            setOnClickListener { viewModel.toggleShowAnswer() }
        }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                showAnswerButton.text = if (showingAnswer) {
                    getString(R.string.hide_answer)
                } else {
                    getString(R.string.show_answer)
                }
            }
            .launchIn(lifecycleScope)

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        lifecycleScope.launch {
            for (templateName in viewModel.getTemplateNames()) {
                tabLayout.addTab(tabLayout.newTab().setText(templateName))
            }
            tabLayout.selectTab(tabLayout.getTabAt(viewModel.getCurrentTabIndex()))
            tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
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
            })
        }

        if (sharedPrefs().getBoolean("safeDisplay", false)) {
            view.findViewById<MaterialCardView>(R.id.webview_container).elevation = 0F
        }

        with(requireActivity()) {
            // use the screen background color if the nav bar doesn't need a scrim when using a
            // transparent background. e.g. when navigation gestures are enabled
            if (!navBarNeedsScrim) {
                window.navigationBarColor = ThemeUtils.getThemeAttrColor(this, R.attr.alternativeBackgroundColor)
            }
        }
    }

    companion object {
        const val ARGS_KEY = "templatePreviewerArgs"

        fun getIntent(context: Context, arguments: TemplatePreviewerArguments): Intent {
            return CardViewerActivity.getIntent(
                context,
                TemplatePreviewerFragment::class,
                bundleOf(ARGS_KEY to arguments)
            )
        }
    }
}
