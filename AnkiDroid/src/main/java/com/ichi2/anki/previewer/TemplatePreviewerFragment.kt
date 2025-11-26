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
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.databinding.TemplatePreviewerBinding
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.BundleUtils.getNullableInt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: TemplatePreviewerViewModel by viewModels {
        val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
        TemplatePreviewerViewModel.factory(arguments)
    }

    lateinit var binding: TemplatePreviewerBinding

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = binding.showAnswer }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        // binding must be set before super.onViewCreated
        // as super.onViewCreated depends on webViewLayout, which depends on the binding
        binding = TemplatePreviewerBinding.bind(view)

        super.onViewCreated(view, savedInstanceState)

        binding.showAnswer.setOnClickListener { viewModel.toggleShowAnswer() }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                binding.showAnswer.text =
                    if (showingAnswer) {
                        getString(R.string.hide_answer)
                    } else {
                        getString(R.string.show_answer)
                    }
            }.launchIn(lifecycleScope)

        if (sharedPrefs().getBoolean("safeDisplay", false)) {
            binding.webViewContainer.elevation = 0F
        }

        arguments?.getNullableInt(ARG_BACKGROUND_OVERRIDE_COLOR)?.let { color ->
            view.setBackgroundColor(color)
        }
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
