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
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.R
import com.ichi2.anki.previewer.TemplatePreviewerFragment.Companion.ARGS_KEY
import com.ichi2.anki.utils.ext.doOnTabSelected
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Container for [TemplatePreviewerFragment] that works as a standalone page
 * by including a toolbar and a TabLayout for changing the current template.
 */
class TemplatePreviewerPage : Fragment(R.layout.template_previewer_container) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val fragment: TemplatePreviewerFragment
        if (savedInstanceState == null) {
            val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
            fragment = TemplatePreviewerFragment.newInstance(arguments)
            childFragmentManager.commitNow {
                replace(R.id.fragment_container, fragment)
            }
        } else {
            fragment = childFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment
        }

        val viewModel = fragment.viewModel
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)

        lifecycleScope.launch {
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
            tabLayout.doOnTabSelected { tab ->
                Timber.v("Selected tab %d", tab.position)
                viewModel.onTabSelected(tab.position)
            }
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            arguments: TemplatePreviewerArguments,
        ): Intent =
            CardViewerActivity.getIntent(
                context,
                TemplatePreviewerPage::class,
                bundleOf(ARGS_KEY to arguments),
            )
    }
}
